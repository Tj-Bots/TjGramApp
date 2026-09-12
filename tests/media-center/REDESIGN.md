# Media center rebuild — approved design and historical investigation

Status update: the user approved the preview, native rebuild, local identification
and dynamic folder-source design. The implementation and current verification are
documented in [README.md](README.md). The investigation and checkpoints below are
historical snapshots: “pending”, “not wired” and “not production” describe the
state when those notes were written, not the current branch. In particular, durable
scanning, independent coordinator, bounded windows, FTS and native details are now
implemented. Device UI/playback/live-account acceptance is still not certified.

## Product contract

- A general media library first, not a recent-items shelf posing as a library.
- Persistent entry points: videos, photos, music, voice/video messages, documents,
  and GIFs. Multi-select visibility is saved per immutable owner identity.
- Separate optional cinema view: movies, series, continue watching, versions,
  episodes. No TMDB key required to browse ordinary files.
- Recent items is one ordering/shelf, never the only route into a category.
- Default current account; all/selected accounts and source chats remain explicit.
- Video files sent as documents must use a shared, non-mutating classification
  policy aligned with the existing TjVideoFiles playback support. Classification
  must not change merely because direct streaming is enabled/disabled.
- Index contents and downloaded files are distinct; never label indexed files
  as downloaded or an incomplete remote search as a complete account inventory.
- Design for multi-million-item histories across long-lived channels/groups.
  One million synthetic records is a baseline measurement, not a product limit.
  Do not silently truncate accessible history, sources or search results to pass
  performance tests. Coverage must remain explicit and resumable.
- Full historical indexing is not a prerequisite for browsing or remote discovery.
  Bounded memory describes retained pages, not a cap on available user content.
  Report index storage separately from media file sizes; do not auto-delete user
  lists/progress to enforce a cache budget. Storage policy needs explicit controls.

## Existing defects confirmed by source inspection

- TjMediaCenterActivity.reload and onPause stop the screen-owned scanner.
- TjMediaLibrary.Stream cursors are volatile; reset starts again at zero.
- Baseline local pages used OFFSET; replaced with owner/order-bound seek keys and
  matching indexes in schema 8. Text matching still uses instr over concatenated text.
- Browsing accumulates MessageObjects, sorts the whole list and fully rebinds it.
- Home.bind removes and recreates every shelf; updates can repeatedly query five
  sets of up to 200 records per account to display at most 20 items per shelf.
- Type selection is exclusive, not a saved multi-selection.
- Existing unit tests establish selected correctness properties, not million-row
  performance, runtime layout quality, or live Telegram search completeness.

## Architecture direction

Keep native Telegram views, transport, player and account infrastructure. Avoid
introducing a second media player or a web-based application screen.

1. Presentation: a native, recycled list/grid; small immutable display records;
   stable item keys; incremental updates; retained scroll state per query.
2. Query model: owner IDs, sources, type set, sort, search and collection remain
   separate from scan scope and scan lifecycle. Changing view never resets scan.
3. Local repository: keyset pages (continue after last stable sort tuple, not an
   ever-growing OFFSET); bounded display window; fetch serialized messages only
   when required for opening. A shared page contract handles equal timestamps.
4. Search: evaluate indexed full-text search against Hebrew/Latin captions and
   filenames. Preserve literal-input safety. Token/prefix semantics differ from
   arbitrary substring matching and must be communicated, not silently changed.
5. Indexing: account-owned coordinator independent of a screen. Commit batches
   and corresponding scan checkpoints atomically. Resume after interruption;
   separate recent updates from historical backfill; fair bounded concurrency.
6. Lifecycle: stop or defer work when Android restricts execution; never promise
   uninterrupted background scanning. Persist safe progress and expose resume.
7. Storage migration: preserve favorites, playback, lists, metadata provenance;
   backfill derived/search data in bounded batches without blocking first open.

Exact scheduler/search implementation choices require compatibility inspection
and explicit tradeoff review before implementation; no new dependency selected.

## Protocol evidence

Official documentation inspected 2026-09-12:

- [Global search](https://core.telegram.org/method/messages.searchGlobal): use
  `next_rate` when present; otherwise the date of the last message. The old pager
  always copied next_rate (zero when absent). Corrected with presence-aware tests.
- [Pagination](https://core.telegram.org/api/offsets): results are sliced and then
  additional filters can be applied. Do not infer exhaustive completion solely
  from a short filtered page or an approximate counter.
- [Search](https://core.telegram.org/method/messages.search): private-chat and
  global search routes differ; do not replace global search with empty-peer search
  and silently lose channels/supergroups. These methods are user-only; bot account
  browsing needs an explicit supported/unsupported state, not endless retries.
- [Errors](https://core.telegram.org/api/errors): transport retry policy must
  distinguish server-requested waits from ordinary failure.

## First-open and ongoing states

- Show cached content immediately when available; empty cache offers a small
  initial remote discovery request, not a full-history blocking operation.
- Older history indexes progressively; use loading/partial/offline/paused/error
  states separately. Never show a fake total or percentage without a denominator.
- Remote network latency is outside local responsiveness guarantees.
- Display filtering does not silently change an already-running scan's scope.
- Retry respects server delay and offline state; no infinite tight retry loop.
- Delete/clear/logout invalidate in-flight pages and checkpoints safely.
- Account replacement never inherits another owner's library or preferences.

## Verification gates (targets, not measurements)

- Synthetic SQLite datasets: 100k and 1m records, repeated dates, multiple owners,
  long Hebrew/Latin captions, documents, duplicate titles and sparse categories.
- Extend the representative-query/memory tests to several million records when
  disk permits; include a decade-spanning history, many sources, uneven account
  sizes and interleaved new arrivals during old-history pagination. A 2015-era
  account's full Telegram behavior cannot be reproduced by a tiny recent fixture.
- Capture cold/warm first page, deep page and selective search latency, query
  plans, database bytes and migration duration. Initial target: warm local page
  p95 <150 ms on the recorded test machine; do not imply device equivalence.
- Bound retained UI records to a documented window (initial target <=600),
  independent of indexed count; scan memory must not grow with history length.
- Checkpoint interruption before/after batch commit: no lost entries; harmless
  repeat fetches permitted, duplicate persisted rows prohibited.
- Slow storage, cancellation, repeated cursors, server wait, offline, edits,
  deletions and logout during pending batches.
- No complete-list reload or per-item database queries during shelf binding.
- Native emulator demo data must enter the production presentation/query path;
  test-only fixture launcher must not bypass authentication in release builds.
- Hebrew RTL and English LTR; light/dark; 320dp width and large text; rotate;
  keyboard search; category multi-select; empty/error/partial scan; scroll restore.
- Measure native frame timing and memory while browsing, searching and indexing.
- Playback, actual Telegram pagination/rate limits and live account completeness
  remain unverified without an authorized connected account.

## Delivery sequence

1. Interactive design preview and approval.
2. Query/classification contracts and scale test baseline.
3. Durable batching/checkpoints and repository migration.
4. Native library/cinema/list screens and navigation integration.
5. Emulator fixture build and UI/performance acceptance checks.
6. Regression review, compile and evidence report; commit/push only on request.

## Implemented foundation checkpoints

- Shared non-mutating video-container recognition extracted from TjVideoFiles;
  player behavior unchanged; 46 isolated recognition cases pass.
- TjMediaKind now provides the same classification for new/refreshed store rows
  and remote-result UI filters, independent of the direct-streaming toggle.
  Explicit photo/music/voice/round types take priority; filename fallback excludes
  encrypted documents and audio/sticker/image/animated attributes. Existing GIF
  grouping remains unchanged pending separate category controls. Isolated production
  classifier tests use protocol/message stubs, not full Android MessageObject.
  Old stored media_type values still require bounded background reclassification;
  no full-table blocking migration was added or claimed complete.
- Remote global cursor fallback corrected; 84 pager sequencing cases pass.
- Production local browsing, automatic matching and title-source queries now
  consume seek keys instead of offsets. Keys guard owner and ordering; raw invalid
  payloads advance the boundary, while lookahead is not consumed twice.
- Schema 8 adds matching recent/played ordering indexes; migration DDL tests cover
  1–8 and preserve user state. Existing redundant indexes retained for now.
- 12 desktop SQLite seek set/mutation/plan checks pass. Android/native tests are
  separate; in-memory UI growth, keyword indexing and durable scan remain open.
- Network-page indexing now snapshots bounded pages and writes each in one SQLite
  transaction, with account/revision checks before writing and before commit.
  The callback runs after transaction completion. Retry preserves each original
  network page (including its stream and revision), rather than flattening failed
  entries and regrouping across streams. Two failed small pages are tested to remain
  two independent writes with unchanged message identities/order. A storage outage
  clears queued streams, so failed data cannot
  accumulate across hundreds of sources while the UI remains stuck loading.
- Schema 9 adds owner/scope scan checkpoints. `acquireScan` retains the last
  committed cursor and replaces the previous worker lease. `commitScanPage`
  conditionally advances the sequence and writes the media in the same transaction;
  duplicate/stale leases cannot write pages. Empty eligible-media pages may still
  advance their remote cursor. Owner/revision checks reject obsolete work.
- Clear/logout removes both media and checkpoints transactionally. Cursor payloads
  are copied and bounded to 16 KiB; batches are bounded to 200 entries. Migration
  DDL tests now cover 1–9. SQL contract tests exercise rollback, duplicate/stale
  leases, empty pages, owner isolation and cleanup. These are not Android Java
  lifecycle or real-network tests.
- The checkpoint storage API is not yet wired to the scanner. Durable end-to-end
  resume, serialized remote cursor format and a screen-independent
  coordinator remain required before this feature is usable.
- Resume must preserve the historical backfill cursor while separately refreshing
  newer media. Treating an exhausted stored cursor as permanently complete would
  miss new arrivals; restarting the full history on each visit would defeat scale.
  Current screen-owned scan start/stop in TjMediaCenterActivity is not sufficient.

No feature may be called perfect or release-certified solely because compilation
passes. Record failed gates and limitations alongside successful checks.

## Interactive design preview check — 2026-09-12

The conversation preview was exercised in the in-app browser using synthetic
content only. At a 360px viewport the rendered product width was 313px (host
padding/scrollbar excluded). Removing photos retained the other five categories
and the MKV example. Search for `mkv` returned that item. Pausing changed the
status to existing-content-available while retaining the list. Measured input/button
bounds did not extend beyond the product's right edge. The trailing empty category
cell was corrected using wrapping, expanding rows. Screenshots were inspected in
the dark appearance. This is not Android UI, playback, full accessibility, light
theme, or real-data/performance certification. Mixed Hebrew/Latin filename direction
still needs Android-specific verification. Visual approval remains pending.

Separate front-refresh and historical-backfill scheduling was proposed for approval;
the checkpoint storage API must not be mistaken for an approved/complete scheduler.
