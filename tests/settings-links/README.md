# FAQ settings links

Run `bash tests/settings-links/run_checks.sh` for the Android-independent URI parser tests.
These cover the eight published destinations and reject ambiguous/extra parameters.
They do not exercise Android activity navigation.

Device checks for a release build:

- Open each `tg://tjgram/settings?section=VALUE` from the FAQ, cold and warm.
  Values: `ghost`, `archive`, `local-premium`, `folders`, `media-center`,
  `media-metadata`, `player`, `media-lists`.
- Verify the existing passcode gate and logged-out behavior; no settings screen
  should bypass authentication. With multiple accounts, use the active account.
- Confirm Ghost/archive switches and saved player settings are unchanged by opening.
- Confirm local premium and player links scroll to the relevant section.
- Metadata and lists should open once after the media center becomes visible;
  dismissing or returning must not reopen the dialog/collection screen.
- Check Back navigation, Hebrew/English/Russian, and light/dark themes.
- In TjGram settings, verify FAQ opens the pinned index, updates opens @TjGramApp,
  discussion retains its existing destination, and contact opens @avi_user.
- Verify ordinary Telegram links such as `tg://settings/folders` still work.
