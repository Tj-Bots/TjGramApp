"""UI source contracts and production SQL fixtures, not a device gesture test."""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
java = root / 'TMessagesProj/src/main/java/org/telegram'
center = (java / 'ui/TjMediaCenterActivity.java').read_text()
details = (java / 'ui/TjMediaDetailsActivity.java').read_text()
store = (java / 'messenger/tj/TjMediaStore.java').read_text()
episodes = (java / 'ui/Components/TjMediaEpisodesView.java').read_text()
lists = (java / 'ui/TjMediaCollectionsActivity.java').read_text()
button = details.split('private TextView button(', 1)[1].split('private TextView paragraph', 1)[0]
assert button.index('setTextIsSelectable(false)') < button.index('setOnClickListener')
assert 'setFocusable(true)' in button
assert 'new org.telegram.ui.Components.FragmentSearchField' in center
assert 'new org.telegram.ui.Components.FilterTabsView' in center
assert 'setIsSearchField(true)' not in center
assert 'DiffUtil.calculateDiff' in center and 'adapter.notifyDataSetChanged()' not in center
assert 'gridTouching' in center and 'getVisibleDialog().isShowing()' in center
assert 'entry.storeRevision' in center and 'showIndexedDetails(entry)' in center
playback = (java / 'ui/Components/TjMediaPlayback.java').read_text()
viewer = (java / 'ui/PhotoViewer.java').read_text()
assert 'openOrdinaryMedia(entry)' in center and 'TjMediaPlayback.open(this, entry' in center
assert 'TjMediaPlayback.open(this, source, position)' in details
assert 'openTjMedia(host, entry.message, positionMs)' in playback
assert 'setParentActivity(null, host, host.getResourceProvider(), message.currentAccount)' in viewer
media_entry = viewer.split('public boolean openTjMedia(', 1)[1].split('public boolean openPhoto(', 1)[0]
assert 'message.messageOwner.ttl_period != 0' in media_entry
assert 'message.messageOwner.media.ttl_seconds != 0' in media_entry
assert 'DialogObject.isEncryptedDialog(message.getDialogId())' in media_entry
assert 'entry.account != UserConfig.selectedAccount' not in center
assert 'new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT' in lists
assert 'swipeMediaType(dx < 0 ? 1 : -1)' in center
assert '.bindVersion(message,' in episodes
assert 'signature.toString().equals(pageSignature)' in episodes
assert 'episodes(pageAfter, true)' in episodes
assert 'background && !refreshAllowed.getAsBoolean()' in episodes
assert 'if (pageToken != pageRequest) return;' in episodes
assert 'removeListener(storeChanged)' in details
assert 'LayoutHelper.createLinear(-1, heroHeight())' in details
assert 'key.equals(artworkKey)' in details
assert 'artworkKey = null;' in details
assert 'body.addView(nextEpisode, LayoutHelper.createLinear(-1, -2, 16, 0, 16, 8))' in details
assert 'cancelRunOnUIThread(refreshTask)' in details
assert 'sourceToken != sourceRequest' in episodes.split('list.setOnItemClickListener', 1)[1]
assert '!multiTouch && action == android.view.MotionEvent.ACTION_UP' in center
assert 'actionBar.setSubtitle(accountSummary' in center
assert 'body.addView(description)' not in center  # No duplicated ordinary-media caption dialog.
assert 'values.isEmpty()' in episodes.split('private void seasons(', 1)[1].split('private void episodes', 1)[0]
assert 'account != title.message.currentAccount' in store.split('public void loadCatalogSources', 1)[1]
assert 'where += " AND c.dialog=? AND c.mid=?"' in store
assert 'editCollection(account, old, name' in lists and 'active(account)' in lists
for file in ['ui/Cells/TjMediaRowCell.java', 'ui/Cells/TjMediaCardCell.java', 'ui/TjMediaDetailsActivity.java']:
    source = (java / file).read_text()
    assert 'hasMediaSpoilers()' in source
    for line in source.splitlines():
        if 'getClosestPhotoSizeWithSize(' in line:
            assert 'null, true)' in line, (file, line)

schema = ''.join(re.findall(r'"([^"]*)"', re.search(r'db.execSQL\(("CREATE TABLE media .*?)\);', store, re.S).group(1)))
db = sqlite3.connect(':memory:')
db.execute(schema)
db.execute(re.search(r'db.execSQL\("(CREATE TABLE IF NOT EXISTS media_collections[^"\n]+)"', store).group(1))
preserve = re.search(r'db.execSQL\("(INSERT OR IGNORE INTO media_collections[^"\n]+)"', store).group(1)
directory = re.search(r'"(SELECT name FROM media_collections[^"\n]+)"', store).group(1)
for owner in (11, 22):
    db.execute("INSERT INTO media(owner,dialog,mid,document,data,filename,caption,date,collection,favorite) VALUES(?,7,1,9,x'00','show.mkv','',1,'Shared name',1)", (owner,))
db.execute(preserve, ('11', '7', '1'))
db.execute("UPDATE media SET collection='' WHERE owner=11")
assert db.execute(directory, ('11', '11')).fetchall() == [('Shared name',)]
db.execute("UPDATE media_collections SET name='Renamed' WHERE owner=11")
assert db.execute(directory, ('22', '22')).fetchall() == [('Shared name',)]
db.execute("DELETE FROM media_collections WHERE owner=11")
assert db.execute('SELECT owner,favorite FROM media ORDER BY owner').fetchall() == [(11, 1), (22, 1)]
db.commit()
db.execute('BEGIN')
db.execute("INSERT INTO media_collections VALUES(11,'Rollback')")
db.rollback()
assert db.execute(directory, ('11', '11')).fetchall() == []
assert db.execute('PRAGMA integrity_check').fetchone()[0] == 'ok'
print('Media UI regression contracts and empty-list/last-member/owner/rollback SQL checks passed')
