#!/bin/sh
# Judges what this branch adds compared with where it left the base (origin/master), and nothing else:
#   - Android Lint on :gleap. lint-baseline.xml lists the findings of the code before this gate; a finding beyond those
#     fails. Of several findings with the same issue and message in a file, the ones on lines this branch added or
#     changed are the new ones.
#   - Checkstyle (checkstyle.xml) on the Java files the branch changes. Only findings on lines it added or changed count.
#   - With --api: japicmp, the public classes of this build against those of the base's build. A binary or source
#     incompatible change to a class in public-api.txt fails; one in another public class is listed.
# Uncommitted and untracked files count as part of the branch. The tools come through the Gradle wrapper:
# quality.init.gradle adds them for these runs only. Exit 0: nothing new. 1: findings, printed. 2: a check could not run.
#
#   sh quality/lint-new.sh [--base <ref>] [--api]

quality=$(cd "$(dirname "$0")" && pwd)
base=origin/master
api=
while [ $# -gt 0 ]; do
  case $1 in
    --base) base=${2:?--base needs a ref}; shift 2 ;;
    --api) api=1; shift ;;
    *) echo "usage: sh quality/lint-new.sh [--base <ref>] [--api]" >&2; exit 2 ;;
  esac
done

root=$(git rev-parse --show-toplevel) || exit 2
cd "$root" || exit 2
if ! fork=$(git merge-base "$base" HEAD 2>/dev/null); then
  echo "lint-new: no common commit with $base. Run git fetch origin first." >&2
  exit 2
fi

# The Android SDK: ANDROID_HOME, else sdk.dir from local.properties, else Android Studio's default on a Mac. Exported,
# because the base build of --api runs in a folder without local.properties.
if [ -z "${ANDROID_HOME:-}" ]; then
  sdk=${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)}
  [ -n "$sdk" ] || sdk="$HOME/Library/Android/sdk"
  [ -d "$sdk" ] && export ANDROID_HOME="$sdk"
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
status=0
found() { status=1; }
failed() { [ "$status" = 1 ] || status=2; }
gradle() { ./gradlew --console=plain -q -I "$quality/quality.init.gradle" "$@" </dev/null; }
show() { # title, file with findings
  printf '## %s\n' "$1"
  sort -t: -k1,1 -k2,2n "$2"
}

# The lines this branch added or changed under gleap/src ("path:line"), and the Java files among them.
{
  git diff -U0 --no-color --no-ext-diff "$fork" -- gleap/src | awk '
    /^\+\+\+ b\// { file = substr($0, 7) }
    /^@@/ { split($3, r, ","); start = substr(r[1], 2); n = (r[2] == "" ? 1 : r[2]); for (i = 0; i < n; i++) print file ":" (start + i) }'
  git ls-files -o --exclude-standard -- gleap/src | while IFS= read -r f; do awk -v f="$f" '{ print f ":" NR }' "$f"; done
} >"$tmp/added"
files=$( (git diff --name-only --diff-filter=d "$fork" -- 'gleap/src/*.java'; git ls-files -o --exclude-standard -- 'gleap/src/*.java') | sort -u)

tasks=:gleap:lintDebug
[ -z "$files" ] || tasks="$tasks :gleap:qualityCheckstyle"
# shellcheck disable=SC2086 # $tasks is a list of task names
gradle --continue $tasks -PqualityFiles="$(printf '%s\n' "$files" | sed "s#^#$root/#" | paste -sd, -)" >"$tmp/gradle.log" 2>&1
code=$?
if [ "$code" != 0 ]; then
  echo "## Gradle could not run the checks"
  grep -v '^$' "$tmp/gradle.log" | tail -n 40
  failed
fi

# Lint: count each (issue, message, file) in the report and in the baseline. Both files are lint's XML, one attribute
# per line.
if [ "$code" = 0 ]; then
  awk -v module="$root/gleap/" -v added="$tmp/added" -v baseline="$quality/lint-baseline.xml" -v apos="'" '
    BEGIN { while ((getline l < added) > 0) ok[l] = 1 }
    function value(   s) { s = $0; sub(/^[^"]*"/, "", s); sub(/"[^"]*$/, "", s); return s }
    function text(s) {
      gsub(/&apos;/, apos, s); gsub(/&quot;/, "\"", s); gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&amp;/, "\\&", s)
      return s
    }
    /^ *<issue$/ { id = ""; message = ""; file = ""; line = 0; locations = 0 }
    /^ *id="/ { id = value() }
    /^ *message="/ { message = value() }
    /^ *<location/ { locations++ }
    locations == 1 && /^ *file="/ { file = value(); if (index(file, module) == 1) file = substr(file, length(module) + 1) }
    locations == 1 && /^ *line="/ { line = value() }
    /^ *<\/issue>/ {
      key = id SUBSEP message SUBSEP file
      if (FILENAME == baseline) known[key]++
      else { n[key]++; at[key, n[key]] = line }
    }
    END {
      for (key in n) {
        extra = n[key] - known[key]
        if (extra <= 0) continue
        split(key, k, SUBSEP)
        path = "gleap/" k[3]
        shown = 0
        for (i = 1; i <= n[key] && shown < extra; i++) {
          if (ok[path ":" at[key, i]]) { print path ":" at[key, i] ": " text(k[2]) " [" k[1] "]"; shown++ }
        }
        if (shown < extra) {
          lines = ""
          for (i = 1; i <= n[key]; i++) if (!ok[path ":" at[key, i]]) lines = lines (lines == "" ? "" : ", ") at[key, i]
          print path ":0: " (extra - shown) " more than the baseline lists, at one of lines " lines " (not changed by this branch): " text(k[2]) " [" k[1] "]"
        }
        total += extra
      }
      exit total > 0
    }' "$quality/lint-baseline.xml" gleap/build/reports/lint-results-debug.xml >"$tmp/lint.txt"
  case $? in
    0) ;;
    1) show "Android Lint: new findings (the old ones are listed in quality/lint-baseline.xml)" "$tmp/lint.txt"; found ;;
    *) echo "## Android Lint wrote no report it could read"; failed ;;
  esac
fi

# Checkstyle: the findings on lines this branch added or changed.
if [ "$code" = 0 ] && [ -n "$files" ]; then
  awk -v root="$root/" -v added="$tmp/added" -v apos="'" '
    BEGIN { while ((getline l < added) > 0) ok[l] = 1 }
    function attr(name,   s) {
      if (!match($0, name "=\"[^\"]*\"")) return ""
      s = substr($0, RSTART + length(name) + 2, RLENGTH - length(name) - 3)
      gsub(/&apos;/, apos, s); gsub(/&quot;/, "\"", s); gsub(/&lt;/, "<", s); gsub(/&gt;/, ">", s); gsub(/&amp;/, "\\&", s)
      return s
    }
    /<file / { file = attr("name"); if (index(file, root) == 1) file = substr(file, length(root) + 1) }
    /<error / {
      check = attr("source"); sub(/.*\./, "", check); sub(/Check$/, "", check)
      if (ok[file ":" attr("line")]) { print file ":" attr("line") ": " attr("message") " [" check "]"; n++ }
    }
    END { exit n > 0 }' gleap/build/reports/checkstyle/quality.xml >"$tmp/checkstyle.txt"
  case $? in
    0) ;;
    1) show "Checkstyle: findings on lines this branch added or changed (quality/checkstyle.xml)" "$tmp/checkstyle.txt"; found ;;
    *) echo "## Checkstyle wrote no report it could read"; failed ;;
  esac
fi

if [ -n "$api" ]; then
  # The base's AAR, built from the commit where the branch left it, against this checkout's.
  mkdir "$tmp/base" "$tmp/old" "$tmp/new"
  aar=gleap/build/outputs/aar/gleap-release.aar
  if ! git archive "$fork" | tar -x -C "$tmp/base" ||
    ! (cd "$tmp/base" && ./gradlew --console=plain -q :gleap:assembleRelease </dev/null >"$tmp/base.log" 2>&1) ||
    ! ./gradlew --console=plain -q :gleap:assembleRelease </dev/null >"$tmp/head.log" 2>&1 ||
    ! unzip -q -o -d "$tmp/old" "$tmp/base/$aar" classes.jar ||
    ! unzip -q -o -d "$tmp/new" "$aar" classes.jar; then
    echo "## API check: the base or this branch did not build"
    cat "$tmp/base.log" "$tmp/head.log" 2>/dev/null | grep -v '^$' | tail -n 40
    failed
  else
    jars="-PqualityApiOld=$tmp/old/classes.jar -PqualityApiNew=$tmp/new/classes.jar"
    # japicmp's change lines start with *** (modified), +++ (new) or --- (removed); a fourth character "!" marks a
    # binary, "*" a source incompatible change. Its exit code alone does not tell a finding from a failure to run.
    changes() { grep -E '^[[:space:]]*(\*\*\*|\+\+\+|---)|^E: ' "$1"; }
    incompatible() { grep -Eq '^[[:space:]]*(\*\*\*|\+\+\+|---)[!*]' "$1"; }
    # shellcheck disable=SC2086 # $jars is two arguments
    gradle :gleap:qualityApiBreaks $jars >"$tmp/breaks.log" 2>&1
    breaks=$?
    if incompatible "$tmp/breaks.log"; then
      echo "## API check: incompatible changes to the public API (quality/public-api.txt). Stop and ask (AGENTS.md)."
      changes "$tmp/breaks.log"
      found
    elif [ "$breaks" != 0 ]; then
      echo "## API check could not run"
      grep -v '^$' "$tmp/breaks.log" | tail -n 40
      failed
    elif changes "$tmp/breaks.log" >/dev/null; then
      echo "## API check, for reading: compatible changes to the public API"
      changes "$tmp/breaks.log"
    fi
    # shellcheck disable=SC2086
    if ! gradle :gleap:qualityApiReport $jars >"$tmp/report.log" 2>&1; then
      echo "## API report could not run"
      grep -v '^$' "$tmp/report.log" | tail -n 20
      failed
    elif incompatible "$tmp/report.log"; then
      echo "## API check, for reading: incompatible changes to public classes outside the public API"
      changes "$tmp/report.log"
    fi
  fi
fi

[ "$status" != 0 ] || echo "lint-new: nothing new compared with $base ($(git rev-parse --short "$fork"))."
exit "$status"
