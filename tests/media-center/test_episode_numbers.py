"""Exercise production catalog-number SQL fragments, not serialized Android messages."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
source = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaStore.java').read_text()
local = (root / 'TMessagesProj/src/main/java/org/telegram/messenger/tj/TjMediaLocalIndex.java').read_text()
method = source.split('boolean numberedOnly, Callback<ArrayList<CatalogNumber>> callback)', 1)[1].split('public void loadCatalogSources', 1)[0]
season = re.search(r'String seasonSql = "([^"]+)"', method)[1]
episode = re.search(r'String episodeSql = "([^"]+)"', method)[1]
aggregate, limit = re.search(r'db.rawQuery\("SELECT " \+ number \+ "([^"]+)"\s*\+ where \+ " GROUP BY " \+ number \+ " ORDER BY " \+ order \+ " LIMIT (\d+)"', method).groups()
schema = ''.join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', source, re.S)[1]))
db = sqlite3.connect(':memory:')
db.execute(schema)
db.execute(re.search(r'db.execSQL\("(CREATE TABLE IF NOT EXISTS media_local_catalog[^"]+)"', local)[1])
db.execute("ALTER TABLE media_local_catalog ADD COLUMN catalog_key TEXT NOT NULL DEFAULT ''")

def add(owner, dialog, mid, s, e, key='show', watched=0, position=0):
    db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,source_type,media_type,watched,position,duration) VALUES(?,?,?,1,x'00','show.mkv','',1,2,2,?,?,1000)", (owner, dialog, mid, watched, position))
    db.execute('INSERT INTO media_local_catalog(owner,dialog,mid,document,title_key,title,series,season,episode,uncertain,catalog_key) VALUES(?,?,?,1,?,?,1,?,?,0,?)', (owner, dialog, mid, key, key, s, e, key))

def numbers(owner=11, selected=2, after=-1, numbered=False, dialog=None, key='show'):
    number = season if selected is None else episode
    order = 'CASE WHEN (' + number + ')<0 THEN 2147483647 ELSE (' + number + ') END'
    where = 'c.owner=? AND c.catalog_key=?'
    args = [owner, key]
    if numbered:
        where += ' AND (' + season + ')>=0 AND (' + episode + ')>=0'
    if dialog is not None:
        where += ' AND m.dialog=?'
        args.append(dialog)
    if selected is not None:
        where += ' AND (' + season + ')=?'
        args.append(selected)
    where += ' AND (' + order + ')>?'
    args.append(after)
    return db.execute('SELECT ' + number + aggregate + where + ' GROUP BY ' + number + ' ORDER BY ' + order + ' LIMIT ' + limit, args).fetchall()

add(11, 7, 1, 2, 3, position=400)
add(11, 8, 2, 2, 3, watched=1)
add(11, 7, 3, 2, 7)
add(11, 7, 4, 2, -1)
add(11, 7, 5, 3, 1)
add(11, 7, 6, 4, -1)
add(22, 7, 1, 2, 99)
add(11, 7, 7, 2, 88, key='different')
assert numbers() == [(3, 2, 1, 1), (7, 1, 0, 0), (-1, 1, 0, 0)]
assert numbers(after=3)[0][0] == 7  # next available, not invented episode 4
assert numbers(after=7) == [(-1, 1, 0, 0)]
assert numbers(selected=None, after=2, numbered=True) == [(3, 1, 0, 0)]
assert numbers(dialog=8) == [(3, 1, 1, 0)]
assert numbers(owner=22) == [(99, 1, 0, 0)]
assert numbers(key='different') == [(88, 1, 0, 0)]
db.execute('UPDATE media SET season_override=3,episode_override=2 WHERE owner=11 AND dialog=8 AND mid=2')
assert numbers()[0] == (3, 1, 0, 1)
assert numbers(selected=3) == [(1, 1, 0, 0), (2, 1, 1, 0)]
db.execute('UPDATE media SET position=980 WHERE owner=11 AND dialog=7 AND mid=1')
assert numbers()[0] == (3, 1, 1, 0)
assert numbers(owner=99) == []
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
print('12 production episode-number SQL grouping, override, gap, state and scope checks passed')
