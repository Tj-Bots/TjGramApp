# Series library — approved design rationale

The user approved full-screen movie/series details, using the supplied HBO
screenshots as a layout reference, while ordinary media keeps compact details.
The user subsequently approved local identification, dynamic folders and the full
build. This document preserves the design exploration, not a checklist of shipped
features. Current implementation, tests and remaining boundaries are in [README.md](README.md).
No live-account or native visual certification is claimed here.

## Opportunity

Find one series across the explicitly selected accounts/chats, browse seasons
and distinct episodes, and choose among actual Telegram sources without having
to understand upload naming conventions. Default scope remains the current
account. A group containing many shows must not become one series by assumption.

## Existing code evidence

- `TjMediaTitle.parse` extracts one season/episode pair from combined caption and
  filename, and selects a title hint from the caption's first line or filename.
  It does not reconcile contradictory hints, multi-episode files or source context.
- `TjMediaMatch.unique` accepts a unique normalized title/original-title match,
  constrained by year when supplied. Parsed hints are not verified file content.
- `TjMediaCatalog` groups confirmed TMDB identities by movie/TV kind and preserves
  distinct message identities; its seasons map supports several sources per episode.
- `TjMediaMetadata` currently searches titles, not complete season/episode metadata.
- `TjMediaCenterActivity.loadGroupSources` currently accumulates title sources
  before displaying choices. This is unsuitable for arbitrarily large duplicate
  collections and must not become the full-screen series page's query model.

## Considered ideas

Product perspective:
1. One searchable series entry instead of repeated upload cards.
2. Distinct-episode availability, with honest scan coverage.
3. Continue watching and explicit next available episode.
4. Favorite sources/quality preferences without losing alternative uploads.
5. Batch identification corrections with preview and undo.

Design perspective:
1. Hero artwork, clear play/continue action, concise factual metadata.
2. Season selector with recycled episode rows and progress indicators.
3. A source chooser showing chat/account, quality, size and known sender.
4. Separate available, unconfirmed and not-yet-found states.
5. Expandable original caption and message link; no duplicate text wall.

Engineering perspective:
1. Persistent title/season/episode/source relationships rather than in-memory
   aggregation of every message each time the screen opens.
2. Evidence-aware parsing: keep filename and caption hints separate and detect conflicts.
3. Conservative reconciliation with optional external metadata and local identity fallback.
4. Cursor-paged source queries and incremental episode summaries during indexing.
5. Scope-aware coverage and guarded updates for edits, removed access and logout.

## Top five, in implementation order

1. **Stable catalog identity.** Separate series -> season -> episode -> message
   source. This prevents duplicates and makes every subsequent screen meaningful.
   Validate same-name remakes, localized aliases, season zero and unknown numbering.
2. **Safe identification.** Automatically propose/assign only well-supported,
   unambiguous matches; expose uncertain items and manual correction. This avoids
   a polished UI built on wrong associations. Validate real Hebrew/Latin captions,
   uploader branding, date/year ambiguity and contradictory episode numbers.
3. **Series detail screen.** Hero, continue/play, season selector and episode list.
   This directly addresses the user's screenshot. Validate RTL, large text,
   absent artwork and stable scroll/dialog state while indexing continues.
4. **Explicit source selection.** An episode can have many files/messages across
   selected accounts/chats. Keep those sources separate and page their list.
   Show sender/channel signature only when exposed by the original message;
   forwarding attribution is not proof of the original uploader's identity.
   Validate multiple qualities, identical file copies, lost access and replaced media.
5. **Honest availability.** Show distinct found episodes and indexed-scope coverage.
   Do not label an entire show complete using a partial scan or file count.
   Validate season metadata revisions, double episodes, specials and incomplete scans.

## Decision before implementation

### User requirement: no TMDB credential by default

The user explicitly requires the experience to account for the many users who
will never configure a TMDB key. Local title identities, series/season/episode
organization, search, source selection, watch state and lists are core features,
not a degraded fallback or a credential-gated onboarding step. Existing catalog
grouping by positive TMDB ID alone does not satisfy this requirement.

Local catalog IDs must remain stable independently of external metadata IDs.
Adding or removing a TMDB credential must not destroy organization, manual
corrections, lists or watch state. External matching enriches a local identity;
it does not become its storage identity.

Without external metadata, use safe Telegram thumbnails, original message data
and explicit naming evidence. Strong consistent hints may support local grouping;
ambiguous aliases or conflicts require a visible proposal/manual correction,
not aggressive silent merging. Manual identification/grouping must work offline
without searching TMDB. Do not promise universal automatic title recognition.

Artwork may use a concealed/neutral fallback where unavailable or spoiler-marked.
Do not invent official episode names, descriptions, ratings, total season counts
or missing episodes. Count distinct locally found episodes, with scope/scan status.
No bundled credential, hidden metadata proxy or default caption upload is allowed.
The local library must remain usable when external requests fail or are disabled.

Options:

- Naming rules alone: fast/offline, but aliases, misleading captions and remakes
  produce false merges; it cannot reliably establish a complete expected catalog.
- Require TMDB identification for all series: a stronger shared external identity,
  but adds credential/network dependency and excludes unmatched/private content.
- **Recommended hybrid:** local parsing and stable local identities; optional TMDB
  enrichment; conservative automatic matching; visible uncertainty and manual
  correction. More state to maintain, but preserves ordinary-library usefulness
  and avoids claiming every guess is verified. External metadata is opt-in.

The hybrid local-first policy was approved. No new runtime dependency or metadata
proxy was added; optional TMDB requests retain the existing explicit credential flow.

## Intended behavior and safeguards

- Search a series once, then show seasons and one row per distinct known episode.
  Keep unidentified files reachable in the ordinary media library and an unresolved
  section; never drop them because a title match failed.
- Multiple source messages for one episode do not increase the episode count.
  Never delete or merge original messages/files. Owner/chat/message/document
  identity must survive catalog grouping and account slot reuse.
- Parse filename and caption independently. Conflicts remain unresolved rather
  than silently preferring one. Do not infer an entire mixed group's title, the
  uploader's identity, language, resolution or episode order from weak context.
- Display quality/language as declared when derived only from text. Do not invent
  runtime, artwork, ratings, cast, Dolby/HDR, subtitles or trailer availability.
- A file may cover more than one episode; the future relation must support that
  without pretending playback offsets are known. Unknown/special numbering stays
  separate until resolved; do not guess missing season/episode numbers.
- Playback progress belongs to the actual source file. Episode watched state may
  aggregate completed sources, but time offsets must not transfer blindly between
  versions with different intros/cuts/durations. Show which version will resume.
- Expected episode metadata and available message sources are separate. Use
  “12 episodes found in selected sources” until reliable comparison is possible;
  “not found in indexed sources” is not “does not exist in your account.”
- Fast first paint comes from lightweight catalog summaries, not loading every
  serialized message. Page seasons/episodes/sources as needed; update the index
  incrementally. An explicit deeper search can extend coverage without dismissing
  dialogs, replacing the screen, or blocking browsing behind full-history scans.

## Acceptance examples

- The same episode in three channels and two qualities appears once with sources.
- Two unrelated shows with the same translated name are not silently merged.
- A mixed group with ten series keeps ten separate identities.
- Caption E04 and filename E05 yields a correction opportunity, not false certainty.
- Partial indexing cannot display a green “complete series” badge.
- Renaming/replacing a message cannot carry stale episode identity/progress across files.
- All/selected accounts only reveal sources inside the chosen scope.
- Changing seasons, reading details or choosing a source survives scan updates.

No runtime visual, live Telegram completeness or new series-metadata API tests
are claimed by this planning document.
