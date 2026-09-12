"""Offline scale baseline using production schema/indexes and synthetic payloads.

This does not run Android, deserialize TL messages or contact Telegram. Temporary
databases are owned by this process and removed at exit. No user data is read.
Run: python3 tests/media-center/benchmark_store.py --rows 100000 1000000
"""
import argparse
import hashlib
import json
import math
import pathlib
import platform
import re
import sqlite3
import statistics
import tempfile
import time


ROOT = pathlib.Path(__file__).resolve().parents[2]
SOURCE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java"


def production_schema(source):
    create = source.split("public void onCreate(SQLiteDatabase db)", 1)[1].split("@Override", 1)[0]
    statements = []
    for statement in re.findall(r"db.execSQL\((.*?)\);", create, re.S):
        literals = re.findall(r'"([^"\\]*(?:\\.[^"\\]*)*)"', statement)
        statements.append("".join(literals))
    assert statements and statements[0].startswith("CREATE TABLE media ")
    return statements


def fill(db, rows):
    # Long mixed-language captions, equal timestamps, sparse matches, media kinds,
    # multiple owners/sources. Payloads are synthetic bytes, NOT valid TL messages.
    insert = """INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,
        search_text,title_text,source_type,media_type,played,favorite,position,duration)
        VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"""
    caption = "טיול צפייה סרט הרצאה משפחה science travel media library " * 6
    for start in range(0, rows, 2000):
        batch = []
        for n in range(start, min(rows, start + 2000)):
            owner = 2 if n % 20 == 0 else 1
            filename = "clip_%09d.mkv" % n
            text = caption + (" rarestneedle " if n == rows // 2 + 1 else "")
            date = 1700000000 - n // 16
            batch.append((owner, -(n % 257 + 1), n + 1, n + 100, b"fixture" * 64,
                          filename, text, date, filename + " " + text, "", n % 3 + 1,
                          n % 6 + 1, date if n % 31 == 0 else 0, int(n % 101 == 0),
                          30000 if n % 31 == 0 else 0, 120000))
        db.executemany(insert, batch)
    db.commit()


def measure(db, sql, args, repeats):
    plan = [r[3] for r in db.execute("EXPLAIN QUERY PLAN " + sql, args)]
    durations = []
    result = None
    for _ in range(repeats):
        start = time.perf_counter()
        result = db.execute(sql, args).fetchall()
        durations.append((time.perf_counter() - start) * 1000)
    ordered = sorted(durations)
    return {"first_observed_ms": round(durations[0], 2),
            "median_ms": round(statistics.median(durations), 2),
            "p95_ms": round(ordered[math.ceil(len(ordered) * .95) - 1], 2),
            "rows_returned": len(result), "plan": plan}


def run(rows, repeats, source, compare_cursor, temp_dir):
    with tempfile.TemporaryDirectory(prefix="tj-media-scale-", dir=temp_dir) as directory:
        path = pathlib.Path(directory) / "synthetic.sqlite"
        db = sqlite3.connect(path)
        try:
            db.execute("PRAGMA cache_size=-8192")
            for statement in production_schema(source):
                db.execute(statement)
            start = time.perf_counter()
            fill(db, rows)
            inserted = time.perf_counter() - start
            owner_count = db.execute("SELECT count(*) FROM media WHERE owner=1").fetchone()[0]
            # Same selected columns/order/filter as loadInternal; excludes Android
            # object construction, which would add work beyond these SQL timings.
            columns = ("data,position,duration,played,watched,favorite,collection,metadata,"
                       "series,source_type,season_override,episode_override,metadata_origin,date,dialog,mid")
            base = "SELECT " + columns + " FROM media WHERE owner=?"
            order = " ORDER BY date DESC, dialog, mid LIMIT 201 OFFSET "
            queries = {
                "first_page": (base + order + "0", (1,)),
                "deep_page_90pct": (base + order + str(owner_count * 9 // 10), (1,)),
                "rare_search": (base + " AND instr(search_text || ' ' || title_text, ?)>0" + order + "0", (1, "rarestneedle")),
                "missing_search": (base + " AND instr(search_text || ' ' || title_text, ?)>0" + order + "0", (1, "not-in-any-caption")),
                "sparse_favorites": (base + " AND favorite=1" + order + "0", (1,)),
                "video_filter": (base + " AND media_type=?" + order + "0", (1, 2)),
            }
            results = {name: measure(db, sql, args, repeats) for name, (sql, args) in queries.items()}
            assert results["rare_search"]["rows_returned"] == 1
            assert results["missing_search"]["rows_returned"] == 0
            assert results["first_page"]["rows_returned"] == 201
            comparison = None
            if compare_cursor:
                production_cursor = "TjMediaPageKey.DATE_ORDER" in source
                started = time.perf_counter()
                if not production_cursor:
                    db.execute("CREATE INDEX bench_recent_cursor ON media(owner,date DESC,dialog,mid)")
                db.commit()
                index_ms = (time.perf_counter() - started) * 1000
                depth = owner_count * 9 // 10
                # A real cursor arrives with the preceding page; this lookup only
                # locates a known reference boundary in the synthetic fixture.
                boundary = db.execute("SELECT date,dialog,mid FROM media WHERE owner=1"
                                      " ORDER BY date DESC,dialog,mid LIMIT 1 OFFSET ?", (depth - 1,)).fetchone()
                date, dialog, mid = boundary
                seek_where = " AND date<=? AND (date<? OR dialog>? OR (dialog=? AND mid>?))"
                if production_cursor:
                    key_source = (SOURCE.parent / "TjMediaPageKey.java").read_text()
                    seek_where = re.search(r'DATE_AFTER = "([^"]+)"', key_source).group(1)
                seek = base + seek_where + " ORDER BY date DESC,dialog,mid LIMIT 201"
                seek_args = (1, date, date, dialog, dialog, mid)
                # Compare identities, not payloads (many fixture payloads are equal).
                identities = "SELECT owner,dialog,mid FROM media WHERE owner=?"
                expected = db.execute(identities + order + str(depth), (1,)).fetchall()
                actual = db.execute(identities + seek_where + " ORDER BY date DESC,dialog,mid LIMIT 201", seek_args).fetchall()
                assert expected == actual and len(set(actual)) == len(actual)
                comparison = {
                    "kind": "production-cursor-sql" if production_cursor else "experimental-only-not-production",
                    "index_build_ms": round(index_ms, 2),
                    "first_page_matching_index": measure(db, base + order + "0", (1,), repeats),
                    "deep_offset_matching_index": measure(db, base + order + str(depth), (1,), repeats),
                    "deep_cursor_matching_index": measure(db, seek, seek_args, repeats),
                    "cursor_ids_equal_offset_reference": True,
                }
            return {"fixture_rows": rows, "owner_rows": owner_count,
                    "database_bytes": path.stat().st_size,
                    "fixture_insert_seconds": round(inserted, 2), "queries": results,
                    "cursor_comparison": comparison}
        finally:
            db.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rows", type=int, nargs="+", default=[100000, 1000000])
    parser.add_argument("--repeats", type=int, default=7)
    parser.add_argument("--compare-cursor", action="store_true", help="Compare an experimental matching index and seek query in the temporary database only")
    parser.add_argument("--temp-dir", type=pathlib.Path, help="Existing directory for temporary fixture databases; use a disk-backed directory for multi-million-row runs")
    args = parser.parse_args()
    if any(n < 1000 or n > 5000000 for n in args.rows) or not 1 <= args.repeats <= 30:
        parser.error("rows must be 1,000–5,000,000; repeats 1–30")
    source = SOURCE.read_text()
    print(json.dumps({"kind": "desktop-synthetic-sql-offset-reference-and-optional-cursor", "sqlite": sqlite3.sqlite_version,
                      "python": platform.python_version(), "platform": platform.platform(),
                      "production_source_sha256": hashlib.sha256(source.encode()).hexdigest(),
                      "repeats": args.repeats,
                      "limitations": "OS cache not flushed; first observed is not a cold-disk claim. No TL deserialization, Android UI or Telegram transport."}, ensure_ascii=False), flush=True)
    for rows in args.rows:
        print(json.dumps(run(rows, args.repeats, source, args.compare_cursor, args.temp_dir), ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
