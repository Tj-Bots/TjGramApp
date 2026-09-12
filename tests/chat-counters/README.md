# Chat counter drill-down device checks

- Tap each nonempty chat counter, including creator/admin categories: the list must contain
  the number of entries displayed in the counter. Both use the same captured ID list.
- Zero counters, headings, folder count and contact count must not open chat lists.
- Scroll a large category to its last entry; verify the dialog's OK button remains accessible
  on small screens, in landscape, in Hebrew/RTL and English/LTR, and in light/dark themes.
- Tap private chats, bots, groups, channels and secret chats. Verify the correct chat opens
  in the counters screen's account, and normal access restrictions remain enforced.
- Back from a chat must return to counters. Closing a list without selection must not navigate.
- Counts still describe loaded local dialogs, not an exhaustive server-side account inventory.
  Reopen counters to take a new snapshot after receiving messages or changing chats.

Compile check: `./gradlew :TMessagesProj_App:compileAfatDebugJavaWithJavac`.
These are manual runtime checks, not assertions covered by compilation.
