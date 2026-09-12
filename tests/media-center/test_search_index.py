"""Run production FTS DDL with synthetic rows; not Android I/O or UI certification."""
import pathlib
import re
import sqlite3
import time

root = pathlib.Path(__file__).resolve().parents[2]
source = (root / "TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaSearchIndex.java").read_text()
ddl = source.split("static void ensure", 1)[1].split("/**", 1)[0]
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE media(owner INTEGER,search_text TEXT,title_text TEXT)")
# An old row predates installation of the derived index.
db.execute("INSERT INTO media VALUES(1,'legacy old film','')")
for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', ddl):
    db.execute(sql)
db.execute("INSERT INTO media VALUES(1,'movie mkv שלום עולם','מסע לכוכבים')")
db.execute("INSERT INTO media VALUES(2,'private movielist','')")
def find(query, owner=1):
    return db.execute("SELECT rowid FROM media WHERE owner=? AND rowid IN (SELECT docid FROM media_fts WHERE media_fts MATCH ?)", (owner,query)).fetchall()
assert find('"mov*"') == [(2,)]
assert find('"של*" "mkv*"') == [(2,)]
assert find('"לכוכ*"') == [(2,)]
assert find('"כוכ*"') == []  # Prefix semantics, not arbitrary substring/stemming.
assert find('"private*"') == []
assert find('"mov*"',2) == [(3,)]
assert find('"OR*"') == []
assert find('"missing*"') == []
db.execute("UPDATE media SET search_text='changed mp4',title_text='' WHERE rowid=2")
assert find('"mov*"') == [] and find('"changed*"') == [(2,)]
db.execute("DELETE FROM media WHERE rowid=2")
assert find('"changed*"') == []
db.execute("INSERT OR REPLACE INTO media_fts(docid,search_text,title_text) SELECT rowid,search_text,title_text FROM media WHERE rowid>0 ORDER BY rowid LIMIT 256")
assert find('"legacy*"') == [(1,)]
db.commit()
db.execute("BEGIN")
db.execute("UPDATE media SET search_text='rolledback' WHERE rowid=1")
db.rollback()
assert find('"legacy*"') == [(1,)] and find('"rolledback*"') == []
db.executemany("INSERT INTO media VALUES(1,?,'')", ((f'video caption file {i}',) for i in range(100000)))
db.execute("INSERT INTO media VALUES(1,'uniqueprobe','')")
start = time.perf_counter()
assert len(find('"uniqueprobe*"')) == 1
elapsed = (time.perf_counter()-start)*1000
db.execute("INSERT INTO media_fts(media_fts) VALUES('integrity-check')")
print(f"12 FTS prefix/owner/edit/delete/backfill/rollback checks; 100k rare query {elapsed:.2f} ms (desktop memory SQLite)")
