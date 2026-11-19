#!/bin/sh
# After every edit of a Java or resource file of the SDK: compile the source set it belongs to, so the agent sees a
# broken edit at once (a few seconds with a warm Gradle). Lint, Checkstyle and the tests wait for the stop hook.
# Exit 2 with stderr reaches the agent; a note on exit 0 goes out as JSON, since plain stdout reaches no one.
command -v jq >/dev/null 2>&1 || exit 0
file=$(jq -r '.tool_input.file_path // empty')
case "$file" in
  */gleap/src/test/*.java) task=:gleap:compileDebugUnitTestJavaWithJavac ;;
  */gleap/src/androidTest/*.java) task=:gleap:compileDebugAndroidTestJavaWithJavac ;;
  */gleap/src/main/*.java | */gleap/src/main/res/*.xml | */gleap/src/main/AndroidManifest.xml) task=:gleap:compileDebugJavaWithJavac ;;
  *) exit 0 ;;
esac
[ -f "$file" ] || exit 0

# The checkout the file is in, which may be a worktree other than the session's project folder.
root=$(git -C "$(dirname "$file")" rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -f "$root/gradlew" ] || exit 0
cd "$root" || exit 0
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ] && ! grep -q '^sdk\.dir=' local.properties 2>/dev/null &&
  [ -d "$HOME/Library/Android/sdk" ]; then
  export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

if output=$(./gradlew --console=plain -q "$task" 2>&1 </dev/null); then
  exit 0
fi
# javac and the resource merger name a file of the SDK and a line; anything else is Gradle failing to run, which the
# agent cannot fix here.
if printf '%s\n' "$output" | grep -F "$root/gleap/src/" | grep -q ':[0-9]'; then
  printf 'The edit is on disk, but %s fails. Fix it before you continue:\n%s\n' "$task" \
    "$(printf '%s\n' "$output" | awk '/^FAILURE:/ { exit } /^$/ || /^Note: / || /^[[:space:]]+at / { next } { print }' | head -n 40)" >&2
  exit 2
fi
note="Could not compile after this edit (Gradle did not run): $(printf '%s\n' "$output" | grep -v '^$' | head -n 5)"
jq -n --arg note "$note" '{hookSpecificOutput: {hookEventName: "PostToolUse", additionalContext: $note}}'
exit 0
