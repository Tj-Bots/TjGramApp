#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
python3 tests/media-center/test_library_presentation.py
test_output=$(mktemp -d /tmp/tj-media-tests.XXXXXX)
javac -encoding UTF-8 -d "$test_output" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaTitle.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaLocalIdentity.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaCatalog.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaMatch.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPlaybackProgress.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjVideoFormat.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPageKey.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaPageMerge.java \
  tests/media-center/TjMediaTitleTest.java \
  tests/media-center/TjMediaLocalIdentityTest.java \
  tests/media-center/TjMediaCatalogTest.java \
  tests/media-center/TjMediaMatchTest.java \
  tests/media-center/TjMediaPlaybackProgressTest.java \
  tests/media-center/TjVideoFormatTest.java \
  tests/media-center/TjMediaPageKeyTest.java \
  tests/media-center/TjMediaPageMergeTest.java
java -cp "$test_output" TjMediaTitleTest
java -cp "$test_output" TjMediaLocalIdentityTest
java -cp "$test_output" TjMediaCatalogTest
java -cp "$test_output" TjMediaMatchTest
java -cp "$test_output" TjMediaPlaybackProgressTest
java -cp "$test_output" TjVideoFormatTest
java -cp "$test_output" TjMediaPageKeyTest
java -cp "$test_output" TjMediaPageMergeTest
javac -encoding UTF-8 -d "$test_output/pager" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaLibrary.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaRetryPolicy.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaScanState.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaKind.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjVideoFormat.java \
  $(rg --files tests/media-center/pager -g '*.java')
java -cp "$test_output/pager" TjMediaLibraryTest
java -cp "$test_output/pager" TjMediaRetryPolicyTest
java -cp "$test_output/pager" TjMediaDurableScanTest
java -cp "$test_output/pager" TjMediaKindTest
javac -encoding UTF-8 -d "$test_output/sources" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaSources.java \
  $(rg --files tests/media-center/sources -g '*.java')
java -cp "$test_output/sources" MediaSourcesTest
javac -encoding UTF-8 -d "$test_output/coordinator" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaSources.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaScanCoordinator.java \
  $(rg --files tests/media-center/sources tests/media-center/coordinator -g '*.java')
java -cp "$test_output/coordinator" MediaCoordinatorTest
python3 tests/media-center/test_store_rules.py
python3 tests/media-center/test_search_index.py
python3 tests/media-center/test_local_catalog.py
python3 tests/media-center/test_episode_numbers.py
python3 tests/media-center/test_catalog_pages.py
python3 tests/media-center/test_seek_pages.py
python3 tests/media-center/test_scan_progress.py
python3 tests/media-center/test_resources.py
python3 tests/media-center/test_navigation.py
python3 tests/media-center/test_ui_regressions.py
git diff --check
