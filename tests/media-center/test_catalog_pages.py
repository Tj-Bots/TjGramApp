"""Global title directory/paging contract, using production DDL and key predicates."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
java = root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj'
local = (java / 'TjMediaLocalIndex.java').read_text()
store = (java / 'TjMediaStore.java').read_text()
key = (java / 'TjMediaCatalogPageKey.java').read_text()
schema = ''.join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', store, re.S).group(1)))
db = sqlite3.connect(':memory:')
db.execute(schema)
for statement in re.findall(r'db.execSQL\("([^"]+)"\);', local.split('static void ensure', 1)[1].split('private static void ensureColumn', 1)[0]):
    db.execute(statement)
    if statement.startswith('CREATE TABLE IF NOT EXISTS media_local_catalog'):
        db.execute("ALTER TABLE media_local_catalog ADD COLUMN catalog_key TEXT NOT NULL DEFAULT ''")

for title in range(85):
    # Repeated sort names test the title-key tie-breaker.
    key_value, name = f'key:{title:04}', f'title:{title // 3:04}'
    db.execute('INSERT INTO media_catalog_titles VALUES(?,?,1)', (key_value, name))
    for owner in (11, 22):
        for version in range(4):
            mid = title * 10 + version + 1
            db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,source_type,media_type) VALUES(?,7,?,?,x'00','show.mkv','show',1,2,2)", (owner, mid, mid))
            db.execute('INSERT INTO media_local_catalog(owner,dialog,mid,document,title_key,title,series,season,episode,uncertain,catalog_key) VALUES(?,7,?,?,?,?,1,1,?,0,?)',
                       (owner, mid, mid, key_value, name, version + 1, key_value))

scope = '((c.owner=11 AND c.dialog IN (7)) OR (c.owner=22 AND c.dialog IN (7))) AND m.source_type=2'
exists = 'EXISTS (SELECT 1 FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.catalog_key=t.title_key AND ' + scope + ')'
before, after = re.findall(r'"( AND t.title[^"\n]+)"', key)

def page(boundary=None, backwards=False):
    args = []
    where = 't.series=1 AND ' + exists
    if boundary:
        where += before if backwards else after
        args = [boundary[0], boundary[0], boundary[1]]
    order = 'DESC,t.title_key DESC' if backwards else 'ASC,t.title_key ASC'
    return db.execute('SELECT t.title,t.title_key FROM media_catalog_titles t WHERE ' + where + ' ORDER BY t.title ' + order + ' LIMIT 41', args).fetchall()

seen, boundary = [], None
while True:
    rows = page(boundary)
    consumed = rows[:40]
    seen += consumed
    if len(rows) <= 40:
        break
    boundary = consumed[-1]
assert len(seen) == len(set(seen)) == 85
assert list(reversed(page(seen[40], True)[:40])) == seen[:40]
assert list(reversed(page(seen[80], True)[:40])) == seen[40:80]
assert page(seen[-1]) == []
assert 'media_catalog_order' in str(db.execute('EXPLAIN QUERY PLAN SELECT t.title,t.title_key FROM media_catalog_titles t WHERE t.series=1 AND ' + exists + ' ORDER BY t.title,t.title_key LIMIT 41').fetchall())
assert 'media_catalog_sources' in str(db.execute('EXPLAIN QUERY PLAN SELECT 1 FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.catalog_key=? AND c.owner=11 LIMIT 1', ('key:0001',)).fetchall())
scope = '(c.owner=11 AND c.dialog IN (0))'
assert db.execute('SELECT COUNT(*) FROM media_catalog_titles t WHERE EXISTS(SELECT 1 FROM media_local_catalog c WHERE c.catalog_key=t.title_key AND ' + scope + ')').fetchone()[0] == 0
db.execute('DELETE FROM media WHERE owner=11')
assert db.execute('SELECT COUNT(*) FROM media_catalog_titles').fetchone()[0] == 85
db.execute('DELETE FROM media WHERE owner=22')
assert db.execute('SELECT COUNT(*) FROM media_catalog_titles').fetchone()[0] == 0
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
assert 'LIMIT 41' in store and 'loadCatalog(' in store
print('Global title pages: unique cross-owner keys, ties, forward/backward, scope, index plan and logout cleanup passed')
