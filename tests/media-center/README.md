# TjGram Media Center

## Current implementation — 2026-09-13

Native Android implementation, with local logic/SQL checks and Java compilation.
This is not a claim of device UI, playback or live-account certification. The
checks do not replace visual and live-account testing. APK packaging is handled by CI.

### Navigation and use

- Open **Media Center** from the drawer (enabled by default) or TjGram settings.
  The settings screen also offers an optional Media Center tab in the host app.
- Inside the center there is one bottom navigation row: Library, Watch, Lists.
  The host Telegram navigation and its fade overlay are hidden here.
  It uses MainTabsLayout/GlassTabView and the host bar's 56dp height, 8dp margins
  and themed non-blurred background. Search lives in the native action bar.
- Library exposes enabled media types: video, photos, music, voice/round video,
  files and GIFs. The visibility selection is saved per account owner. Video
  container files sent as documents use the same recognition as the player.
- Default scope is the current account. Select all/specific accounts, chat types,
  explicit chats and cloud/Tj local folders. Explicit empty scopes never mean all.
- Library pages are chronological. Movie/series catalogs are alphabetical and
  contain one entry per catalog identity across all selected accounts.
- Watch has bounded featured/recent/continue/favorite/movie/series shelves.
  Lists, favorites and playback state currently belong to individual source files.
  Lists have an owner-scoped directory and can exist empty. Create from Lists;
  long-press a name to rename/delete the list without deleting media. A file still
  belongs to one named list. Existing file-based names remain discoverable.
  Ordinary media opens through native viewers/player; unavailable documents and
  cross-account photos/videos go to their source chat. Identified movies/series
  use full-screen details. Long-press a library item for its management actions.

### Sources and scanning

`TjMediaSources` resolves native folder rules, unions explicitly selected chats,
deduplicates peers and excludes secret chats. Selections use immutable account
owner IDs and folder IDs, not names or reusable account slots. Membership is based
on dialogs currently known to Telegram and expands as dialog data loads.

An explicitly started scan captures its own account/chat/folder scope. Changing
display filters does not silently change that job. `TjMediaScanCoordinator` owns
the foreground job independently of the screen. Folder membership changes are
coalesced: new members join, removed members stop future scanning. Previously
indexed data is not deleted automatically. A caught-up folder job remains
subscribed for membership changes until stopped.

Moving the app to the background pauses active work; returning resumes the
requested job. Process death preserves committed checkpoints, not an always-on
service: start scanning again to resume. Recent-refresh and older-history cursors
are stored separately. Each page and its cursor are committed atomically under
an owner/revision/worker-lease guard. A failed write cannot advance the cursor.

Scanning fetches message metadata, not all media file bytes. It uses at most three
network requests, bounded pending batches and a separate preview window. Existing
content remains usable while indexing. Store updates are coalesced into automatic
refreshes of the current page boundary, deferred while a dialog or scrolling is
active. The refresh attempts to restore the visible item's offset when that item
remains in the bounded result window. Scan status and paging controls are separate.
Visible row changes use DiffUtil; progress-only notifications do not rebind artwork.
Touching, scrolling, paused fragments and open dialogs defer visual refresh.
Protocol error identifiers are separated from local read/write failures; arbitrary
server text is not displayed. A particular device's server failure still requires
its actual error code to diagnose.

### Local catalog, with no TMDB requirement

Filename/caption hints identify local movie/series titles. Known season/episode or
movie-year evidence can create a deterministic local identity. Conflicting episode
numbers/names and generic filenames stay unresolved, and remain accessible in
the ordinary library. Mixed channels are never assumed to contain one show.

Full-screen movie/series details contain artwork/thumbnail fallback, play/resume,
download/cancel, source information and expandable original caption/file details.
Series expose paged seasons, distinct numbered episodes, source counts, watch-state
indicators and next **available indexed** episode. Sources retain account, chat,
known sender, filename, size and per-file resume state. Next-episode navigation
skips gaps and seasons containing only unknown-numbered sources; it does not
invent unavailable episodes or autoplay them. The primary play label identifies
the selected source's season/episode when known.

Manual local title/type/year and numbering corrections work without credentials.
Changing local identity detaches old external metadata but preserves the file,
lists and progress. Automatic matching skips manually assigned local titles.
Time positions are never transferred between different source files or cuts.

TMDB is optional enrichment. Only explicitly requested title queries are sent;
there is no bundled credential or hidden proxy. Absent credentials/artwork use
Telegram thumbnails or a neutral spoiler-safe fallback. Local identities remain
independent of TMDB IDs. We do not infer official episode names, ratings, HDR,
total seasons or completeness from filenames or partial scans.

### Implementation map

| Component | Responsibility |
| --- | --- |
| `TjMediaCenterActivity` | Native navigation, saved scope/types, search, bounded pages and deferred automatic refresh |
| `TjMediaDetailsActivity` / `TjMediaEpisodesView` | Stable full-screen detail, local correction, episode summaries and source selection |
| `TjMediaCollectionsActivity` | Empty-list creation, owner-scoped browsing, rename/delete with confirmation |
| `TjMediaHomeView` / row/card cells | Bounded shelves, general media rows and themed artwork cards |
| `TjMediaLibrary` / `TjMediaScanCoordinator` | Existing Telegram transport, scan lifetime, pacing and owner-scoped resume |
| `TjMediaStore` / `TjMediaScanState` | App-private schema 9 storage and atomic checkpoint transactions on a serial queue |
| `TjMediaPageKey` / `TjMediaPageMerge` | Stable chronological windows across unequal account pages, without OFFSET |
| `TjMediaLocalIdentity` / `TjMediaLocalIndex` | Local naming evidence, per-source relationships and a persistent title directory |
| `TjMediaCatalogPageKey` | Alphabetical title paging independent of upload count |
| `TjMediaSearchIndex` | Incremental FTS4 word/prefix index for filename, caption and confirmed/manual title |
| `TjMediaSources` | Native folder membership plus explicit-chat union |
| `TjMediaMetadata` / `TjMediaAutoMatcher` | Optional bounded metadata requests; existing OkHttp and encrypted owner credentials |

The store serializes SQLite work off the UI thread. Search backfill processes 256
rows per batch; local catalog backfill processes 128. Their progress is persisted.
Local file windows retain at most 400 queried candidates across owners (individual
queries capped at 200); initial remote preview adds at most 200. Home requests at
most 120 candidates. Catalog pages have 40 titles, episode pages 20 numbers, source
pages 50 files. These are window sizes, **not library/history limits**.

The title directory is updated in the same storage flow as per-file relationships.
Indexed title/source lookups avoid deserializing every upload to group them.
Deleting the last source removes its directory key; clear/logout cascades through
the derived catalog. Manual flags/progress are not stored in the rebuildable index.

FTS search matches literal words and prefixes, not arbitrary substrings. Local
search and Telegram remote search can have different coverage. Partial backfill
is shown rather than falsely reported as an exhaustive search result.

### Verification

Run from the repository root:

```sh
bash tests/media-center/run_checks.sh
bash tests/accounts/run_checks.sh
GRADLE_USER_HOME=/tmp/tj-gradle-cache ./gradlew :TMessagesProj_App:compileAfatDebugJavaWithJavac
```

Current checks cover 29 title parsing, 14 credential-free identities, 13 grouping/
next-episode, 9 matching, 9 playback-state, 46 video-format, 21 page-key, 19,201
cross-owner merge windows, 84 production pager cases, 9 durable-scan, 14 media-kind,
12 source-resolution and 11 coordinator lifecycle cases. Protocol/Android boundary
stubs are explicitly test-only; these do not send Telegram requests.

SQL checks cover migration DDL 1–9, FTS/backfill/rollback, local catalog overrides,
distinct cross-account title pages, owner/source isolation, cleanup, seek pages,
stale leases and checkpoint atomicity. Resource checks validate 125 keys across
English, Hebrew and runtime fallback, including placeholders. Account tests cover
14 ordering/notification/bot-edit policy cases.

The available Android emulator also ran `android_catalog_sql.py` through an isolated
in-memory sqlite3 database: directory cleanup, rollback, scope and title boundaries
passed; integrity check returned `ok`. This is native SQL, **not** SQLiteOpenHelper,
serialized Telegram-message, Android UI or real-account verification.

See [SCALE_BASELINE.md](SCALE_BASELINE.md) for measured desktop SQL results and their
limits. [REDESIGN.md](REDESIGN.md) and [SERIES_DESIGN.md](SERIES_DESIGN.md) preserve
the approved design rationale and distinguish it from implementation evidence.

### Remaining verification and product boundaries

- Native Hebrew/English, light/dark, small screens, large text, rotation, TalkBack,
  playback/PiP, download recovery and real Telegram scan completeness need device
  testing. Compilation does not establish these behaviors.
- No official expected-episode catalog or “complete series” claim. Multi-episode
  files are flagged as ambiguous, not split into invented playback offsets.
- Local keys use normalized name/type/year; unrelated same-name/year content or
  translated aliases may require manual correction. There is no bulk alias/undo UI.
- Each source currently has one named list plus favorite/watched state. There is
  no independent title-level list or automatic cross-version playback-position merge.
- No automatic index quota/pruning. Changes/deletions missed while offline can
  remain stale until Telegram emits an update or the source is queried again.
- Derived-index failures are logged; a failed backfill resumes on database reopen.
  Broad sparse queries and device storage/serialization costs require profiling.

Secret, TTL and auto-deleting media are excluded. Ordinary protected media remains
app-private and uses Telegram's existing viewing/downloading behavior. No Ghost
read-receipt bypass or secure-window weakening is introduced. Clearing the media
index never deletes Telegram messages or media files.

### External sources and attribution

No third-party app source or new runtime dependency was imported. Earlier research
included Telegram-Stremio (GPL-3.0 server) and Flick (Apache-2.0 Compose app); neither
was copied into this native Telegram integration.

Official TMDB references: [authentication](https://developer.themoviedb.org/docs/authentication-application),
[movie search](https://developer.themoviedb.org/reference/search-movie),
[TV search](https://developer.themoviedb.org/reference/search-tv),
[images](https://developer.themoviedb.org/docs/image-basics),
[attribution](https://developer.themoviedb.org/docs/faq).

`tj_tmdb_logo.xml` is a format-only conversion of the official Alt short blue
[SVG](https://www.themoviedb.org/assets/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg)
from [TMDB logos](https://www.themoviedb.org/about/logos-attribution). Geometry,
gradient and viewport are preserved. TMDB owns its mark; no endorsement is implied.
# Library presentation follow-up

The list directory now uses recycled chat-style rows with local item counts and
a latest-item text preview. A floating plus button creates lists; long press
retains rename/delete. Counts describe indexed membership, not a remote scan.
Queries use the existing owner/collection index on the store's worker queue.

Photo and GIF tabs use static thumbnail grids. The mixed library keeps compact
rows; videos include duration and size. Music/voice and documents use Telegram's
existing cells, with explicit account constructors and separate recycle pools per
account. Existing callers retain the selected-account default. The media center
hosts FragmentContextView for the existing mini-player and full music player.
Music queues include only the visible same-account, same-dialog tracks and do not
request an unbounded remote playlist. Voice playback clears an unrelated queue.

`test_library_presentation.py` checks production summary SQL, empty lists, owner
isolation, index use and source wiring. These are not device playback tests.
Device QA should cover create/rename/delete, partial account loading failure,
photo/GIF grids and returning to mixed rows, music/voice pause/resume and full
player, document download/cancel/open, account logout, large text, RTL and themes.
