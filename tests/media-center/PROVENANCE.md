# Media-center redesign provenance

Research date: 2026-09-13. These are research references, not claims of code
incorporation or an exhaustive audit of each project.

## Implementation in this change

No source code or artwork was copied from the projects below. The implementation
uses TjGram's existing Telegram views, account-scoped media storage, downloads and
PhotoViewer. No second Telegram session, TDLib runtime, replacement player, or
third-party UI framework was added. Existing repository notices remain applicable.

## References examined

- [Findroid](https://github.com/jarnedemeulemeester/findroid), revision
  `0cb44260edc02d0cf4413e77be743fa258e39b2a`: SeasonScreen and separate playback entry;
  informed the separation between browsing a series and opening its player.
- [Streamyfin](https://github.com/streamyfin/streamyfin), revision
  `4faddc5fd6b0aaa3aef2a6a0ed1640180ededa5d`: SeasonPicker and MediaSourceSelector;
  informed explicit season and source-version selection.
- [Jellyfin Web](https://github.com/jellyfin/jellyfin-web), revision
  `6c4965e5b3adff999f161f3a8d98eb11dc35935a`: media information presentation;
  informed separating reported dimensions from codec/audio information.
- [TMPlayer](https://github.com/dracu-lah/TMPlayer): MediaName and DownloadWindow;
  reviewed naming and partial-file behavior, without importing its Telegram or
  download implementation. Research used its then-current main branch.
- [MediaBox](https://mboxil.github.io/): product and interface comparison, not a
  source-code dependency. The research did not locate its application source in
  the public repositories examined; this is not proof that none exists elsewhere.
- [Emby TV naming](https://emby.media/support/articles/TV-Naming.html): comparison
  with structured media libraries. Telegram captions and mixed chats need more
  conservative matching than a controlled folder hierarchy.

## Future imports

If code is imported later, record the exact file, revision, license, original
notices and local modifications before committing it. Check compatibility with
the repository's existing licensing; a credits screen does not replace required
license notices or source-distribution obligations. This research record does
not authorize relicensing upstream code.

## Evidence boundaries

Reference clients were not tested exhaustively with live accounts. Current
TjGram test coverage and its remaining device/playback limitations are documented
in [README.md](README.md), separately from design inspiration.
