# Alternative login regression checks

The standalone rules test uses synthetic tokens only. It does not contact Telegram, log in,
or require Android. Run from the repository root with a JDK:

```sh
tj_login_tests_dir=$(mktemp -d /tmp/tj-login-tests.XXXXXX)
javac -d "$tj_login_tests_dir" TMessagesProj/src/main/java/org/telegram/messenger/tj/TjLoginRules.java tests/tj-login/TjLoginRulesTest.java
java -cp "$tj_login_tests_dir" TjLoginRulesTest
```

Compile Android integration with `./gradlew :TMessagesProj_App:compileAfatDebugJavaWithJavac`.
Neither test replaces the device checks below. Do not place real tokens or login QR codes in
test fixtures, screenshots, logs, or issue reports.

After compiling Android integration, also test the actual bot/QR request encoders (offline):

```sh
tj_login_tests_dir=$(mktemp -d /tmp/tj-login-tests.XXXXXX)
tj_app_classes=TMessagesProj/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes
javac -cp "$tj_app_classes" -d "$tj_login_tests_dir" tests/tj-login/TjBotRequestTest.java
java -cp "$tj_login_tests_dir:$tj_app_classes" TjBotRequestTest
```

The encoder checks verify field ordering, 64-bit account exclusions and binary migration tokens;
they do not prove that a server accepted a login or that the rendered QR can be scanned.

## Device checks (require an authorized test account / bot)

- In Hebrew and English, open QR and bot login directly from the two buttons below the phone
  controls. There must be no intermediate method picker. The existing passkey subtitle link
  must open only the limitation explanation. Phone-number change and account-deletion screens
  must not offer alternative login methods.
- Check narrow screens, large system fonts, RTL/LTR and light/dark themes. Button labels must
  wrap without truncation and remain reachable by scrolling with the keyboard visible. Local
  TJ login strings have Hebrew and English coverage; other languages fall back to English.
- QR: scan and approve from an already connected device. Verify automatic expiry refresh,
  a two-step password account, a different Telegram data center, network interruption and retry.
- Dismiss QR, navigate back, background the app, and rotate while a request or QR encoding is
  pending. A stale callback must not reopen a dialog or complete the UI login. Reopen and retry.
- Accounts already connected in the same environment must not be offered for QR or added again.
  Test and production accounts must be treated independently.
- Bot: reject empty, malformed, oversized and revoked tokens. Verify a valid bot can receive
  and send messages, switch accounts, restart and read its locally stored messages. Remote
  historical chat lists/history are not supported like user accounts. No contacts sync or
  periodic online-status request should be sent for the bot.
- Fresh bot account with no cached dialogs: send it a message from a test user. The chat must
  appear immediately alongside the unread badge, without requiring server history pagination.
  Repeat after restart and verify ordinary user-account history pagination is unchanged.
- Bot login permits screenshots, with the token still masked in a clearly outlined input.
  The QR dialog must continue to block screenshots. Neither may save its credential to view
  state or autofill. Verify cancel during an outstanding bot request, then retry.
- Passkey information must be localized; no native passkey prompt should open. The existing
  test-backend checkbox must remain unchanged.

## Preserved media export

- In an ordinary private chat, preserve a one-time photo/video, mark it viewed, reopen the
  chat and export to Gallery. Repeat with Ghost mode on and off. Export must not send a new
  read receipt or produce duplicate Gallery menu entries.
- Repeat with only the archived attachment remaining (normal cache removed).
- If all copies have been removed, report failure without downloading expired media again.
- Secret-chat restrictions must remain in place; the new export option is not offered there.
- An unrelated, ordinary photo/video must keep its original save behavior.

## Drawer account scrollbar

- With 1–4 accounts, no scrollbar should appear. With 5 or more, the rail and thumb must be
  visible without needing to begin scrolling. Test both ends and the middle of the list.
- Repeat with Hebrew/RTL and English/LTR and light/dark themes. The indicator must not overlap
  the account labels or cover the fixed Add Account row.
- Avatar long-press drag, row long-press preview, switching accounts and saved order must remain
  unchanged. The indicator is paint only and must never intercept touch events.

## Account preview navigation

- From account A, preview account B and dismiss without expanding. Account A must remain active.
- Expand B's preview to full screen. B must become the selected account and its normal main
  tabs must replace the old stack. Back (button and gesture) must not reveal account A.
- Open a chat in B, then go back: return to B's chats. Restart and verify B remains selected.
- Repeat with ordinary drawer account taps and the main-tabs account picker; both must still
  replace the stack. Verify account drag order and preview cancellation are unchanged.
