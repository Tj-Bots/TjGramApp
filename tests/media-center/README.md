# TjGram Media Center

Isolated logic/resource checks and Java compilation pass. Device, visual, playback
and real TMDB verification remain pending user validation after the CI build.
The user authorized committing and pushing the feature without a local APK build.
This is not device certification; the acceptance gates below remain open.

## Behavior and navigation

Open **TjGram settings > Media center**. Default scope: current account. All active
accounts or selected accounts are optional; account IDs are resolved from owner
user IDs, never assumed from reusable slot numbers. Sources can be private chats,
groups, channels or selected chats. Ordinary general media remains available
regardless of whether movie identification succeeds.

Home contains featured, continue-watching, recent, favorite, movie and series
shelves. Movies/series use one card per identified TMDB title. The view selector
also exposes history, watched, local index and named lists. Lists are selected
from an account-scoped directory; each item currently has one named list plus an
independent favorite flag.

Remote search uses Telegram's matching; local search normalizes Unicode and
matches tokens across filename, caption and confirmed title. Source/type filtering
happens before local pagination. Exact-title retrieval walks all local pages for
episode/quality selection, independently of the first visible catalog page.

**Scan selected sources** explicitly indexes older media without downloading files.
The scan does not retain an ever-growing list of message objects. It limits requests,
stops automatic paging on error, and stops on screen exit or scope changes. Its
completion waits for local writes, failed writes are retried before new pages,
and network pumping pauses when the storage queue lags. Its
counter is processed entries, not a guaranteed unique total. Stop retains indexed
records. Restart currently starts at the beginning, updating existing records.

**Identify movies and series** is another explicit, cancellable operation. It scans
already-indexed videos from selected sources/accounts that have a TMDB credential.
Only extracted title hints are sent. Exact normalized title/original-title matches
must be unique, with a matching year when available. Without episode hints, both
movie and TV results are checked so same-name ambiguity is not silently resolved.
Results are cached for repeated episodes; ambiguous files stay manually identifiable.
Existing manual matches and replaced documents are guarded at the database update.
Automatic identification is labelled in details and can be reviewed/corrected;
an exact name match is not a guarantee that the file contains that title. Old
matches of unknown origin are not falsely classified as manually approved.

Details show source caption, file size/name/quality and optional TMDB information.
Play/resume passes the original account/message and timestamp into Telegram's
existing player route; source-message opening is a separate action. Resume excludes
watched and >=98%-completed files. Manual season/episode overrides affect only the
local catalog; clearing fields returns to filename/caption hints.
Numbered series offer **Next available episode**, limited to the indexed selected
sources. It handles season boundaries and gaps without inventing missing episodes
or automatically starting playback. Episode lists summarize watched/completed/
in-progress state; version selection includes account, chat, quality, size and progress.

## Implementation map

- TjMediaCenterActivity: native screen, scope controls, dialogs, search debounce,
  local page aggregation and cancellable explicit scan.
- TjMediaHomeView / TjMediaCardCell: themed cards/shelves, artwork fallbacks,
  spoiler concealment, progress, RTL and shelf scroll preservation.
- TjMediaLibrary: lifecycle/generation-cancelled Telegram media cursors, max three
  active requests per instance; normal and index-only modes.
- TjMediaStore: app-private SQLite on a serial worker queue, schema version 7.
  Identity is owner/dialog/message with a document guard. Data includes serialized
  media, normalized search, progress/history, favorite/watched/list, confirmed
  metadata, identification origin and manual season/episode overrides. Document replacement resets stale
  progress/identification/episode data. Indexed edits, deletion, explicit history
  clear and logout are observed; cache refresh is not a deletion.
- TjMediaTitle / TjMediaCatalog: pure Java tentative parsing/search, grouping and
  next-numbered-episode selection. Ambiguous identification requires manual choice.
- TjMediaMetadata / TjMediaAutoMatcher / TjMediaMatch / TjConfig: existing OkHttp plus Android Keystore encryption;
  no default credential or endpoint controlled by TjGram. Only the selected query,
  media type and language are sent to TMDB. Responses are bounded, redirects and
  automatic retries disabled, credential-bearing exceptions never logged.
  Removing a credential prevents new external artwork requests.
- PhotoViewer / MediaController: progress for indexed ordinary video/audio every five seconds and at
  release. Message/owner are captured for the player, independently of the visible
  message, avoiding cross-item writes on transitions. Editing previews/live photos
  are excluded. Music/voice/round-video hooks also guard the exact player instance.
  TjMediaPlaybackProgress preserves natural completion through PhotoViewer's rewind
  and clears it on replay/seek, without changing the manual watched flag.

Manual edits validate the existing message/document/edit/photo identity on the same
database queue and never reinsert UI snapshots. Source revisions reject stale pages
and failed-write retries after deletion/clear. Clear stops scanning/matching before
deleting. Credential reads/writes use immutable owner IDs as their actual storage
keys. Missing/stale local item state offers a visible retry/source-opening fallback.

Grid columns adapt to the actual container width and font scale. Cards expose one
combined screen-reader description, action labels have 48dp minimum touch height,
and the settings icon has an accessible name. These source changes are not a
substitute for device TalkBack and visual testing.

Secret, TTL and auto-deleting media are excluded. Ordinary protected media remains
app-private and opens through existing Telegram behavior. No Ghost-mode or secure
window weakening is introduced. Clearing the index never deletes Telegram messages
or media files. No automatic index-size pruning is implemented.

## Verification

Run from repository root:

~~~sh
bash tests/media-center/run_checks.sh
env GRADLE_USER_HOME=/tmp/tj-gradle-cache ./gradlew -p tests/media-center/network checkMetadata
env ANDROID_HOME=/home/avi/Android/Sdk GRADLE_USER_HOME=/tmp/tj-gradle-cache ./gradlew :TMessagesProj_App:compileAfatDebugJavaWithJavac
~~~

- 24 parsing/search checks: English/Hebrew, caption precedence, numeric titles,
  Hebrew/Latin episode syntax, quality, Unicode and empty inputs.
- 13 catalog checks: source identity, owner separation, movie/TV ID collisions,
  versions, special/unknown season ordering, next episode, season transitions and gaps.
- 9 automatic matching checks: ambiguity, remakes, years, translated/original names,
  duplicate responses and episode-based type constraints.
- 9 playback checkpoint checks: natural end, automatic rewind, release, replay,
  seek, reset and invalid duration; these exercise the production state helper.
- 34 pager sequencing checks compile the production TjMediaLibrary against test-only
  protocol/storage boundaries: request limits/filters, write completion, write retry,
  deletion revisions, late canceled responses, owner replacement and TTL exclusion.
  Extended cases cover cross-account identity/deduplication, selected/empty sources,
  slow-storage backpressure and recovery, queued stale writes and repeated cursors.
  No Telegram request is made; Android loopers, serialization and real SQLite are
  not simulated by this harness.
- SQLite tests extract production schema/predicates and exercise account boundaries,
  resume rules, literal search, list directories, >200-row pagination, pre-pagination
  filters, title types, partial history deletion and episode override defaults.
  Literal migration DDL from versions 1–7 preserves history/list state. These are
  not Android SQLiteOpenHelper, Java migration backfill or notification-order tests.
- Resource tests check English/Hebrew/runtime fallback, placeholders, referenced keys
  and logo XML. Latest run: 97 keys passed.
- The isolated network harness compiles the production metadata service with small
  Android/account/credential stubs and in-process OkHttp responses. It covers request
  fields, parsing, invalid credentials, HTTP/rate-limit errors, oversized/malformed
  responses, cancellation and owner changes. No live API call is made. The JSON-Java
  dependency belongs only to this standalone test build, not the application.
  Latest run: 45 checks passed.
- Incremental Java/resource compilations passed; existing upstream resource and
  deprecated-API warnings remain.
- adb has no connected device, and the local SDK has no emulator executable. No
  runtime screenshots, playback tests or actual TMDB credential calls are claimed.

Focused deep-review covered Android, concurrency, SQLite and error propagation.
Five baseline findings were fixed: captured-owner credential storage, stale manual
edits, scan/clear ordering, completion overwritten by rewind, and silent item-state
failure. Follow-up concurrency review found no remaining issue in those owner/write/
revision fixes. See [REVIEW.md](REVIEW.md) for evidence and remaining acceptance gates.

## Remaining verification and product boundaries

Before release: test navigation, large text/RTL/tablet layout, process/screen
recreation, multi-account switching/logout, scans/retries/cancellation, source
selection, PiP/player transitions, actual playback/resume, Android migrations and
notification order on a device. Exercise real TMDB authentication, malformed
responses, offline and rate limits without logging credentials.

Current boundaries: conservative automatic matching with manual fallback,
one named list per item, per-file watched/progress rather than title-level state,
unverified playback tracking on devices, no durable scan checkpoint, no automatic index quota,
and no startup reconciliation for deletions while offline. Indexed data can be stale
until Telegram emits an update or a source is searched again. These gaps must not be
hidden behind a claim that the media center is complete or release-ready.

## External sources and TMDB attribution

No third-party app source or new runtime dependency was imported. Research included
Telegram-Stremio (GPL-3.0 server architecture) and Flick (Apache-2.0 Compose app);
neither was copied into this native Telegram integration.

Official docs:
[authentication](https://developer.themoviedb.org/docs/authentication-application),
[movie search](https://developer.themoviedb.org/reference/search-movie),
[TV search](https://developer.themoviedb.org/reference/search-tv),
[images](https://developer.themoviedb.org/docs/image-basics),
[attribution](https://developer.themoviedb.org/docs/faq).

tj_tmdb_logo.xml is a format-only conversion of the official **Alt short (blue)**
[SVG](https://www.themoviedb.org/assets/v4/logos/v2/blue_short-8e7b30f73a4020692ccca9c88bafe5dcb6f8a62a4c6bc55cd9ba82bb2cd95f6c.svg)
from [TMDB logos](https://www.themoviedb.org/about/logos-attribution).
Geometry, gradient stops and viewport are preserved, with no recoloring/mirroring.
TMDB owns its mark; no endorsement is implied. About includes the logo, localized
attribution and the exact required English notice. Device rendering remains untested.
