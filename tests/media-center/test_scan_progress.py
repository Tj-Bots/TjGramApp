"""SQL transaction contract using production DDL; not Java/Android lifecycle coverage."""
import re
import sqlite3
from pathlib import Path

source = Path('TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java').read_text()
ddl = re.search(r'db.execSQL\("(CREATE TABLE media_scan_progress [^"\n]+)"\)', source).group(1)
predicate = re.search(r'"(owner=\? AND scope=\? AND lease=\? AND sequence=\?)"', source).group(1)
db = sqlite3.connect(':memory:')
db.execute(ddl)
db.execute('CREATE TABLE fixture_media(owner INTEGER, mid INTEGER, PRIMARY KEY(owner,mid))')
db.executemany('INSERT INTO media_scan_progress VALUES(?,?,?,?,?)',
               [(1, 'videos', 'old', 0, b''), (2, 'videos', 'other', 0, b'')])
db.commit()


def commit(owner, lease, sequence, position, rows, fail=False):
    try:
        with db:
            changed = db.execute('UPDATE media_scan_progress SET position=?,sequence=? WHERE ' + predicate,
                                 (position, sequence + 1, owner, 'videos', lease, sequence)).rowcount
            if changed != 1:
                return False
            for mid in rows:
                db.execute('INSERT OR REPLACE INTO fixture_media VALUES(?,?)', (owner, mid))
            if fail:
                raise sqlite3.IntegrityError('synthetic failed media write')
        return True
    except sqlite3.IntegrityError:
        return False


def state(owner=1):
    return db.execute('SELECT lease,sequence,position FROM media_scan_progress WHERE owner=?', (owner,)).fetchone()


assert commit(1, 'old', 0, b'page1', [1, 2])
assert state() == ('old', 1, b'page1')
assert not commit(1, 'old', 0, b'duplicate', [3])
assert not commit(1, 'old', 1, b'lost', [4], fail=True)
assert state() == ('old', 1, b'page1')
assert list(db.execute('SELECT mid FROM fixture_media ORDER BY mid')) == [(1,), (2,)]
with db:
    db.execute("UPDATE media_scan_progress SET lease='new' WHERE owner=1")
assert state() == ('new', 1, b'page1')
assert not commit(1, 'old', 1, b'stale', [5])
assert commit(1, 'new', 1, b'empty-final-page', [])
assert state() == ('new', 2, b'empty-final-page')
assert not commit(2, 'new', 0, b'wrong-owner', [6])
assert state(2) == ('other', 0, b'')
with db:
    db.execute('DELETE FROM fixture_media WHERE owner=1')
    db.execute('DELETE FROM media_scan_progress WHERE owner=1')
assert not commit(1, 'new', 2, b'after-clear', [7])
assert state(2) == ('other', 0, b'')
assert db.execute('PRAGMA integrity_check').fetchone() == ('ok',)
print('Scan SQL contract passed: atomic rollback, duplicate/stale leases, empty pages, owner isolation and clear')
