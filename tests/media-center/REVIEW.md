# Media Center verification record

This records source remediation, not device certification. No local APK build,
real account login or live metadata credential was used for this feature. The user
authorized source commit/push and will perform device validation after CI builds.

## Review findings and fixes

| Baseline finding | Remediation | Verification |
| --- | --- | --- |
| Credential could follow a reused account slot | Explicit captured owner is the preference storage key for read/write | Follow-up concurrency source review; metadata request owner/cancel tests |
| Stale manual edit could restore deleted/replaced media | Existing-row snapshot validation (owner/message/document/edit/photo), no implicit reindex | SQL source/predicate guards; follow-up source review |
| Clear could be refilled by an active scanner | Stop producers before deletion; revision guards reject stale requests and retries | Production pager sequencing tests; follow-up source review |
| PhotoViewer completion overwritten by rewind | Dedicated completion checkpoint retained through rewind; cleared on user replay/seek | 9 pure production helper checks; integration compiles |
| Failed local state read made taps do nothing | Visible error with retry and original-message navigation | Source callback inspection; integration compiles |

Source review also checked account routing into ChatActivity, audio timestamp
handling, cancellation, serialized database access, bound query parameters,
filter-before-pagination, bounded scan/matcher memory and spoiler concealment.
No full dependency graph or performance benchmark is implied.

## Automated checks

Run commands in README.md. Current focused suite covers title parsing, catalog
grouping and next-episode selection, conservative matching, playback checkpoints,
production pager sequencing, SQLite predicates/migration DDL and 97 localized
resource keys. The separate transport harness runs 45 production request/response
checks with fake in-process HTTP responses. App Java/resource compilation is run
without assembling an APK.

The pager harness now has 34 checks, including multi-account duplicate IDs, selected
dialog request construction, explicit no-source selection, bounded pending writes
under slow storage, resumed pumping, revision changes before queued writes finish,
and repeated server cursors. These execute production paging code against controlled
boundaries, not production Telegram transport or Android database serialization.

## Required device acceptance gates

- [ ] Home/grid/details in Hebrew RTL and English LTR, large fonts, rotation,
      split screen, tablet width, light/dark theme and TalkBack.
- [ ] Current/all/selected account scope; source filtering, logout and account-slot reuse.
- [ ] Real Telegram search/caption results, page boundaries, offline/retry, scan
      cancellation, clear during scan, edits/deletions during in-flight requests.
- [ ] Natural playback completion followed by close; replay/seek; music, voice,
      round video and PiP handoff; persisted continue-playing after process restart.
- [ ] TMDB Android Keystore save/read/disable, actual authorized search, artwork,
      wrong key, rate limit, offline transition and cancellation.
- [ ] Existing-device SQLite upgrades and storage-failure UI; no secret/TTL
      inclusion or accidental Ghost-mode/read-receipt behavior change.

No device is connected and the SDK does not currently contain an emulator runtime.
These gates are deliberately left unchecked. A compilation or stubbed test cannot
prove them, and the feature must not be described as perfect or release-certified.
