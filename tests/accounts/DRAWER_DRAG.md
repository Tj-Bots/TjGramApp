# Drawer account drag regression

`TjDrawerDragSmoke` exercises the production `DrawerAccountsCell` nested inside
Telegram's `RecyclerListView`, using real pointer down/hold/move/up events.
It requires a signed-out emulator and refuses to run with activated accounts.
The account slots are fixture rows only: no users are created, no preferences
are saved, and the reorder listener records results in memory.

Coverage:

- Avatar hold reorders both three-account and six-account cards.
- A rebind with the previous persisted order during a drag must not undo it.
- The completed gesture commits the order once and does not click/preview.
- The six-account list still scrolls normally after the drag, without reordering.

Before the fix, the rebind case returned `[0, 1, 2, 3, 4, 5]`, undoing the move.
The scroll indicator is retained: its drawing decoration does not handle touch.

Build app and test APKs with the same checkout:

```sh
./gradlew :TMessagesProj_App:assembleAfatDebug \
  :TMessagesProj_App:assembleAfatDebugAndroidTest \
  -PtjSmokeRunner=org.telegram.ui.TjDrawerDragSmoke
adb install -r -t TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
adb install -r -t TMessagesProj_App/build/outputs/apk/androidTest/afat/debug/TMessagesProj_App-afat-debug-androidTest.apk
adb shell am instrument -w org.telegram.messenger.tjgram.test/org.telegram.ui.TjDrawerDragSmoke
```

Omit `tjSmokeRunner` to build the existing media UI smoke runner instead.
This fixture does not prove authenticated multi-account persistence or reproduce
every possible device gesture. The independent `run_checks.sh` suite covers the
shared account ordering policy.
