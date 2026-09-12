"""Exercise the production SQLite schema/predicates, not Android lifecycle or I/O."""
import pathlib
import re
import sqlite3

root = pathlib.Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java").read_text()
schema = re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', source, re.S).group(1)
schema = "".join(re.findall(r'"([^"]*)"', schema))
db = sqlite3.connect(":memory:")
db.execute(schema)
for owner, mid, position, duration, watched in [
    (1, 1, 5000, 10000, 0), (2, 1, 8000, 10000, 0),
    (1, 2, 10000, 10000, 0), (1, 3, 5000, 10000, 1),
    (1, 4, 0, 10000, 0), (1, 5, 5000, 0, 0),
]:
    db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,position,duration,watched,search_text) "
               "VALUES(?,7,?,9,?, ?, ?,100,?,?,?,'movie mkv שם הסרט')",
               (owner, mid, b"test", "movie.mkv", "שם הסרט", position, duration, watched))
predicate = re.search(r'if \(mode == 1\) where \+= "([^"]+)"', source).group(1)
assert db.execute("SELECT mid FROM media WHERE owner=1" + predicate).fetchall() == [(1,)]
assert db.execute("SELECT position FROM media WHERE owner=2 AND dialog=7 AND mid=1").fetchone() == (8000,)
assert db.execute("SELECT COUNT(*) FROM media").fetchone() == (6,)
assert " AND rowid IN (SELECT docid FROM media_fts WHERE media_fts MATCH ?)" in source
# Prefix-search behavior and trigger synchronization are exercised in test_search_index.py.
db.execute("UPDATE media SET favorite=1 WHERE owner=1 AND dialog=7 AND mid=1")
assert db.execute("SELECT favorite FROM media WHERE owner=2 AND dialog=7 AND mid=1").fetchone() == (0,)
db.execute("UPDATE media SET position=6000 WHERE owner=1 AND dialog=7 AND mid=1 AND document=99")
assert db.execute("SELECT position FROM media WHERE owner=1 AND dialog=7 AND mid=1").fetchone() == (5000,)

# Directory names are scoped by owner; similarly named lists cannot leak across accounts.
db.execute("UPDATE media SET collection='Weekend' WHERE owner=1")
db.execute("UPDATE media SET collection='Private' WHERE owner=2")
directory = re.search(r'"(SELECT DISTINCT collection FROM media[^"\n]+)"', source).group(1)
assert db.execute(directory, ("1",)).fetchall() == [("Weekend",)]
assert db.execute(directory, ("2",)).fetchall() == [("Private",)]
assert db.execute(directory, ("3",)).fetchall() == []

# A filtered match outside the first unfiltered page must still be found.
for mid in range(100, 351):
    db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,source_type,media_type,metadata,metadata_id,series) "
               "VALUES(3,8,?,9,?,'movie.mkv','',?,3,2,'{}',42,?)", (mid, b"test", mid, int(mid % 2 == 0)))
db.execute("UPDATE media SET source_type=1, media_type=3 WHERE owner=3 AND mid=100")
for production in [" AND source_type=?", " AND ((1 << media_type) & ?) != 0", " AND metadata_id=?"]:
    assert production in source
assert db.execute("SELECT mid FROM media WHERE owner=3 AND source_type=? AND ((1 << media_type) & ?) != 0 ORDER BY date DESC LIMIT 201",
                  (1, 1 << 3)).fetchall() == [(100,)]
movie_count = db.execute("SELECT count(*) FROM media WHERE owner=3 AND metadata_id=42 AND series=0").fetchone()[0]
series_count = db.execute("SELECT count(*) FROM media WHERE owner=3 AND metadata_id=42 AND series=1").fetchone()[0]
assert (movie_count, series_count) == (125, 126)

# Pagination consumes 200 raw rows plus one lookahead, without counting it twice.
assert '"201 OFFSET "' not in source and "result.nextKey = new TjMediaPageKey" in source
first = db.execute("SELECT mid FROM media WHERE owner=3 ORDER BY date DESC, dialog, mid LIMIT 201 OFFSET 0").fetchall()
second = db.execute("SELECT mid FROM media WHERE owner=3 ORDER BY date DESC, dialog, mid LIMIT 201 OFFSET 200").fetchall()
assert len(first) == 201 and len(second) == 51
assert first[200] == second[0]
assert len(set(first[:200] + second)) == 251
assert not set(first[:200]).intersection(second)
assert '"owner=? AND dialog=? AND mid<=?"' in source
db.execute("DELETE FROM media WHERE owner=? AND dialog=? AND mid<=?", (3, 8, 200))
assert db.execute("SELECT min(mid),count(*) FROM media WHERE owner=3").fetchone() == (201, 150)
assert db.execute("SELECT count(*) FROM media WHERE owner=1").fetchone() == (5,)
assert db.execute("SELECT season_override,episode_override FROM media WHERE owner=1 LIMIT 1").fetchone() == (-2, -2)
db.execute("UPDATE media SET season_override=0,episode_override=1 WHERE owner=1 AND mid=1")
assert db.execute("SELECT season_override,episode_override FROM media WHERE owner=2 AND mid=1").fetchone() == (-2, -2)
print("Production SQLite schema/predicate checks passed")
assert " AND metadata=''" in source and "snapshot.current(db) ? db.update" in source
for name in ["setFlags", "saveMetadata", "setEpisode"]:
    body = re.split(r"(?:public|private) void " + name + r"\(", source, maxsplit=1)[1].split("\n    public ", 1)[0]
    assert "index(message)" not in body, name + " must not resurrect an old snapshot"
    assert "snapshot.current(db)" in body
db.execute("UPDATE media SET metadata='manual',metadata_id=12 WHERE owner=1 AND mid=1")
db.execute("UPDATE media SET metadata='auto' WHERE owner=1 AND dialog=7 AND mid=1 AND document=9 AND metadata=''")
assert db.execute("SELECT metadata FROM media WHERE owner=1 AND mid=1").fetchone() == ('manual',)
db.execute("UPDATE media SET metadata='auto' WHERE owner=1 AND dialog=7 AND mid=2 AND document=99 AND metadata=''")
assert db.execute("SELECT metadata FROM media WHERE owner=1 AND mid=2").fetchone() == ('',)
print("Automatic metadata preserves manual matches and replacement documents")

# Exercise literal migration DDL from each previous schema version. Java backfills
# and Android SQLiteOpenHelper lifecycle are deliberately not simulated here.
added = {
    2: ["metadata TEXT NOT NULL DEFAULT ''", "series INTEGER NOT NULL DEFAULT 0"],
    3: ["search_text TEXT NOT NULL DEFAULT ''", "title_text TEXT NOT NULL DEFAULT ''"],
    4: ["source_type INTEGER NOT NULL DEFAULT 0", "media_type INTEGER NOT NULL DEFAULT 0"],
    5: ["metadata_id INTEGER NOT NULL DEFAULT 0"],
    6: ["season_override INTEGER NOT NULL DEFAULT -2", "episode_override INTEGER NOT NULL DEFAULT -2"],
    7: ["metadata_origin INTEGER NOT NULL DEFAULT 0"],
}
migrations = []
for condition in re.finditer(r"if \(oldVersion < (\d+)\) \{", source):
    start, depth, end = condition.end(), 1, condition.end()
    while depth:
        if source[end] == "{": depth += 1
        elif source[end] == "}": depth -= 1
        end += 1
    statements = re.findall(r'db.execSQL\("((?:ALTER TABLE|CREATE INDEX|CREATE TABLE)[^"\n]+)"\)', source[start:end])
    migrations.append((int(condition.group(1)), statements))
expected_columns = [row[1:5] for row in db.execute("PRAGMA table_info(media)")]
scan_schema = re.search(r'db.execSQL\("(CREATE TABLE media_scan_progress [^"\n]+)"\)', source).group(1)
for old_version in range(1, 10):
    previous = schema
    for version, declarations in added.items():
        if version > old_version:
            for declaration in declarations:
                previous = previous.replace(declaration + ", ", "")
    migrated = sqlite3.connect(":memory:")
    migrated.execute(previous)
    if old_version >= 9:
        migrated.execute(scan_schema)
    migrated.execute("CREATE INDEX media_recent ON media(owner,date DESC)")
    migrated.execute("CREATE INDEX media_played ON media(owner,played DESC)")
    if old_version >= 5:
        migrated.execute("CREATE INDEX media_title ON media(owner,metadata_id,series)")
    if old_version >= 8:
        migrated.execute("CREATE INDEX media_recent_page ON media(owner,date DESC,dialog,mid)")
        migrated.execute("CREATE INDEX media_played_page ON media(owner,played DESC,dialog,mid)")
    migrated.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,position,duration,played,favorite,collection) "
                     "VALUES(1,7,1,9,?,'example.mkv','caption',100,1234,10000,999,1,'Keep me')", (b"test",))
    for version, statements in migrations:
        if version > old_version:
            for statement in statements: migrated.execute(statement)
    assert sorted(row[1:5] for row in migrated.execute("PRAGMA table_info(media)")) == sorted(expected_columns)
    assert migrated.execute("SELECT position,duration,played,favorite,collection FROM media").fetchone() == (1234,10000,999,1,'Keep me')
    indexes = {row[1] for row in migrated.execute("PRAGMA index_list(media)")}
    assert {"media_recent_page", "media_played_page"}.issubset(indexes)
    assert len(list(migrated.execute("PRAGMA table_info(media_scan_progress)"))) == 5
    migrated.close()
print("Migration DDL preserves schema and user state from versions 1–9")
