package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/** Local metadata index. All database access is serialized off the UI thread. */
public final class TjMediaStore extends SQLiteOpenHelper implements NotificationCenter.NotificationCenterDelegate {
    private static final DispatchQueue queue = new DispatchQueue("tj-media-store");
    private static volatile TjMediaStore instance;
    private final long[] observedOwners = new long[UserConfig.MAX_ACCOUNT_COUNT];
    private volatile boolean searchReady;
    private boolean searchAvailable;
    private boolean localCatalogAvailable;
    private volatile boolean localCatalogReady;
    private long lastLocalCatalogNotification;
    private final Runnable localBackfill = this::backfillLocalCatalog;
    public boolean isLocalCatalogReady() { return localCatalogReady; }
    private final Runnable searchBackfill = this::backfillSearch;

    public boolean isSearchReady() { return searchReady; }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("CREATE TABLE IF NOT EXISTS media_collections (owner INTEGER NOT NULL, name TEXT NOT NULL, PRIMARY KEY(owner,name))");
        db.execSQL("CREATE TABLE IF NOT EXISTS media_collection_icons (owner INTEGER NOT NULL, name TEXT NOT NULL, icon TEXT NOT NULL, PRIMARY KEY(owner,name))");
        db.execSQL("CREATE INDEX IF NOT EXISTS media_collection_page ON media(owner,collection,date DESC,dialog,mid) WHERE collection<>''");
        try {
            TjMediaLocalIndex.ensure(db);
            localCatalogAvailable = true;
            queue.postRunnable(localBackfill);
        } catch (Exception e) { FileLog.e("Tj local catalog initialization failed", e); }
        try {
            TjMediaSearchIndex.ensure(db);
            searchAvailable = true;
            queue.postRunnable(searchBackfill);
        } catch (Exception e) {
            // An unavailable search index must not prevent ordinary library browsing.
            searchAvailable = false;
            FileLog.e("Tj media search initialization failed", e);
        }
    }

    private void backfillLocalCatalog() {
        if (!localCatalogAvailable) return;
        try {
            localCatalogReady = TjMediaLocalIndex.backfill(getWritableDatabase());
            if (!localCatalogReady) queue.postRunnable(localBackfill, 100);
            long now = android.os.SystemClock.elapsedRealtime();
            if (localCatalogReady || now - lastLocalCatalogNotification >= 2000) {
                lastLocalCatalogNotification = now;
                changed();
            }
        } catch (Exception e) { FileLog.e("Tj local catalog backfill failed", e); }
    }

    private void backfillSearch() {
        if (!searchAvailable) return;
        try {
            searchReady = TjMediaSearchIndex.backfill(getWritableDatabase());
            if (!searchReady) queue.postRunnable(searchBackfill, 50);
            else changed();
        } catch (Exception e) {
            searchReady = false;
            FileLog.e("Tj media search backfill failed", e);
        }
    }
    private final java.util.concurrent.atomic.AtomicLongArray revisions = new java.util.concurrent.atomic.AtomicLongArray(UserConfig.MAX_ACCOUNT_COUNT);
    public long revision(int account) { return revisions.get(account); }
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    public interface Callback<T> { void run(T value); }

    public static final class Record {
        public MessageObject message;
        public long position;
        public long duration;
        public long playedAt;
        public boolean watched;
        public boolean favorite;
        public String collection;
        public TjMediaMetadata.Title metadata;
        public int metadataOrigin; // 0 unknown/absent, 1 chosen manually, 2 matched automatically.
        public int sourceType;
        public String localKey = "", localTitle = "";
        public boolean localSeries, localUncertain, localManual;
        public int localYear;
        public String catalogKey() {
            return !localKey.isEmpty() ? localKey : metadata == null ? "" : TjMediaCatalog.key(metadata.id, metadata.series);
        }
        public boolean isSeries() { return localSeries || metadata != null && metadata.series; }
        public String title() { return metadata != null ? metadata.name : !localTitle.isEmpty() ? localTitle
                : TjMediaTitle.parse(message.getDocumentName(), message.messageOwner.message).title; }
        public int seasonOverride = -2, episodeOverride = -2;
        public int indexedSeason = -2, indexedEpisode = -2;
        public int season() { return seasonOverride != -2 ? seasonOverride : indexedSeason != -2 ? indexedSeason
                : TjMediaTitle.parse(message.getDocumentName(), message.messageOwner.message).season; }
        public int episode() { return episodeOverride != -2 ? episodeOverride : indexedEpisode != -2 ? indexedEpisode
                : TjMediaTitle.parse(message.getDocumentName(), message.messageOwner.message).episode; }
    }

    public static final class Page extends ArrayList<Record> {
        public TjMediaPageKey nextKey;
        public TjMediaPageKey previousKey;
        public boolean hasMore;
        public boolean hasPrevious;
    }

    public static final class CatalogPage extends ArrayList<Record> {
        public TjMediaCatalogPageKey nextKey, previousKey;
        public boolean hasMore, hasPrevious;
    }

    private TjMediaStore() {
        super(ApplicationLoader.applicationContext, "tj_media_library.db", null, 9);
        AndroidUtilities.runOnUIThread(() -> {
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                observedOwners[account] = UserConfig.getInstance(account).getClientUserId();
                NotificationCenter center = NotificationCenter.getInstance(account);
                center.addObserver(this, NotificationCenter.messagesDeleted);
                center.addObserver(this, NotificationCenter.replaceMessagesObjects);
                center.addObserver(this, NotificationCenter.appDidLogout);
                center.addObserver(this, NotificationCenter.historyCleared);
                center.addObserver(this, NotificationCenter.removeAllMessagesFromDialog);
            }
        });
    }

    public void addListener(Runnable listener) { listeners.addIfAbsent(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void changed() { AndroidUtilities.runOnUIThread(() -> { for (Runnable listener : listeners) listener.run(); }); }

    public void clear(int account, Callback<Boolean> callback) {
        revisions.incrementAndGet(account);
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                if (ownerActive(account, owner)) {
                    clearOwner(getWritableDatabase(), owner);
                    success = true;
                }
            } catch (Exception e) { FileLog.e("Tj library clear failed", e); }
            boolean result = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (id == NotificationCenter.appDidLogout) {
            revisions.incrementAndGet(account);
            long previous = observedOwners[account];
            observedOwners[account] = 0;
            if (previous != 0) queue.postRunnable(() -> {
                try { clearOwner(getWritableDatabase(), previous); }
                catch (Exception e) { FileLog.e("Tj media logout cleanup failed", e); }
                changed();
            });
            return;
        }
        if (owner == 0) return;
        observedOwners[account] = owner;
        if (id == NotificationCenter.historyCleared || id == NotificationCenter.removeAllMessagesFromDialog) {
            if (args.length < 2 || !(args[0] instanceof Number)) return;
            // A channel difference reload uses the same event but is not a deletion.
            if (id == NotificationCenter.removeAllMessagesFromDialog && !Boolean.FALSE.equals(args[1])) return;
            if (id == NotificationCenter.historyCleared && !(args[1] instanceof Number)) return;
            long dialog = ((Number) args[0]).longValue();
            int maxId = id == NotificationCenter.historyCleared ? ((Number) args[1]).intValue() : Integer.MAX_VALUE;
            revisions.incrementAndGet(account);
            queue.postRunnable(() -> {
                if (!ownerActive(account, owner)) return;
                try {
                    getWritableDatabase().delete("media", "owner=? AND dialog=? AND mid<=?",
                            new String[]{Long.toString(owner), Long.toString(dialog), Integer.toString(maxId)});
                    changed();
                } catch (Exception e) { FileLog.e("Tj media history cleanup failed", e); }
            });
            return;
        }
        if (id == NotificationCenter.replaceMessagesObjects && args.length > 1 && args[1] instanceof java.util.List) {
            revisions.incrementAndGet(account);
            for (Object object : (java.util.List<?>) args[1]) if (object instanceof MessageObject) index((MessageObject) object, true);
        } else if (id == NotificationCenter.messagesDeleted && args.length > 2 && !Boolean.TRUE.equals(args[2])) {
            revisions.incrementAndGet(account);
            long channel = ((Number) args[1]).longValue();
            ArrayList<Integer> ids = new ArrayList<>();
            for (Object value : (java.util.List<?>) args[0]) if (value instanceof Integer) ids.add((Integer) value);
            queue.postRunnable(() -> {
                try {
                    SQLiteDatabase db = getWritableDatabase();
                    db.beginTransaction();
                    try {
                        for (int mid : ids) {
                            try (Cursor cursor = db.query("media", new String[]{"dialog", "data"}, "owner=? AND mid=?",
                                    new String[]{Long.toString(owner), Integer.toString(mid)}, null, null, null)) {
                                ArrayList<Long> dialogs = new ArrayList<>();
                                while (cursor.moveToNext()) {
                                    TLRPC.Message raw = deserialize(cursor.getBlob(1));
                                    if (raw != null && raw.peer_id != null && raw.peer_id.channel_id == channel) dialogs.add(cursor.getLong(0));
                                }
                                for (long dialog : dialogs) db.delete("media", "owner=? AND dialog=? AND mid=?", identity(owner, dialog, mid));
                            }
                        }
                        db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                } catch (Exception e) { FileLog.e("Tj media deletion update failed", e); }
                changed();
            });
        }
    }

    public static TjMediaStore getInstance() {
        if (instance == null) synchronized (TjMediaStore.class) {
            if (instance == null) instance = new TjMediaStore();
        }
        return instance;
    }

    private static void clearOwner(SQLiteDatabase db, long owner) {
        String[] args = {Long.toString(owner)};
        db.beginTransaction();
        try {
            db.delete("media", "owner=?", args);
            db.delete("media_scan_progress", "owner=?", args);
            db.delete("media_collections", "owner=?", args);
            db.delete("media_collection_icons", "owner=?", args);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE media (owner INTEGER NOT NULL, dialog INTEGER NOT NULL, mid INTEGER NOT NULL, "
                + "document INTEGER NOT NULL, data BLOB NOT NULL, filename TEXT NOT NULL, caption TEXT NOT NULL, "
                + "date INTEGER NOT NULL, position INTEGER NOT NULL DEFAULT 0, duration INTEGER NOT NULL DEFAULT 0, "
                + "played INTEGER NOT NULL DEFAULT 0, watched INTEGER NOT NULL DEFAULT 0, "
                + "favorite INTEGER NOT NULL DEFAULT 0, collection TEXT NOT NULL DEFAULT '', "
                + "metadata TEXT NOT NULL DEFAULT '', series INTEGER NOT NULL DEFAULT 0, "
                + "search_text TEXT NOT NULL DEFAULT '', title_text TEXT NOT NULL DEFAULT '', "
                + "source_type INTEGER NOT NULL DEFAULT 0, media_type INTEGER NOT NULL DEFAULT 0, "
                + "metadata_id INTEGER NOT NULL DEFAULT 0, "
                + "metadata_origin INTEGER NOT NULL DEFAULT 0, "
                + "season_override INTEGER NOT NULL DEFAULT -2, episode_override INTEGER NOT NULL DEFAULT -2, "
                + "PRIMARY KEY(owner,dialog,mid))");
        db.execSQL("CREATE INDEX media_recent ON media(owner,date DESC)");
        db.execSQL("CREATE INDEX media_played ON media(owner,played DESC)");
        db.execSQL("CREATE INDEX media_title ON media(owner,metadata_id,series)");
        db.execSQL("CREATE INDEX media_recent_page ON media(owner,date DESC,dialog,mid)");
        db.execSQL("CREATE INDEX media_played_page ON media(owner,played DESC,dialog,mid)");
        db.execSQL("CREATE TABLE media_scan_progress (owner INTEGER NOT NULL, scope TEXT NOT NULL, lease TEXT NOT NULL, sequence INTEGER NOT NULL DEFAULT 0, position BLOB NOT NULL, PRIMARY KEY(owner,scope))");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 9) {
            db.execSQL("CREATE TABLE media_scan_progress (owner INTEGER NOT NULL, scope TEXT NOT NULL, lease TEXT NOT NULL, sequence INTEGER NOT NULL DEFAULT 0, position BLOB NOT NULL, PRIMARY KEY(owner,scope))");
        }
        if (oldVersion < 8) {
            db.execSQL("CREATE INDEX media_recent_page ON media(owner,date DESC,dialog,mid)");
            db.execSQL("CREATE INDEX media_played_page ON media(owner,played DESC,dialog,mid)");
        }
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE media ADD COLUMN metadata_origin INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 6) {
            db.execSQL("ALTER TABLE media ADD COLUMN season_override INTEGER NOT NULL DEFAULT -2");
            db.execSQL("ALTER TABLE media ADD COLUMN episode_override INTEGER NOT NULL DEFAULT -2");
        }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE media ADD COLUMN metadata TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE media ADD COLUMN series INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE media ADD COLUMN search_text TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE media ADD COLUMN title_text TEXT NOT NULL DEFAULT ''");
            try (Cursor cursor = db.query("media", new String[]{"owner", "dialog", "mid", "filename", "caption", "metadata", "series"}, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("search_text", TjMediaTitle.normalizeSearch(cursor.getString(3) + " " + cursor.getString(4)));
                    TjMediaMetadata.Title title = metadata(cursor.getString(5), cursor.getInt(6) != 0);
                    values.put("title_text", title == null ? "" : TjMediaTitle.normalizeSearch(title.name));
                    db.update("media", values, "owner=? AND dialog=? AND mid=?", identity(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2)));
                }
            }
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE media ADD COLUMN source_type INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE media ADD COLUMN media_type INTEGER NOT NULL DEFAULT 0");
            try (Cursor cursor = db.query("media", new String[]{"owner", "dialog", "mid", "data"}, null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    int account = -1;
                    for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) if (UserConfig.getInstance(i).getClientUserId() == cursor.getLong(0)) account = i;
                    TLRPC.Message raw = deserialize(cursor.getBlob(3));
                    if (account < 0 || raw == null) continue;
                    MessageObject message = new MessageObject(account, raw, false, false);
                    ContentValues values = new ContentValues();
                    values.put("source_type", sourceType(message));
                    values.put("media_type", mediaType(message));
                    db.update("media", values, "owner=? AND dialog=? AND mid=?", identity(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2)));
                }
            }
        }
        if (oldVersion < 5) {
            db.execSQL("ALTER TABLE media ADD COLUMN metadata_id INTEGER NOT NULL DEFAULT 0");
            db.execSQL("CREATE INDEX media_title ON media(owner,metadata_id,series)");
            try (Cursor cursor = db.query("media", new String[]{"owner", "dialog", "mid", "metadata", "series"}, "metadata<>''", null, null, null, null)) {
                while (cursor.moveToNext()) {
                    TjMediaMetadata.Title title = metadata(cursor.getString(3), cursor.getInt(4) != 0);
                    if (title == null) continue;
                    ContentValues values = new ContentValues();
                    values.put("metadata_id", title.id);
                    db.update("media", values, "owner=? AND dialog=? AND mid=?", identity(cursor.getLong(0), cursor.getLong(1), cursor.getInt(2)));
                }
            }
        }
    }

    private static int sourceType(MessageObject message) {
        if (message.getDialogId() > 0) return 1;
        TLRPC.Chat chat = MessagesController.getInstance(message.currentAccount).getChat(-message.getDialogId());
        if (chat == null) return message.messageOwner.peer_id.channel_id == 0 ? 2 : 0;
        return ChatObject.isChannel(chat) && !chat.megagroup ? 3 : 2;
    }

    private static int mediaType(MessageObject message) {
        return TjMediaKind.of(message);
    }

    public static boolean eligible(MessageObject message) {
        if (message == null || message.messageOwner == null || message.getId() <= 0
                || DialogObject.isEncryptedDialog(message.getDialogId())) return false;
        TLRPC.MessageMedia media = MessageObject.getMedia(message.messageOwner);
        return media != null && media.ttl_seconds == 0 && message.messageOwner.ttl_period == 0
                && (media instanceof TLRPC.TL_messageMediaDocument || media instanceof TLRPC.TL_messageMediaPhoto);
    }

    public static String displayCaption(MessageObject message) {
        String text = message.messageOwner.message;
        if (text == null) return "";
        StringBuilder display = new StringBuilder(text);
        if (message.messageOwner.entities != null) for (TLRPC.MessageEntity entity : message.messageOwner.entities) {
            if (!(entity instanceof TLRPC.TL_messageEntitySpoiler)) continue;
            int start = Math.max(0, Math.min(display.length(), entity.offset));
            int end = (int) Math.min(display.length(), Math.max(start, (long) entity.offset + entity.length));
            for (int i = start; i < end; i++) display.setCharAt(i, '\u2022');
        }
        return display.toString();
    }

    private static boolean ownerActive(int account, long owner) {
        UserConfig config = UserConfig.getInstance(account);
        return owner != 0 && config.isClientActivated() && config.getClientUserId() == owner;
    }

    /** A manual edit must never reinsert a stale UI snapshot or replace newer media. */
    private static final class Snapshot {
        final int account, editDate;
        final long owner, document, photo;
        final String[] args;

        Snapshot(MessageObject message, long expectedOwner) {
            account = message.currentAccount;
            owner = expectedOwner;
            args = identity(owner, message.getDialogId(), message.getId());
            document = message.getDocument() == null ? 0 : message.getDocument().id;
            editDate = message.messageOwner.edit_date;
            photo = photoId(message.messageOwner);
        }

        boolean current(SQLiteDatabase db) {
            if (!ownerActive(account, owner)) return false;
            try (Cursor cursor = db.query("media", new String[]{"document", "data"},
                    "owner=? AND dialog=? AND mid=?", args, null, null, null)) {
                if (!cursor.moveToFirst() || cursor.getLong(0) != document) return false;
                TLRPC.Message stored = deserialize(cursor.getBlob(1));
                return stored != null && stored.edit_date == editDate && photoId(stored) == photo;
            }
        }

        private static long photoId(TLRPC.Message message) {
            return message.media == null || message.media.photo == null ? 0 : message.media.photo.id;
        }
    }

    /** Snapshot serialization is bounded by one message and precedes worker ownership. */
    public void index(MessageObject message) {
        index(message, false);
    }

    private void index(MessageObject message, boolean existingOnly) {
        index(message, existingOnly, null);
    }

    public void index(MessageObject message, Callback<Boolean> callback) {
        index(message, false, callback);
    }

    private void index(MessageObject message, boolean existingOnly, Callback<Boolean> callback) {
        index(message, existingOnly, -1, callback);
    }

    public void index(MessageObject message, long expectedRevision, Callback<Boolean> callback) {
        index(message, false, expectedRevision, callback);
    }

    /** A network page is one transaction; snapshots are captured before worker ownership. */
    public void indexBatch(java.util.List<MessageObject> messages, long expectedRevision, Callback<Boolean> callback) {
        if (messages == null || messages.isEmpty() || messages.size() > 200) {
            AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        ArrayList<PreparedIndex> snapshots = new ArrayList<>(messages.size());
        for (MessageObject message : messages) {
            PreparedIndex snapshot = prepareIndex(message);
            if (snapshot == null || !snapshots.isEmpty()
                    && (snapshot.account != snapshots.get(0).account || snapshot.owner != snapshots.get(0).owner)) {
                AndroidUtilities.runOnUIThread(() -> callback.run(false));
                return;
            }
            snapshots.add(snapshot);
        }
        PreparedIndex first = snapshots.get(0);
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                if (!ownerActive(first.account, first.owner) || revision(first.account) != expectedRevision) return;
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    for (PreparedIndex snapshot : snapshots) writeIndex(db, snapshot.values, false);
                    // A clear/logout may arrive while the worker writes. Roll back this page
                    // if its identity/revision is already obsolete before commit.
                    if (!ownerActive(first.account, first.owner) || revision(first.account) != expectedRevision) return;
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                success = true;
            } catch (Exception e) { FileLog.e("Tj media page index failed", e); }
            finally {
                boolean result = success;
                AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(first.account, first.owner)
                        && revision(first.account) == expectedRevision));
            }
        });
    }

    /** A worker lease prevents an obsolete scan from advancing a newer scan's cursor. */
    public static final class ScanLease {
        public final int account;
        public final long owner, sequence;
        public final String scope;
        private final String lease;
        private final byte[] position;

        private ScanLease(int account, long owner, String scope, String lease, long sequence, byte[] position) {
            this.account = account;
            this.owner = owner;
            this.scope = scope;
            this.lease = lease;
            this.sequence = sequence;
            this.position = position.clone();
        }

        public byte[] position() { return position.clone(); }
        private String[] expected() {
            return new String[]{Long.toString(owner), scope, lease, Long.toString(sequence)};
        }
    }

    /** Resume a scope, superseding its previous worker without discarding committed progress. */
    public void acquireScan(int account, String scope, Callback<ScanLease> callback) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || scope == null
                || scope.isEmpty() || scope.length() > 512) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }
        long owner = UserConfig.getInstance(account).getClientUserId();
        long expectedRevision = revision(account);
        if (ownerActive(account, owner)) observedOwners[account] = owner;
        queue.postRunnable(() -> {
            ScanLease result = null;
            try {
                if (!ownerActive(account, owner) || revision(account) != expectedRevision) return;
                SQLiteDatabase db = getWritableDatabase();
                String lease = java.util.UUID.randomUUID().toString();
                byte[] position = new byte[0];
                long sequence = 0;
                db.beginTransaction();
                try {
                    String[] args = {Long.toString(owner), scope};
                    try (Cursor cursor = db.query("media_scan_progress", new String[]{"sequence", "position"},
                            "owner=? AND scope=?", args, null, null, null)) {
                        if (cursor.moveToFirst()) {
                            sequence = cursor.getLong(0);
                            position = cursor.getBlob(1);
                        }
                    }
                    ContentValues values = new ContentValues();
                    values.put("owner", owner);
                    values.put("scope", scope);
                    values.put("lease", lease);
                    values.put("sequence", sequence);
                    values.put("position", position);
                    if (db.update("media_scan_progress", values, "owner=? AND scope=?", args) == 0)
                        db.insertOrThrow("media_scan_progress", null, values);
                    if (!ownerActive(account, owner) || revision(account) != expectedRevision) return;
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                result = new ScanLease(account, owner, scope, lease, sequence, position);
            } catch (Exception e) { FileLog.e("Tj media scan resume failed", e); }
            finally {
                ScanLease acquired = result;
                AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner)
                        && revision(account) == expectedRevision ? acquired : null));
            }
        });
    }

    /** Media and continuation are committed together, including pages with no eligible media. */
    public void commitScanPage(ScanLease scan, byte[] position, java.util.List<MessageObject> messages,
                               long expectedRevision, Callback<ScanLease> callback) {
        if (scan == null || position == null || position.length > 16384 || messages == null
                || messages.size() > 200 || scan.sequence == Long.MAX_VALUE) {
            AndroidUtilities.runOnUIThread(() -> callback.run(null));
            return;
        }
        byte[] nextPosition = position.clone();
        ArrayList<PreparedIndex> snapshots = new ArrayList<>(messages.size());
        for (MessageObject message : messages) {
            PreparedIndex snapshot = prepareIndex(message);
            if (snapshot == null || snapshot.account != scan.account || snapshot.owner != scan.owner) {
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }
            snapshots.add(snapshot);
        }
        queue.postRunnable(() -> {
            ScanLease result = null;
            try {
                if (!ownerActive(scan.account, scan.owner) || revision(scan.account) != expectedRevision) return;
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    ContentValues values = new ContentValues();
                    values.put("position", nextPosition);
                    values.put("sequence", scan.sequence + 1);
                    // Conditional update is rolled back with the media if any write fails.
                    if (db.update("media_scan_progress", values,
                            "owner=? AND scope=? AND lease=? AND sequence=?", scan.expected()) != 1) return;
                    for (PreparedIndex snapshot : snapshots) writeIndex(db, snapshot.values, false);
                    if (!ownerActive(scan.account, scan.owner) || revision(scan.account) != expectedRevision) return;
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                result = new ScanLease(scan.account, scan.owner, scan.scope, scan.lease,
                        scan.sequence + 1, nextPosition);
            } catch (Exception e) { FileLog.e("Tj media scan page commit failed", e); }
            finally {
                ScanLease committed = result;
                AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(scan.account, scan.owner)
                        && revision(scan.account) == expectedRevision ? committed : null));
            }
        });
    }

    private static final class PreparedIndex {
        final int account;
        final long owner;
        final ContentValues values;

        PreparedIndex(int account, long owner, ContentValues values) {
            this.account = account;
            this.owner = owner;
            this.values = values;
        }
    }

    private PreparedIndex prepareIndex(MessageObject message) {
        if (!eligible(message)) return null;
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        byte[] data = serialize(message.messageOwner);
        if (data == null || !ownerActive(account, owner)) return null;
        observedOwners[account] = owner;
        ContentValues values = new ContentValues();
        values.put("owner", owner);
        values.put("dialog", message.getDialogId());
        values.put("mid", message.getId());
        values.put("document", message.getDocument() == null ? 0 : message.getDocument().id);
        values.put("data", data);
        values.put("filename", message.getDocumentName() == null ? "" : message.getDocumentName());
        values.put("caption", message.messageOwner.message == null ? "" : message.messageOwner.message);
        values.put("date", message.messageOwner.date);
        values.put("source_type", sourceType(message));
        values.put("media_type", mediaType(message));
        values.put("search_text", TjMediaTitle.normalizeSearch(values.getAsString("filename") + " " + values.getAsString("caption")));
        return new PreparedIndex(account, owner, values);
    }

    /** All callers use the store queue; the page caller also owns a transaction. */
    private boolean writeIndex(SQLiteDatabase db, ContentValues values, boolean existingOnly) {
        String[] args = identity(values.getAsLong("owner"), values.getAsLong("dialog"), values.getAsInteger("mid"));
        try (Cursor cursor = db.query("media", new String[]{"document"}, "owner=? AND dialog=? AND mid=?", args, null, null, null)) {
            boolean exists = cursor.moveToFirst();
            if (existingOnly && !exists) return false;
            if (exists && cursor.getLong(0) != values.getAsLong("document")) {
                values.put("position", 0);
                values.put("duration", 0);
                values.put("played", 0);
                values.put("watched", 0);
                values.put("metadata", "");
                values.put("metadata_id", 0);
                values.put("metadata_origin", 0);
                values.put("season_override", -2);
                values.put("episode_override", -2);
                values.put("title_text", "");
                values.put("series", 0);
            }
        }
        if (db.update("media", values, "owner=? AND dialog=? AND mid=?", args) == 0)
            db.insertOrThrow("media", null, values);
        if (localCatalogAvailable) TjMediaLocalIndex.index(db, values);
        return true;
    }

    private void index(MessageObject message, boolean existingOnly, long expectedRevision, Callback<Boolean> callback) {
        if (message != null && !eligible(message) && existingOnly) {
            long owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
            String[] args = identity(owner, message.getDialogId(), message.getId());
            queue.postRunnable(() -> {
                try { getWritableDatabase().delete("media", "owner=? AND dialog=? AND mid=?", args); }
                catch (Exception e) { FileLog.e("Tj media expiry update failed", e); }
                changed();
            });
            return;
        }
        PreparedIndex snapshot = prepareIndex(message);
        if (snapshot == null) {
            if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        int account = snapshot.account;
        long owner = snapshot.owner;
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                if (!ownerActive(account, owner) || expectedRevision >= 0 && revision(account) != expectedRevision) return;
                SQLiteDatabase db = getWritableDatabase();
                success = writeIndex(db, snapshot.values, existingOnly);
                if (existingOnly && success) changed();
            } catch (Exception e) { FileLog.e("Tj media index failed", e); }
            finally {
                boolean result = success;
                if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner)));
            }
        });
    }

    /** Only updates indexed media; never creates history for unrelated playback. */
    public void progress(MessageObject message, long position, long duration) {
        if (!eligible(message) || duration <= 0 || position < 0 || message.getDocument() == null) return;
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        String[] args = new String[]{Long.toString(owner), Long.toString(message.getDialogId()),
                Integer.toString(message.getId()), Long.toString(message.getDocument().id)};
        ContentValues values = new ContentValues();
        values.put("position", Math.min(position, duration));
        values.put("duration", duration);
        values.put("played", System.currentTimeMillis());
        queue.postRunnable(() -> {
            if (!ownerActive(account, owner)) return;
            try {
                getWritableDatabase().update("media", values, "owner=? AND dialog=? AND mid=? AND document=?", args);
            } catch (Exception e) { FileLog.e("Tj media progress failed", e); }
        });
    }

    public void setFlags(MessageObject message, boolean favorite, boolean watched, String collection, Callback<Boolean> callback) {
        if (!eligible(message)) { callback.run(false); return; }
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        Snapshot snapshot = new Snapshot(message, owner);
        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);
        values.put("watched", watched ? 1 : 0);
        values.put("collection", collection == null ? "" : collection.trim());
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                SQLiteDatabase db = getWritableDatabase();
                if (snapshot.current(db)) {
                    db.beginTransaction();
                    try {
                        db.execSQL("INSERT OR IGNORE INTO media_collections(owner,name) SELECT owner,collection FROM media WHERE owner=? AND dialog=? AND mid=? AND collection<>''", snapshot.args);
                        success = db.update("media", values, "owner=? AND dialog=? AND mid=?", snapshot.args) == 1;
                        db.execSQL("INSERT OR IGNORE INTO media_collections(owner,name) SELECT owner,collection FROM media WHERE owner=? AND dialog=? AND mid=? AND collection<>''", snapshot.args);
                        db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                }
            } catch (Exception e) { success = false; FileLog.e("Tj media list update failed", e); }
            boolean result = success;
            if (success) changed();
            AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner)));
        });
    }

    public void state(MessageObject message, Callback<Record> callback) {
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        String[] args = identity(owner, message.getDialogId(), message.getId());
        Snapshot snapshot = new Snapshot(message, owner);
        queue.postRunnable(() -> {
            Record record = new Record();
            record.message = message;
            record.collection = "";
            try {
                if (!snapshot.current(getReadableDatabase())) {
                    AndroidUtilities.runOnUIThread(() -> callback.run(null));
                    return;
                }
            } catch (Exception e) {
                FileLog.e("Tj media snapshot read failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }
            try (Cursor cursor = getReadableDatabase().query("media", new String[]{"position", "duration", "played", "watched", "favorite", "collection", "metadata", "series", "season_override", "episode_override", "metadata_origin", "source_type"},
                    "owner=? AND dialog=? AND mid=?", args, null, null, null)) {
                if (cursor.moveToFirst()) {
                    record.position = cursor.getLong(0);
                    record.duration = cursor.getLong(1);
                    record.playedAt = cursor.getLong(2);
                    record.watched = cursor.getInt(3) != 0;
                    record.favorite = cursor.getInt(4) != 0;
                    record.collection = cursor.getString(5);
                    record.metadata = metadata(cursor.getString(6), cursor.getInt(7) != 0);
                    record.seasonOverride = cursor.getInt(8);
                    record.episodeOverride = cursor.getInt(9);
                    record.metadataOrigin = cursor.getInt(10);
                    record.sourceType = cursor.getInt(11);
                }
            } catch (Exception e) {
                FileLog.e("Tj media state failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }
            try { if (localCatalogAvailable) readLocalRecords(getReadableDatabase(), java.util.Collections.singletonList(record), owner); }
            catch (Exception e) {
                FileLog.e("Tj local catalog state failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null)); return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner) ? record : null));
        });
    }

    public void setMetadata(MessageObject message, TjMediaMetadata.Title title, Callback<Boolean> callback) {
        saveMetadata(message, title, false, result -> callback.run(result == 1));
    }

    public void setLocalTitle(MessageObject message, String name, boolean series, int year, Callback<Boolean> callback) {
        String title = name == null ? "" : name.trim();
        if (!eligible(message) || title.isEmpty() || title.length() > 250 || year != 0 && (year < 1800 || year > 2200)) { callback.run(false); return; }
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        Snapshot snapshot = new Snapshot(message, owner);
        PreparedIndex localSnapshot = prepareIndex(message);
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                SQLiteDatabase db = getWritableDatabase();
                if (localCatalogAvailable && snapshot.current(db)) {
                    db.beginTransaction();
                    try {
                        if (localSnapshot != null) TjMediaLocalIndex.index(db, localSnapshot.values);
                        ContentValues values = new ContentValues();
                        values.put("title_key", TjMediaLocalIdentity.key(title, series, year));
                        values.put("title", title); values.put("series", series ? 1 : 0);
                        values.put("year", year);
                        values.put("manual", 1); values.put("uncertain", 0);
                        success = db.update("media_local_catalog", values, "owner=? AND dialog=? AND mid=?", snapshot.args) == 1;
                        if (success) {
                            ContentValues detached = new ContentValues();
                            detached.put("metadata", ""); detached.put("metadata_id", 0); detached.put("metadata_origin", 0);
                            detached.put("series", 0); detached.put("title_text", TjMediaTitle.normalizeSearch(title));
                            db.update("media", detached, "owner=? AND dialog=? AND mid=?", snapshot.args);
                            TjMediaLocalIndex.refreshTitle(db, snapshot.args);
                        }
                        if (ownerActive(account, owner)) db.setTransactionSuccessful(); else success = false;
                    } finally { db.endTransaction(); }
                }
            } catch (Exception e) { success = false; FileLog.e("Tj local title save failed", e); }
            boolean saved = success;
            AndroidUtilities.runOnUIThread(() -> { callback.run(saved && ownerActive(account, owner)); if (saved) changed(); });
        });
    }

    private void readLocalRecords(SQLiteDatabase db, java.util.List<Record> records, long owner) {
        if (!localCatalogAvailable || records.isEmpty()) return;
        StringBuilder selection = new StringBuilder("owner=? AND (");
        java.util.HashMap<String, Record> byMessage = new java.util.HashMap<>();
        for (Record record : records) {
            long dialog = record.message.getDialogId(); int mid = record.message.getId();
            if (!byMessage.isEmpty()) selection.append(" OR ");
            selection.append("(dialog=").append(dialog).append(" AND mid=").append(mid).append(')');
            byMessage.put(dialog + ":" + mid, record);
        }
        selection.append(')');
        try (Cursor cursor = db.query("media_local_catalog", new String[]{"dialog", "mid", "title_key", "title", "series", "uncertain", "manual", "year", "season", "episode"},
                selection.toString(), new String[]{Long.toString(owner)}, null, null, null)) {
            while (cursor.moveToNext()) {
                Record record = byMessage.get(cursor.getLong(0) + ":" + cursor.getInt(1));
                if (record == null) continue;
                record.localKey = cursor.getString(2); record.localTitle = cursor.getString(3);
                record.localSeries = cursor.getInt(4) != 0; record.localUncertain = cursor.getInt(5) != 0;
                record.localManual = cursor.getInt(6) != 0;
                record.localYear = cursor.getInt(7);
                record.indexedSeason = cursor.getInt(8); record.indexedEpisode = cursor.getInt(9);
            }
        }
    }

    /** Returns 1 for saved, 0 for an already changed/missing record, -1 for failure. */
    public void setMetadataIfAbsent(MessageObject message, TjMediaMetadata.Title title, Callback<Integer> callback) {
        saveMetadata(message, title, true, callback);
    }

    private void saveMetadata(MessageObject message, TjMediaMetadata.Title title, boolean onlyAbsent, Callback<Integer> callback) {
        if (!eligible(message)) { callback.run(-1); return; }
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        Snapshot snapshot = new Snapshot(message, owner);
        PreparedIndex catalogSnapshot = prepareIndex(message);
        ContentValues values = new ContentValues();
        values.put("metadata", title == null ? "" : title.json);
        values.put("metadata_id", title == null ? 0 : title.id);
        values.put("metadata_origin", title == null ? 0 : onlyAbsent ? 2 : 1);
        values.put("title_text", title == null ? "" : TjMediaTitle.normalizeSearch(title.name));
        values.put("series", title != null && title.series ? 1 : 0);
        queue.postRunnable(() -> {
            int result = -1;
            try {
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    result = snapshot.current(db) ? db.update("media", values,
                            "owner=? AND dialog=? AND mid=?" + (onlyAbsent ? " AND metadata=''" : ""), snapshot.args) : 0;
                    if (result > 0 && localCatalogAvailable && catalogSnapshot != null)
                        TjMediaLocalIndex.index(db, catalogSnapshot.values);
                    if (ownerActive(account, owner)) db.setTransactionSuccessful(); else result = -1;
                } finally { db.endTransaction(); }
            } catch (Exception e) { result = -1; FileLog.e("Tj metadata save failed", e); }
            int updated = result;
            AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner) ? updated : -1));
        });
    }

    public void setEpisode(MessageObject message, int season, int episode, Callback<Boolean> callback) {
        if (!eligible(message) || season < -2 || season > 999 || episode < -2 || episode > 9999) { callback.run(false); return; }
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        Snapshot snapshot = new Snapshot(message, owner);
        ContentValues values = new ContentValues();
        values.put("season_override", season);
        values.put("episode_override", episode);
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                SQLiteDatabase db = getWritableDatabase();
                if (snapshot.current(db)) success = db.update("media", values,
                        "owner=? AND dialog=? AND mid=?", snapshot.args) == 1;
            } catch (Exception e) { FileLog.e("Tj episode update failed", e); }
            boolean result = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner)));
        });
    }

    public static final class CollectionSummary {
        public final String name, preview, icon;
        public final long count;
        CollectionSummary(String name, long count, String preview, String icon) {
            this.name = name; this.count = count; this.preview = preview; this.icon = icon;
        }
    }

    /** Index-backed local list summaries; never downloads or deserializes media. */
    public void collectionSummaries(int account, Callback<ArrayList<CollectionSummary>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<CollectionSummary> result = new ArrayList<>();
            boolean success = false;
            if (ownerActive(account, owner)) try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT n.name, (SELECT COUNT(*) FROM media m WHERE m.owner=? AND m.collection=n.name AND m.collection<>''), "
                    + "(SELECT substr(COALESCE(NULLIF(m.filename,''),m.caption),1,120) FROM media m WHERE m.owner=? AND m.collection=n.name AND m.collection<>'' ORDER BY m.date DESC,m.dialog,m.mid LIMIT 1), "
                    + "(SELECT icon FROM media_collection_icons i WHERE i.owner=? AND i.name=n.name) "
                    + "FROM (SELECT name FROM media_collections WHERE owner=? UNION SELECT collection AS name FROM media WHERE owner=? AND collection<>'') n ORDER BY n.name COLLATE NOCASE",
                    new String[]{Long.toString(owner), Long.toString(owner), Long.toString(owner), Long.toString(owner), Long.toString(owner)})) {
                while (cursor.moveToNext()) result.add(new CollectionSummary(cursor.getString(0), cursor.getLong(1), cursor.getString(2), cursor.getString(3)));
                success = true;
            } catch (Exception e) { FileLog.e("Tj collection summaries failed", e); }
            boolean loaded = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(loaded && ownerActive(account, owner) ? result : null));
        });
    }

    public void collections(int account, Callback<ArrayList<String>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<String> names = new ArrayList<>();
            boolean success = false;
            if (ownerActive(account, owner)) try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT name FROM media_collections WHERE owner=? UNION SELECT collection AS name FROM media WHERE owner=? AND collection<>'' ORDER BY name COLLATE NOCASE",
                    new String[]{Long.toString(owner), Long.toString(owner)})) {
                while (cursor.moveToNext()) names.add(cursor.getString(0));
                success = true;
            } catch (Exception e) { FileLog.e("Tj media lists load failed", e); }
            boolean result = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner) ? names : null));
        });
    }

    /** null replacement deletes only the list and its memberships, never the files. */
    public void editCollection(int account, String oldName, String replacement, Callback<Boolean> callback) {
        editCollection(account, oldName, replacement, null, callback);
    }

    public void editCollection(int account, String oldName, String replacement, String icon, Callback<Boolean> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        String name = replacement == null ? null : replacement.trim();
        if (name != null && (name.isEmpty() || name.length() > 128)) { callback.run(false); return; }
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                SQLiteDatabase db = getWritableDatabase();
                if (!ownerActive(account, owner)) throw new IllegalStateException("Unavailable owner");
                db.beginTransaction();
                try {
                    if (name != null) {
                        if (!name.equals(oldName)) try (Cursor duplicate = db.rawQuery(
                                "SELECT 1 FROM media_collections WHERE owner=? AND name=? UNION ALL SELECT 1 FROM media WHERE owner=? AND collection=? AND collection<>'' LIMIT 1",
                                new String[]{Long.toString(owner), name, Long.toString(owner), name})) {
                            if (duplicate.moveToFirst()) throw new IllegalArgumentException("Collection already exists");
                        }
                        ContentValues row = new ContentValues(); row.put("owner", owner); row.put("name", name);
                        db.insertWithOnConflict("media_collections", null, row, SQLiteDatabase.CONFLICT_IGNORE);
                    }
                    if (oldName != null && !oldName.equals(name)) {
                        if (name != null) {
                            ContentValues renamed = new ContentValues(); renamed.put("name", name);
                            db.update("media_collection_icons", renamed, "owner=? AND name=?", new String[]{Long.toString(owner), oldName});
                        } else db.delete("media_collection_icons", "owner=? AND name=?", new String[]{Long.toString(owner), oldName});
                        ContentValues membership = new ContentValues(); membership.put("collection", name == null ? "" : name);
                        db.update("media", membership, "owner=? AND collection=?", new String[]{Long.toString(owner), oldName});
                        db.delete("media_collections", "owner=? AND name=?", new String[]{Long.toString(owner), oldName});
                    }
                    if (name != null && icon != null) {
                        ContentValues symbol = new ContentValues(); symbol.put("owner", owner); symbol.put("name", name); symbol.put("icon", icon);
                        if (db.insertWithOnConflict("media_collection_icons", null, symbol, SQLiteDatabase.CONFLICT_REPLACE) < 0)
                            throw new IllegalStateException("Collection icon write failed");
                    }
                    db.setTransactionSuccessful(); success = true;
                } finally { db.endTransaction(); }
            } catch (Exception e) { success = false; FileLog.e("Tj media collection edit failed", e); }
            boolean result = success;
            if (success) changed();
            AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner)));
        });
    }

    private static TjMediaMetadata.Title metadata(String json, boolean series) {
        if (json == null || json.isEmpty()) return null;
        try { return new TjMediaMetadata.Title(new org.json.JSONObject(json), series); }
        catch (org.json.JSONException ignored) { return null; }
    }

    /** mode: all, continue, history, favorites, watched, named collection. */
    public void load(int account, int mode, String query, String collection, TjMediaPageKey after,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, Callback<Page> callback) {
        loadInternal(account, mode, query, collection, after, selectedSources, sourceType, mediaType, 0, callback);
    }

    public void loadPreview(int account, int mode, String query, java.util.Set<Long> sources,
                            int sourceType, int mediaType, int limit, Callback<Page> callback) {
        loadInternal(account, mode, query, "", null, sources, sourceType, mediaType, 0,
                Math.max(1, Math.min(20, limit)), callback);
    }

    public void loadWindow(int account, int mode, String query, String collection, TjMediaPageKey boundary,
                           java.util.Set<Long> sources, int sourceType, int mediaType, int limit, Callback<Page> callback) {
        loadInternal(account, mode, query, collection, boundary, sources, sourceType, mediaType, 0,
                Math.max(1, Math.min(200, limit)), callback);
    }

    public void loadTitle(int account, long titleId, boolean series, TjMediaPageKey after,
                          java.util.Set<Long> selectedSources, int sourceType, Callback<Page> callback) {
        loadInternal(account, series ? 8 : 7, "", "", after, selectedSources, sourceType, 0, titleId, callback);
    }

    public void loadLocalTitle(int account, String key, TjMediaPageKey after,
                               java.util.Set<Long> sources, int sourceType, Callback<Page> callback) {
        loadInternal(account, 10, "", key, after, sources, sourceType, 0, 0, callback);
    }

    /** One row per title across the chosen owners. No per-message GROUP BY or full deserialization. */
    public void loadCatalog(java.util.List<Integer> accounts, boolean series, String query,
            TjMediaCatalogPageKey boundary, java.util.Map<Long, java.util.Set<Long>> selectedSources,
            int sourceType, int mediaType, Callback<CatalogPage> callback) {
        java.util.Map<Long, Integer> owners = new java.util.TreeMap<>();
        java.util.Map<Integer, Long> versions = new java.util.HashMap<>();
        for (int account : accounts) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            if (ownerActive(account, owner)) { owners.put(owner, account); versions.put(account, revision(account)); }
        }
        java.util.Map<Long, java.util.Set<Long>> sources = TjMediaSources.copy(selectedSources);
        queue.postRunnable(() -> {
            CatalogPage result = new CatalogPage();
            try {
                SQLiteDatabase db = getReadableDatabase();
                if (!localCatalogAvailable) throw new IllegalStateException("Local catalog unavailable");
                if (!owners.isEmpty() && TjMediaKind.matches(mediaType, TjMediaKind.VIDEO)) {
                    ArrayList<String> scopeArgs = new ArrayList<>();
                    StringBuilder scope = new StringBuilder("(");
                    for (long owner : owners.keySet()) {
                        if (scope.length() > 1) scope.append(" OR ");
                        scope.append("(c.owner=?"); scopeArgs.add(Long.toString(owner));
                        java.util.Set<Long> chats = sources.get(owner);
                        if (chats != null && !chats.isEmpty()) {
                            scope.append(" AND c.dialog IN (");
                            boolean first = true;
                            for (long chat : chats) { if (!first) scope.append(','); first = false; scope.append(chat); }
                            scope.append(')');
                        }
                        scope.append(')');
                    }
                    scope.append(')');
                    if (sourceType != 0) { scope.append(" AND m.source_type=?"); scopeArgs.add(Integer.toString(sourceType)); }
                    if (query != null && !query.trim().isEmpty()) {
                        if (!searchAvailable) throw new IllegalStateException("Media search unavailable");
                        String expression = TjMediaSearchIndex.query(query);
                        if (expression.isEmpty()) scope.append(" AND 0");
                        else { scope.append(" AND m.rowid IN (SELECT docid FROM media_fts WHERE media_fts MATCH ?)"); scopeArgs.add(expression); }
                    }
                    String from = " FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.catalog_key=";
                    ArrayList<String> args = new ArrayList<>(); args.add(series ? "1" : "0");
                    String where = "t.series=?";
                    if (boundary != null) { where += boundary.selection(); java.util.Collections.addAll(args, boundary.arguments()); }
                    where += " AND EXISTS (SELECT 1" + from + "t.title_key AND " + scope + ")";
                    args.addAll(scopeArgs);
                    boolean backwards = boundary != null && boundary.before;
                    int read = 0;
                    TjMediaCatalogPageKey first = null, last = null;
                    try (Cursor titles = db.rawQuery("SELECT t.title,t.title_key FROM media_catalog_titles t WHERE " + where
                            + " ORDER BY t.title " + (backwards ? "DESC,t.title_key DESC" : "ASC,t.title_key ASC") + " LIMIT 41", args.toArray(new String[0]))) {
                        while (titles.moveToNext()) {
                            if (read++ == 40) { if (backwards) result.hasPrevious = true; else result.hasMore = true; break; }
                            last = new TjMediaCatalogPageKey(titles.getString(0), titles.getString(1), false);
                            if (first == null) first = last;
                            ArrayList<String> sourceArgs = new ArrayList<>(); sourceArgs.add(last.key); sourceArgs.addAll(scopeArgs);
                            try (Cursor row = db.rawQuery("SELECT m.data,m.position,m.duration,m.played,m.watched,m.favorite,m.collection,m.metadata,m.series,m.source_type,m.season_override,m.episode_override,m.metadata_origin,c.owner"
                                    + from + "? AND " + scope + " ORDER BY c.owner,c.dialog,c.mid LIMIT 1", sourceArgs.toArray(new String[0]))) {
                                if (!row.moveToFirst()) continue;
                                long owner = row.getLong(13); int account = owners.get(owner);
                                if (!ownerActive(account, owner)) continue;
                                TLRPC.Message raw = deserialize(row.getBlob(0)); if (raw == null) continue;
                                Record record = new Record(); record.message = new MessageObject(account, raw, false, false);
                                if (!eligible(record.message) || !TjMediaKind.matches(mediaType, TjMediaKind.of(record.message))) continue;
                                record.position = row.getLong(1); record.duration = row.getLong(2); record.playedAt = row.getLong(3);
                                record.watched = row.getInt(4) != 0; record.favorite = row.getInt(5) != 0;
                                record.collection = row.getString(6); record.metadata = metadata(row.getString(7), row.getInt(8) != 0);
                                record.sourceType = row.getInt(9); record.seasonOverride = row.getInt(10);
                                record.episodeOverride = row.getInt(11); record.metadataOrigin = row.getInt(12);
                                readLocalRecords(db, java.util.Collections.singletonList(record), owner); result.add(record);
                            }
                        }
                    }
                    if (first != null) {
                        TjMediaCatalogPageKey low = backwards ? last : first, high = backwards ? first : last;
                        result.previousKey = new TjMediaCatalogPageKey(low.title, low.key, true);
                        result.nextKey = high;
                        if (backwards) { result.hasMore = true; java.util.Collections.reverse(result); }
                        else result.hasPrevious = boundary != null;
                    }
                }
            } catch (Exception e) {
                FileLog.e("Tj catalog page failed", e); AndroidUtilities.runOnUIThread(() -> callback.run(null)); return;
            }
            AndroidUtilities.runOnUIThread(() -> {
                for (long owner : owners.keySet()) {
                    int account = owners.get(owner);
                    if (!ownerActive(account, owner) || revision(account) != versions.get(account)) { callback.run(null); return; }
                }
                callback.run(result);
            });
        });
    }

    public static final int ANY_EPISODE = Integer.MIN_VALUE;
    private static final class EpisodeFilter {
        final int season, episode;
        EpisodeFilter(int season, int episode) { this.season = season; this.episode = episode; }
    }

    public static final class CatalogNumber {
        public final int number;
        public final long sources;
        public final boolean watched, inProgress;
        CatalogNumber(int number, long sources, boolean watched, boolean inProgress) {
            this.number = number; this.sources = sources; this.watched = watched; this.inProgress = inProgress;
        }
    }

    /** Number summaries only, never serialized messages; 100 rows plus lookahead. */
    public void catalogNumbers(int account, Record title, int season, int after,
            java.util.Set<Long> selectedSources, int sourceType, Callback<ArrayList<CatalogNumber>> callback) {
        catalogNumbers(account, title, season, after, selectedSources, sourceType, false, callback);
    }

    public void catalogNumbers(int account, Record title, int season, int after,
            java.util.Set<Long> selectedSources, int sourceType, boolean numberedOnly, Callback<ArrayList<CatalogNumber>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        String key = title.catalogKey();
        java.util.Set<Long> sources = selectedSources == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(selectedSources);
        queue.postRunnable(() -> {
            ArrayList<CatalogNumber> result = new ArrayList<>();
            try {
                SQLiteDatabase db = getReadableDatabase();
                if (!ownerActive(account, owner) || !localCatalogAvailable) throw new IllegalStateException("Unavailable catalog owner");
                String seasonSql = "CASE WHEN m.season_override=-2 THEN COALESCE(c.season,-1) ELSE m.season_override END";
                String episodeSql = "CASE WHEN m.episode_override=-2 THEN COALESCE(c.episode,-1) ELSE m.episode_override END";
                String number = season == ANY_EPISODE ? seasonSql : episodeSql;
                String where = "c.owner=? AND c.catalog_key=?";
                ArrayList<String> args = new ArrayList<>(); args.add(Long.toString(owner));
                args.add(key);
                if (key.isEmpty()) {
                    // An uncertain identity still has one known source, not a global group of empty keys.
                    where += " AND c.dialog=? AND c.mid=?";
                    args.add(Long.toString(title.message.getDialogId())); args.add(Integer.toString(title.message.getId()));
                    if (account != title.message.currentAccount) where += " AND 0";
                }
                if (numberedOnly) where += " AND (" + seasonSql + ")>=0 AND (" + episodeSql + ")>=0";
                if (!sources.isEmpty()) {
                    StringBuilder ids = new StringBuilder();
                    for (long id : sources) { if (ids.length() > 0) ids.append(','); ids.append(id); }
                    where += " AND m.dialog IN (" + ids + ")";
                }
                if (sourceType != 0) { where += " AND m.source_type=?"; args.add(Integer.toString(sourceType)); }
                if (season != ANY_EPISODE) { where += " AND (" + seasonSql + ")=?"; args.add(Integer.toString(season)); }
                // Unknown numbering is last, not silently interpreted as episode zero.
                String order = "CASE WHEN (" + number + ")<0 THEN 2147483647 ELSE (" + number + ") END";
                where += " AND (" + order + ")>?"; args.add(Integer.toString(after));
                try (Cursor rows = db.rawQuery("SELECT " + number + ",COUNT(*),MAX(CASE WHEN m.watched<>0 OR (m.duration>0 AND m.position>=m.duration*0.98) THEN 1 ELSE 0 END),MAX(CASE WHEN m.watched=0 AND m.position>0 AND m.duration>0 AND m.position<m.duration*0.98 THEN 1 ELSE 0 END) FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE "
                        + where + " GROUP BY " + number + " ORDER BY " + order + " LIMIT 101", args.toArray(new String[0]))) {
                    while (rows.moveToNext()) result.add(new CatalogNumber(rows.getInt(0), rows.getLong(1), rows.getInt(2) != 0, rows.getInt(3) != 0));
                }
            } catch (Exception e) {
                FileLog.e("Tj catalog numbers failed", e); AndroidUtilities.runOnUIThread(() -> callback.run(null)); return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner) ? result : null));
        });
    }

    public void loadCatalogSources(int account, Record title, int season, int episode, TjMediaPageKey after,
            java.util.Set<Long> sources, int sourceType, Callback<Page> callback) {
        if (title.catalogKey().isEmpty()) {
            if (account != title.message.currentAccount || after != null) { callback.run(new Page()); return; }
            state(title.message, current -> {
                if (current == null) { callback.run(null); return; }
                Page page = new Page();
                if ((sources == null || sources.isEmpty() || sources.contains(current.message.getDialogId()))
                        && (sourceType == 0 || current.sourceType == sourceType)
                        && (season == ANY_EPISODE || current.season() == season)
                        && (episode == ANY_EPISODE || current.episode() == episode)) page.add(current);
                callback.run(page);
            });
            return;
        }
        boolean local = !title.localKey.isEmpty();
        loadInternal(account, local ? 10 : title.isSeries() ? 8 : 7, "", title.localKey, after, sources,
                sourceType, 0, local || title.metadata == null ? 0 : title.metadata.id, 50,
                new EpisodeFilter(season, episode), callback);
    }

    /** One indexed source for a visible episode thumbnail, not a page of every variant. */
    public void loadEpisodePreview(int account, Record title, int season, int episode,
            java.util.Set<Long> sources, int sourceType, Callback<Page> callback) {
        if (title.catalogKey().isEmpty()) {
            loadCatalogSources(account, title, season, episode, null, sources, sourceType, callback);
            return;
        }
        boolean local = !title.localKey.isEmpty();
        loadInternal(account, local ? 10 : title.isSeries() ? 8 : 7, "", title.localKey, null, sources,
                sourceType, 0, local || title.metadata == null ? 0 : title.metadata.id, 1,
                new EpisodeFilter(season, episode), callback);
    }

    private void loadInternal(int account, int mode, String query, String collection, TjMediaPageKey after,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, long titleId, Callback<Page> callback) {
        loadInternal(account, mode, query, collection, after, selectedSources, sourceType, mediaType, titleId, 200, callback);
    }

    private void loadInternal(int account, int mode, String query, String collection, TjMediaPageKey after,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, long titleId, int limit, Callback<Page> callback) {
        loadInternal(account, mode, query, collection, after, selectedSources, sourceType, mediaType, titleId, limit, null, callback);
    }

    private void loadInternal(int account, int mode, String query, String collection, TjMediaPageKey after,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, long titleId, int limit,
                     EpisodeFilter episodeFilter, Callback<Page> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        java.util.Set<Long> sources = selectedSources == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(selectedSources);
        queue.postRunnable(() -> {
            Page result = new Page();
            if (ownerActive(account, owner)) try {
                boolean byPlayed = mode == 1 || mode == 2;
                boolean backwards = after != null && after.before;
                if (after != null && !after.matches(owner, byPlayed)) {
                    throw new IllegalArgumentException("Media page boundary belongs to a different owner or order");
                }
                String where = "owner=?";
                ArrayList<String> args = new ArrayList<>();
                args.add(Long.toString(owner));
                if (after != null) {
                    where += after.selection();
                    java.util.Collections.addAll(args, after.arguments());
                }
                if (titleId > 0) { where += " AND metadata_id=?"; args.add(Long.toString(titleId)); }
                if (!sources.isEmpty()) {
                    StringBuilder peers = new StringBuilder();
                    for (long peer : sources) { if (peers.length() > 0) peers.append(','); peers.append(peer); }
                    // Only primitive long IDs enter this expression, never user text.
                    where += " AND dialog IN (" + peers + ")";
                }
                if (sourceType > 0) { where += " AND source_type=?"; args.add(Integer.toString(sourceType)); }
                if (mediaType != 0) {
                    int mask = mediaType > 0 ? 1 << mediaType : -mediaType;
                    // Old rows grouped GIF with video and container files with documents.
                    // Include these candidates until bounded reclassification is complete.
                    if ((mask & (1 << TjMediaKind.GIF)) != 0) mask |= 1 << TjMediaKind.VIDEO;
                    if ((mask & (1 << TjMediaKind.VIDEO)) != 0) mask |= 1 << TjMediaKind.DOCUMENT;
                    where += " AND ((1 << media_type) & ?) != 0";
                    args.add(Integer.toString(mask));
                }
                if (mode == 1) where += " AND position>0 AND duration>0 AND position<duration*0.98 AND watched=0";
                if (mode == 2) where += " AND played>0";
                if (mode == 3) where += " AND favorite=1";
                if (mode == 4) where += " AND watched=1";
                if (mode == 5) { where += " AND collection=? AND collection<>''"; args.add(collection == null ? "" : collection); }
                if (mode == 7 || mode == 8) {
                    int series = mode == 8 ? 1 : 0;
                    where += localCatalogAvailable
                            ? " AND ((metadata<>'' AND series=" + series + ") OR EXISTS (SELECT 1 FROM media_local_catalog c WHERE c.owner=media.owner AND c.dialog=media.dialog AND c.mid=media.mid AND c.title_key<>'' AND c.series=" + series + "))"
                            : " AND metadata<>'' AND series=" + series;
                }
                if (mode == 10) {
                    if (!localCatalogAvailable) throw new IllegalStateException("Local catalog unavailable");
                    where += " AND rowid IN (SELECT m.rowid FROM media_local_catalog c JOIN media m ON m.owner=c.owner AND m.dialog=c.dialog AND m.mid=c.mid WHERE c.owner=? AND c.title_key=?)";
                    args.add(Long.toString(owner));
                    args.add(collection);
                }
                if (episodeFilter != null) {
                    for (String column : new String[]{"season", "episode"}) {
                        int number = column.equals("season") ? episodeFilter.season : episodeFilter.episode;
                        if (number == ANY_EPISODE) continue;
                        where += " AND (CASE WHEN " + column + "_override=-2 THEN COALESCE((SELECT " + column
                                + " FROM media_local_catalog c WHERE c.owner=media.owner AND c.dialog=media.dialog AND c.mid=media.mid),-1) ELSE " + column + "_override END)=?";
                        args.add(Integer.toString(number));
                    }
                }
                if (query != null && !query.trim().isEmpty()) {
                    getReadableDatabase(); // Initializes the optional search index on first use.
                    if (!searchAvailable) throw new IllegalStateException("Media search index unavailable");
                    String expression = TjMediaSearchIndex.query(query);
                    if (expression.isEmpty()) where += " AND 0";
                    else {
                        where += " AND rowid IN (SELECT docid FROM media_fts WHERE media_fts MATCH ?)";
                        args.add(expression);
                    }
                }
                // For a local title, prefer rowid probes from the title index.
                // Otherwise SQLite may scan millions of owner/date rows to avoid
                // sorting the small matching title set. NOT INDEXED retains rowid lookup.
                try (Cursor cursor = getReadableDatabase().query(mode == 10 ? "media NOT INDEXED" : "media", new String[]{"data", "position", "duration", "played", "watched", "favorite", "collection", "metadata", "series", "source_type", "season_override", "episode_override", "metadata_origin", "date", "dialog", "mid"},
                        where, args.toArray(new String[0]), null, null,
                        backwards ? (byPlayed ? "played ASC, dialog DESC, mid DESC" : "date ASC, dialog DESC, mid DESC")
                                : byPlayed ? TjMediaPageKey.PLAYED_ORDER : TjMediaPageKey.DATE_ORDER,
                        limit == 200 ? "201" : Integer.toString(limit + 1))) {
                    int read = 0;
                    TjMediaPageKey firstKey = null;
                    while (cursor.moveToNext()) {
                        if (read == limit) { result.hasMore = true; break; }
                        read++;
                        // Advance over raw rows even if an old payload cannot be decoded.
                        // The lookahead row is not consumed and will start the next page.
                        result.nextKey = new TjMediaPageKey(owner, byPlayed,
                                cursor.getLong(byPlayed ? 3 : 13), cursor.getLong(14), cursor.getInt(15));
                        if (firstKey == null) firstKey = result.nextKey;
                        TLRPC.Message raw = deserialize(cursor.getBlob(0));
                        if (raw == null) continue;
                        Record record = new Record();
                        record.message = new MessageObject(account, raw, false, false);
                        if (!eligible(record.message)) continue;
                        if (!TjMediaKind.matches(mediaType, TjMediaKind.of(record.message))) continue;
                        record.position = cursor.getLong(1);
                        record.duration = cursor.getLong(2);
                        record.playedAt = cursor.getLong(3);
                        record.watched = cursor.getInt(4) != 0;
                        record.favorite = cursor.getInt(5) != 0;
                        record.collection = cursor.getString(6);
                        record.metadata = metadata(cursor.getString(7), cursor.getInt(8) != 0);
                        record.sourceType = cursor.getInt(9);
                        record.seasonOverride = cursor.getInt(10);
                        record.episodeOverride = cursor.getInt(11);
                        record.metadataOrigin = cursor.getInt(12);
                        result.add(record);
                    }
                    if (firstKey != null) {
                        if (backwards) {
                            result.previousKey = result.nextKey.previous();
                            result.nextKey = firstKey;
                            result.hasPrevious = result.hasMore;
                            result.hasMore = true;
                            java.util.Collections.reverse(result);
                        } else {
                            result.previousKey = firstKey.previous();
                            result.hasPrevious = after != null;
                        }
                    }
                }
                readLocalRecords(getReadableDatabase(), result, owner);
            } catch (Exception e) {
                FileLog.e("Tj media query failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner) ? result : new Page()));
        });
    }

    private static String[] identity(long owner, long dialog, int mid) {
        return new String[]{Long.toString(owner), Long.toString(dialog), Integer.toString(mid)};
    }

    private static byte[] serialize(TLRPC.Message message) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
            int length = buffer.position();
            buffer.position(0);
            return buffer.readData(length, true);
        } catch (Exception e) { FileLog.e("Tj media serialization failed", e); return null; }
        finally { if (buffer != null) buffer.reuse(); }
    }

    private static TLRPC.Message deserialize(byte[] data) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(data.length);
            buffer.writeBytes(data);
            buffer.position(0);
            return TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
        } catch (Exception e) { FileLog.e("Tj media deserialization failed", e); return null; }
        finally { if (buffer != null) buffer.reuse(); }
    }
}
