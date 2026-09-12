#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/../.." && pwd)"
test_dir="$(mktemp -d /tmp/tj-settings-links.XXXXXX)"
trap 'rm -r -- "$test_dir"' EXIT
javac -d "$test_dir" \
  "$repo_dir/TMessagesProj/src/main/java/org/telegram/messenger/tj/TjSettingsLinks.java" \
  "$repo_dir/tests/settings-links/SettingsLinksTest.java"
java -cp "$test_dir" SettingsLinksTest
