"""Production derived-schema invariants in desktop SQLite; not Android lifecycle tests."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaLocalIndex.java').read_text()
store = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java').read_text()
schema = ''.join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', store, re.S).group(1)))
db = sqlite3.connect(':memory:')
db.execute(schema)
ddl = re.findall(r'db.execSQL\("([^"]+)"\);', source.split('static void ensure', 1)[1].split('private static void ensureColumn', 1)[0])
for _ in range(2):
    for statement in ddl:
        db.execute(statement)
        if statement.startswith('CREATE TABLE IF NOT EXISTS media_local_catalog'):
            columns = {row[1] for row in db.execute('PRAGMA table_info(media_local_catalog)')}
            if 'catalog_key' not in columns:
                db.execute("ALTER TABLE media_local_catalog ADD COLUMN catalog_key TEXT NOT NULL DEFAULT ''")
for owner in [1, 2]:
    for mid in range(1, 205):
        db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,played) VALUES(?,7,?,?,x'00','show.mkv','caption',1,400)", (owner, mid, mid))
        db.execute("INSERT INTO media_local_catalog(owner,dialog,mid,document,title_key,title,series,season,episode,uncertain,manual) VALUES(?,7,?,?,'local:show','Show',1,1,?,0,1)", (owner, mid, mid, (mid - 1) // 3 + 1))
assert db.execute("SELECT COUNT(DISTINCT episode) FROM media_local_catalog WHERE owner=1").fetchone()[0] == 68
assert db.execute("SELECT COUNT(*) FROM media_local_catalog WHERE owner=1 AND episode=1").fetchone()[0] == 3
db.execute("UPDATE media SET metadata='tmdb',metadata_id=33 WHERE owner=1")
db.execute("UPDATE media SET metadata='',metadata_id=0 WHERE owner=1")
assert db.execute("SELECT COUNT(*) FROM media_local_catalog WHERE title_key='local:show' AND manual=1").fetchone()[0] == 408
db.execute("UPDATE media SET season_override=2,episode_override=9 WHERE owner=1 AND mid=1")
row = db.execute("SELECT CASE WHEN m.season_override=-2 THEN c.season ELSE m.season_override END, CASE WHEN m.episode_override=-2 THEN c.episode ELSE m.episode_override END FROM media m JOIN media_local_catalog c USING(owner,dialog,mid) WHERE m.owner=1 AND m.mid=1").fetchone()
assert row == (2, 9)
db.commit()
db.execute('BEGIN')
db.execute('DELETE FROM media WHERE owner=1')
assert db.execute('SELECT COUNT(*) FROM media_local_catalog WHERE owner=1').fetchone()[0] == 0
db.rollback()
assert db.execute('SELECT COUNT(*) FROM media_local_catalog WHERE owner=1').fetchone()[0] == 204
db.execute('DELETE FROM media WHERE owner=1')
assert db.execute('SELECT COUNT(*) FROM media_local_catalog WHERE owner=2').fetchone()[0] == 204
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert 'LIMIT 128' in source and 'setTransactionSuccessful' in source
assert 'current.getLong(0) == media.getAsLong("document")' in source
assert 'readLocalRecords(getReadableDatabase(), result, owner)' in store
assert 'GROUP BY ' in store and ' LIMIT 101' in store
print('Local catalog schema, identity, override, delete/rollback and bounded-backfill checks passed')
