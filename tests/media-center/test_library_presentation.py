"""Production SQL and UI wiring checks; not a device playback/gesture test."""
from pathlib import Path
import re
import sqlite3
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
java = root / 'TMessagesProj/src/main/java/org/telegram'
store = (java / 'messenger/tj/TjMediaStore.java').read_text()
center = (java / 'ui/TjMediaCenterActivity.java').read_text()
lists = (java / 'ui/TjMediaCollectionsActivity.java').read_text()
block = store.split('public void collectionSummaries(', 1)[1].split('public void collections(', 1)[0]
query = ''.join(re.findall(r'"([^"]*)"', block.split('rawQuery(', 1)[1].split('new String[]', 1)[0]))
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE media(owner INTEGER, collection TEXT, filename TEXT, caption TEXT, date INTEGER, dialog INTEGER, mid INTEGER);
CREATE TABLE media_collections(owner INTEGER, name TEXT, PRIMARY KEY(owner,name));
CREATE INDEX media_collection_page ON media(owner,collection,date DESC,dialog,mid) WHERE collection<>'';
INSERT INTO media_collections VALUES(1,'Empty'),(1,'Movies'),(2,'Movies');
INSERT INTO media VALUES(1,'Movies','old.mkv','',1,5,1),(1,'Movies','','Latest caption',3,5,2),
 (1,'Loose','file.pdf','',2,8,3),(2,'Movies','Other account','',4,5,2),(1,'','Unlisted','',6,5,4);
''')
assert db.execute(query, (1, 1, 1, 1)).fetchall() == [
    ('Empty', 0, None), ('Loose', 1, 'file.pdf'), ('Movies', 2, 'Latest caption')]
assert db.execute(query, (2, 2, 2, 2)).fetchall() == [('Movies', 1, 'Other account')]
plan = '\n'.join(row[3] for row in db.execute('EXPLAIN QUERY PLAN ' + query, (1, 1, 1, 1)))
assert 'media_collection_page' in plan and 'SEARCH m USING' in plan, plan
assert 'queue.postRunnable' in block and 'ownerActive(account, owner)' in block
assert 'new RecyclerListView' in lists and 'new ScrollView' not in lists
assert 'add.setOnClickListener(v -> create())' in lists and 'Gravity.BOTTOM' in lists
assert 'setNeedPlayMessageListener' in center and 'new org.telegram.ui.Components.FragmentContextView' in center
assert 'cell.initStreamingIcons()' in center and 'controller.setPlaylist(queue, message, 0, false, null)' in center
assert 'entry.account == message.currentAccount' in center and 'entry.message.getDialogId() == message.getDialogId()' in center
assert 'mediaType + "|" + entry.key' in center
assert 'TjMediaKind.matches(typeSelection(), org.telegram.messenger.tj.TjMediaKind.of(m))' in center
assert 'TjMediaKind.matches(mediaType, TjMediaKind.of(record.message))' in store
for name in ['SharedAudioCell', 'SharedDocumentCell']:
    cell = (java / f'ui/Cells/{name}.java').read_text()
    explicit = cell.split('Theme.ResourcesProvider resourcesProvider, int account)', 1)[1]
    assert explicit.index('currentAccount = account') < explicit.index('generateObserverTag()')
for lang, expected in [('values', 'Videos'), ('values-he', 'סרטונים')]:
    tree = ET.parse(root / f'TMessagesProj/src/main/res/{lang}/strings.xml')
    strings = {e.get('name'): e.text for e in tree.getroot()}
    assert strings['TjMediaVideos'] == expected
    for key in ['TjMediaListItemCount', 'TjMediaListEmpty', 'TjMediaListsEmpty']:
        assert strings[key]
grid = (java / 'ui/Cells/TjMediaGridCell.java').read_text()
assert 'hasMediaSpoilers()' in grid and 'cancelLoadImage()' in grid
assert 'loadFile(' not in grid
print('Library presentation: summaries, owner isolation, indexed lookup, player wiring, types and resources passed')
