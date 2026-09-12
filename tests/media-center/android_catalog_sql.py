"""Emit isolated in-memory Android sqlite3 assertions; no app data or login needed."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[2]
java = root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj'
local = (java / 'TjMediaLocalIndex.java').read_text()
store = (java / 'TjMediaStore.java').read_text()
schema = ''.join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', store, re.S).group(1)))
print('.bail on')
print(schema + ';')
for statement in re.findall(r'db.execSQL\("([^"]+)"\);', local.split('static void ensure', 1)[1].split('private static void ensureColumn', 1)[0]):
    print(statement + ';')
    if statement.startswith('CREATE TABLE IF NOT EXISTS media_local_catalog'):
        print("ALTER TABLE media_local_catalog ADD COLUMN catalog_key TEXT NOT NULL DEFAULT '';")
print('BEGIN;')
for title in range(20):
    print(f"INSERT INTO media_catalog_titles VALUES('key:{title:03}','title:{title:03}',1);")
    for owner in (11, 22):
        for version in range(6):
            mid = title * 10 + version + 1
            print(f"INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,source_type,media_type) VALUES({owner},7,{mid},{mid},x'00','show.mkv','show',1,2,2);")
            print(f"INSERT INTO media_local_catalog(owner,dialog,mid,document,title_key,title,series,season,episode,uncertain,catalog_key) VALUES({owner},7,{mid},{mid},'key:{title:03}','title:{title:03}',1,1,{version+1},0,'key:{title:03}');")
print('COMMIT; CREATE TEMP TABLE assertion(ok INTEGER CHECK(ok=1));')
print('INSERT INTO assertion SELECT COUNT(*)=20 FROM media_catalog_titles;')
print("INSERT INTO assertion SELECT COUNT(*)=0 FROM media_catalog_titles t WHERE EXISTS(SELECT 1 FROM media_local_catalog c WHERE c.catalog_key=t.title_key AND c.owner=11 AND c.dialog=0);")
keys = (java / 'TjMediaCatalogPageKey.java').read_text()
before, after = re.findall(r'"( AND t.title[^"\n]+)"', keys)
for expression, expected in ((before, 9), (after, 10)):
    for value in ('title:009', 'title:009', 'key:009'):
        expression = expression.replace('?', "'" + value + "'", 1)
    print(f'INSERT INTO assertion SELECT COUNT(*)={expected} FROM media_catalog_titles t WHERE t.series=1{expression};')
print('BEGIN; DELETE FROM media WHERE owner=11; INSERT INTO assertion SELECT COUNT(*)=20 FROM media_catalog_titles; ROLLBACK;')
print('INSERT INTO assertion SELECT COUNT(*)=240 FROM media_local_catalog;')
print('DELETE FROM media WHERE owner=11; INSERT INTO assertion SELECT COUNT(*)=120 FROM media_local_catalog;')
print('DELETE FROM media WHERE owner=22; INSERT INTO assertion SELECT COUNT(*)=0 FROM media_catalog_titles;')
print("PRAGMA integrity_check; SELECT 'Android catalog SQL assertions passed';")
