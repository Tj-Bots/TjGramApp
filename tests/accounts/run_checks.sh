#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
account_test_output=$(mktemp -d /tmp/tj-account-tests.XXXXXX)
javac -encoding UTF-8 -d "$account_test_output" \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjAccountOrder.java \
  TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMessageEditPolicy.java \
  tests/accounts/*.java
java -cp "$account_test_output" AccountPolicyTest
