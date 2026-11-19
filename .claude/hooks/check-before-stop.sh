#!/bin/sh
# Before a turn ends, once the branch changes the SDK, the sample app or the checks: quality/lint-new.sh (lint and
# Checkstyle on what the branch adds), the unit tests and a compile of the sample app. Red means the turn is not done.
# Gradle keeps what did not change, so an unchanged tree costs seconds. The API check (--api) builds the base too and
# stays with CI and with AGENTS.md's done criteria; the on-device tests need an emulator and stay with CI.
command -v jq >/dev/null 2>&1 || exit 0
input=$(cat)
active=$(printf '%s' "$input" | jq -r '.stop_hook_active // false')
# The session's working folder, which may be a worktree other than the project folder.
dir=$(printf '%s' "$input" | jq -r '.cwd // empty')
cd "${dir:-${CLAUDE_PROJECT_DIR:-.}}" 2>/dev/null || exit 0
root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
cd "$root" || exit 0
[ -f quality/lint-new.sh ] || exit 0

paths="gleap app quality build.gradle settings.gradle gradle.properties gradle"
fork=$(git merge-base origin/master HEAD 2>/dev/null) || exit 0
# shellcheck disable=SC2086 # $paths is a list of pathspecs
changed=$( (git diff --name-only "$fork" -- $paths; git ls-files -o --exclude-standard -- $paths) 2>/dev/null)
[ -n "$changed" ] || exit 0

# The agent was told once and changed nothing since: another block would only burn a turn.
state="$(git rev-parse --git-dir)/claude-stop-gate"
# shellcheck disable=SC2086
key=$( (git rev-parse HEAD; git diff HEAD; git ls-files -o --exclude-standard -- $paths | xargs cat 2>/dev/null) | git hash-object --stdin)
if [ "$active" = true ] && [ "$(cat "$state" 2>/dev/null)" = "$key" ]; then
  exit 0
fi

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && ! grep -q '^sdk\.dir=' local.properties 2>/dev/null &&
  [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

failures=
step() { # name, command...
  name=$1
  shift
  if ! output=$("$@" 2>&1 </dev/null); then
    failures="$failures
## $name
$(printf '%s\n' "$output" | grep -v '^$' | tail -n 80)
"
  fi
}
# Without -q, so a failing test is named ("GleapRegionTest > fromString FAILED"). Keeps the failures, compiler errors
# and what went wrong; drops the task lines and Gradle's warnings.
tests() {
  ./gradlew --console=plain :gleap:testDebugUnitTest :app:compileDebugJavaWithJavac >"$log" 2>&1
  code=$?
  awk '/ FAILED$/ { print; n = 4; next }
    n > 0 && /^    / { print; n--; next }
    { n = 0 }
    /tests completed|error:/ { print; next }
    /^\* What went wrong:/ { w = 3 }
    w > 0 { print; w-- }' "$log"
  return "$code"
}
log=$(mktemp)
step "sh quality/lint-new.sh" sh quality/lint-new.sh
step "./gradlew :gleap:testDebugUnitTest :app:compileDebugJavaWithJavac" tests
rm -f "$log"

if [ -z "$failures" ]; then
  rm -f "$state"
  exit 0
fi
printf '%s\n' "$key" >"$state"
printf 'Fix this before you finish, or report BLOCKED with the reason:\n%s\n' "$failures" >&2
exit 2
