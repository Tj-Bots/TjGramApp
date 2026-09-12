#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
test_output=$(mktemp -d /tmp/tj-media-tests.XXXXXX)
javac -encoding UTF-8 -d "$test_output" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaTitle.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaCatalog.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaMatch.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPlaybackProgress.java \
  tests/media-center/TjMediaTitleTest.java \
  tests/media-center/TjMediaCatalogTest.java \
  tests/media-center/TjMediaMatchTest.java \
  tests/media-center/TjMediaPlaybackProgressTest.java
java -cp "$test_output" TjMediaTitleTest
java -cp "$test_output" TjMediaCatalogTest
java -cp "$test_output" TjMediaMatchTest
java -cp "$test_output" TjMediaPlaybackProgressTest
javac -encoding UTF-8 -d "$test_output/pager" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaLibrary.java \
  $(rg --files tests/media-center/pager -g '*.java')
java -cp "$test_output/pager" TjMediaLibraryTest
python3 tests/media-center/test_store_rules.py
python3 tests/media-center/test_resources.py
git diff --check
