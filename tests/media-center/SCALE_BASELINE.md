# Desktop SQL baseline — 2026-09-12

The following baseline sections preserve measurements of earlier implementations,
identified by source hash. Statements about missing features describe those snapshots,
not the current branch. Current implementation is in [README.md](README.md). None of
these measurements is whole-app or device-performance certification.
Source SHA-256: `9e6848ec87a6ab3b359c9e2b71bd52392cd7683d6269cdbf5150cfc1341d1b22`.

Command: `python3 tests/media-center/benchmark_store.py --rows 100000 1000000 --repeats 5`

Environment: Linux x86_64, Python 3.14.4, SQLite 3.46.1; an 8 MiB SQLite page cache.
OS caches were not flushed. Production CREATE TABLE/index statements are extracted
from TjMediaStore; query projection/predicates/order mirror loadInternal. No ANALYZE
was added, matching fresh schema creation. Native Android SQLite may plan differently.

Fixtures have 95% of records in one owner and 5% in a second, 257 source IDs,
timestamps shared by 16 records, sparse favorites and mixed Hebrew/Latin captions.
Payloads are synthetic bytes, not serialized TL messages. The generated 1m-record
database occupied 2,109,988,864 bytes. Temporary databases were removed at exit.

| Query | 100k median ms | 1m median ms | 1m worst of five ms |
| --- | ---: | ---: | ---: |
| First 201 rows | 51.56 | 476.86 | 478.49 |
| Page at 90% depth | 155.96 | 1471.89 | 1501.58 |
| Rare text result | 93.12 | 896.29 | 916.97 |
| Absent text | 95.20 | 866.42 | 878.32 |
| Sparse favorites | 54.57 | 465.24 | 470.25 |
| Video category | 52.13 | 479.64 | 480.19 |

All observed query plans selected `media_title (owner=?)` and used a temporary
B-tree for ORDER BY. This dataset demonstrates a real planner/sort risk; it is not
evidence that every real account or SQLite build chooses that plan.

The insertion timing is intentionally not a scan speed estimate: fixture setup
uses one transaction, whereas current production index() schedules each message
separately and is also subject to transport, serialization, lifecycle and disk I/O.

## Implications

- Matching order indexes and seek-based pagination need comparison against this
  baseline, including equal timestamps and duplicate account/message IDs.
- Text-search indexing must address expensive negative/rare queries, not only
  common matches found in the first page.
- Full Android object construction, UI binding, GC, frame timing and remote
  pagination remain separate unmeasured costs.
- Five repeats are a quick diagnostic, not a statistically robust p95 study.
- No million-row UI or live Telegram scan has been verified by this benchmark.

## Experimental cursor comparison (not yet production)

Command: `python3 tests/media-center/benchmark_store.py --rows 1000000 --repeats 5 --compare-cursor`

An additional index `(owner,date DESC,dialog,mid)` and a date-bound seek predicate
were tested in the temporary database only. The returned identity list exactly
matched the OFFSET reference, including equal-timestamp boundaries.

| Query on 1m fixture | Median ms |
| --- | ---: |
| Original first page, this comparison run | 462.75 |
| Original deep page, this comparison run | 1461.11 |
| Matching index, first page | 0.21 |
| Matching index, deep OFFSET page | 27.88 |
| Matching index, deep seek page | 0.21 |

Experimental index build: 685.49 ms; database including it: 2,130,583,552 bytes.
Seek plan: `SEARCH media USING INDEX bench_recent_cursor (owner=? AND date<?)`.
These are local SQL observations on this fixture, not promised app latencies.
This does not fix substring search or prove sparse-filter performance, migrations,
cross-account merged pagination or bounded Android UI memory.

## Production seek implementation — five million rows

After migrating production to schema 8 and seek keys:

`python3 tests/media-center/benchmark_store.py --rows 5000000 --repeats 3 --compare-cursor --temp-dir /home/avi/dev`

Store SHA-256: `f35d38538b500aa20d5e661989f5b70413c85e51dc141324f047204111402711`.
4,750,000 rows belong to owner 1; 250,000 to owner 2. Database size:
10,757,681,152 bytes. Fixture setup 43.61 seconds (batched synthetic inserts, not
production ingestion timing). The database was disk-backed and removed at exit.

| SQL operation | Median ms | Worst of three ms |
| --- | ---: | ---: |
| First page (initial observations) | 0.27 | 1.80 |
| Deep seek page | 0.27 | 0.31 |
| Deep OFFSET reference on same indexes (not production path) | 153.69 | 154.34 |
| Sparse favorites, first page | 13.14 | 13.53 |
| Video filter, first page | 0.60 | 0.96 |
| Rare substring search | 5859.39 | 8401.33 |
| Missing substring search | 5570.60 | 5588.09 |

The production seek predicate matched reference identities exactly and used
`media_recent_page (owner=? AND date<?)`. No claim of whole-app speed follows:
UI records still accumulate, search is still slow, and background ingestion and
Android migration latency remain separate work. Three repeats are diagnostic only.

## Android SQLite correctness check

Android 15 / API 35 Google APIs x86_64 emulator `TjMediaApi35`, SQLite 3.44.3.
`android_seek_sql.py` emitted the current production schema/indexes/predicates and
10,000 synthetic rows into the emulator sqlite3 shell. Date and played-time page
identity comparisons, deletion/new-arrival scenarios and owner isolation passed;
`PRAGMA integrity_check` returned `ok`. Both native plans used the corresponding
covering page index with a time range. No account was connected.

This tests actual Android SQLite semantics, not SQLiteOpenHelper callbacks, Java
cursor extraction, real TL serialization, UI, playback or Telegram transport.
The test database is synthetic and remains isolated in the emulator's local temp
directory; it is not the app's library database.

## Local title lookup and directory — current implementation

Command: `python3 tests/media-center/benchmark_catalog.py --rows 5000000 --titles 100000`

The desktop in-memory fixture contains five million minimal source rows and
100,000 title keys. These are not serialized Telegram messages. Setup took 22.78
seconds using batched synthetic inserts, not production ingestion. Eleven reads
were measured; “warm” excludes the first. Neither OS caches nor Android runtime
costs are represented.

| Query | First ms | Warm median ms |
| --- | ---: | ---: |
| Rare title, 20 matching sources | 0.40 | 0.03 |
| Deep alphabetical directory page, filtered to three chats | 0.31 | 0.10 |

The source query probes media row IDs from the title index, rather than walking
the entire chronological index to find a rare title. The directory seeks its
`series,title,title_key` index and verifies source membership with indexed EXISTS
probes. It returns one title regardless of how many upload rows match. A previous
experiment that let SQLite pick the owner/date scan took approximately 214 ms on
the same-scale rare lookup; source queries now explicitly retain row-ID probes.

This benchmark mirrors the production lookup shape with a minimal schema. It does
not measure full-message deserialization, metadata requests, disk-backed cold I/O,
view binding, frame timing, concurrent Android scans or arbitrary sparse searches.
It is not a promise that the full app opens in sub-millisecond time.

The current title-directory DDL, cleanup triggers, scoped lookup boundaries and
rollback assertions additionally passed in isolated **Android emulator sqlite3**
using `android_catalog_sql.py`; integrity check returned `ok`. No APK or real
account was needed for that SQL test. This does not test Android UI or OpenHelper
lifecycle callbacks.
