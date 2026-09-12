"""Synthetic catalog lookup, desktop SQLite. No Telegram messages or credentials."""
import argparse
import sqlite3
import time
import statistics

parser = argparse.ArgumentParser()
parser.add_argument('--rows', type=int, default=1000000)
parser.add_argument('--titles', type=int, default=100000)
args = parser.parse_args()
db = sqlite3.connect(':memory:')
db.executescript('''CREATE TABLE media(owner INTEGER,dialog INTEGER,mid INTEGER,date INTEGER,PRIMARY KEY(owner,dialog,mid));
CREATE INDEX media_recent_page ON media(owner,date DESC,dialog,mid);
CREATE TABLE media_local_catalog(owner INTEGER,dialog INTEGER,mid INTEGER,title_key TEXT,season INTEGER,episode INTEGER,PRIMARY KEY(owner,dialog,mid));
CREATE INDEX media_local_title ON media_local_catalog(owner,title_key,season,episode,dialog,mid);
CREATE INDEX media_catalog_sources ON media_local_catalog(title_key,owner,dialog,mid);
CREATE TABLE media_catalog_titles(title_key TEXT PRIMARY KEY,title TEXT,series INTEGER);
CREATE INDEX media_catalog_order ON media_catalog_titles(series,title,title_key);''')
started = time.monotonic()
for start in range(0, args.rows, 10000):
    rows = [(1, -(i % 101 + 1), i + 1, i) for i in range(start, min(start + 10000, args.rows))]
    db.executemany('INSERT INTO media VALUES(?,?,?,?)', rows)
    db.executemany('INSERT INTO media_local_catalog VALUES(?,?,?,?,?,?)',
                   ((o, d, m, 'rare' if m <= 20 else 'show' + str(m % args.titles), 1, m % 24 + 1) for o, d, m, _ in rows))
db.executemany('INSERT INTO media_catalog_titles VALUES(?,?,1)', (('show' + str(i), f'Show {i:09}') for i in range(args.titles)))
db.commit()
sql = 'SELECT dialog,mid FROM media NOT INDEXED WHERE owner=? AND rowid IN (SELECT m.rowid FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.owner=? AND c.title_key=?) ORDER BY date DESC,dialog,mid LIMIT 51'
times = []
for _ in range(11):
    start = time.perf_counter()
    found = db.execute(sql, (1, 1, 'rare')).fetchall()
    times.append((time.perf_counter() - start) * 1000)
assert len(found) == 20 and found[0][1] == 20
plan = [r[3] for r in db.execute('EXPLAIN QUERY PLAN ' + sql, (1, 1, 'rare'))]
assert any('INTEGER PRIMARY KEY' in row for row in plan), plan
print({'rows': args.rows, 'setup_seconds': round(time.monotonic() - started, 2),
       'first_ms': round(times[0], 2), 'warm_median_ms': round(statistics.median(times[1:]), 2),
       'worst_ms': round(max(times), 2), 'plan': plan, 'environment': 'desktop memory SQLite; not Android'})
directory = '''SELECT t.title,t.title_key FROM media_catalog_titles t WHERE t.series=1 AND t.title>=? AND (t.title>? OR t.title_key>?) AND EXISTS (
SELECT 1 FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid
WHERE c.title_key=t.title_key AND c.owner=1 AND c.dialog IN (-1,-2,-3)) ORDER BY t.title,t.title_key LIMIT 41'''
boundary = f'Show {args.titles // 2:09}'
times = []
for _ in range(11):
    start = time.perf_counter()
    page = db.execute(directory, (boundary, boundary, 'show' + str(args.titles // 2))).fetchall()
    times.append((time.perf_counter() - start) * 1000)
assert len(page) == len(set(page)) == 41
print({'directory_titles': args.titles, 'deep_scoped_title_first_ms': round(times[0], 2),
       'deep_scoped_title_warm_ms': round(statistics.median(times[1:]), 2),
       'plan': [row[3] for row in db.execute('EXPLAIN QUERY PLAN ' + directory, (boundary, boundary, 'show' + str(args.titles // 2)))]})
