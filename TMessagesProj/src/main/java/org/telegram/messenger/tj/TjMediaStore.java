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
        public int seasonOverride = -2, episodeOverride = -2;
        public int season() { return seasonOverride != -2 ? seasonOverride : TjMediaTitle.parse(message.getDocumentName(), message.messageOwner.message).season; }
        public int episode() { return episodeOverride != -2 ? episodeOverride : TjMediaTitle.parse(message.getDocumentName(), message.messageOwner.message).episode; }
    }

    public static final class Page extends ArrayList<Record> {
        public int nextOffset;
        public boolean hasMore;
    }

    private TjMediaStore() {
        super(ApplicationLoader.applicationContext, "tj_media_library.db", null, 7);
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
                    getWritableDatabase().delete("media", "owner=?", new String[]{Long.toString(owner)});
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
                try { getWritableDatabase().delete("media", "owner=?", new String[]{Long.toString(previous)}); }
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
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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
        if (message.isPhoto()) return 1;
        if (message.isVideo() || message.isGif()) return 2;
        if (message.isMusic()) return 4;
        if (message.isVoice() || message.isRoundVideo()) return 5;
        return 3;
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
        if (!eligible(message)) {
            if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        int account = message.currentAccount;
        long owner = UserConfig.getInstance(account).getClientUserId();
        observedOwners[account] = owner;
        long dialog = message.getDialogId();
        int mid = message.getId();
        long document = message.getDocument() == null ? 0 : message.getDocument().id;
        byte[] data = serialize(message.messageOwner);
        if (data == null || !ownerActive(account, owner)) {
            if (callback != null) AndroidUtilities.runOnUIThread(() -> callback.run(false));
            return;
        }
        ContentValues values = new ContentValues();
        values.put("owner", owner);
        values.put("dialog", dialog);
        values.put("mid", mid);
        values.put("document", document);
        values.put("data", data);
        values.put("filename", message.getDocumentName() == null ? "" : message.getDocumentName());
        values.put("caption", message.messageOwner.message == null ? "" : message.messageOwner.message);
        values.put("date", message.messageOwner.date);
        values.put("source_type", sourceType(message));
        values.put("media_type", mediaType(message));
        values.put("search_text", TjMediaTitle.normalizeSearch(values.getAsString("filename") + " " + values.getAsString("caption")));
        queue.postRunnable(() -> {
            boolean success = false;
            try {
                if (!ownerActive(account, owner) || expectedRevision >= 0 && revision(account) != expectedRevision) return;
                SQLiteDatabase db = getWritableDatabase();
                String[] args = identity(owner, dialog, mid);
                try (Cursor cursor = db.query("media", new String[]{"document"}, "owner=? AND dialog=? AND mid=?", args, null, null, null)) {
                    boolean exists = cursor.moveToFirst();
                    if (existingOnly && !exists) return;
                    if (exists && cursor.getLong(0) != document) {
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
                if (existingOnly) changed();
                success = true;
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
                if (snapshot.current(db)) success = db.update("media", values,
                        "owner=? AND dialog=? AND mid=?", snapshot.args) == 1;
            } catch (Exception e) { FileLog.e("Tj media list update failed", e); }
            boolean result = success;
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
            try (Cursor cursor = getReadableDatabase().query("media", new String[]{"position", "duration", "played", "watched", "favorite", "collection", "metadata", "series", "season_override", "episode_override", "metadata_origin"},
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
                }
            } catch (Exception e) {
                FileLog.e("Tj media state failed", e);
                AndroidUtilities.runOnUIThread(() -> callback.run(null));
                return;
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(ownerActive(account, owner) ? record : null));
        });
    }

    public void setMetadata(MessageObject message, TjMediaMetadata.Title title, Callback<Boolean> callback) {
        saveMetadata(message, title, false, result -> callback.run(result == 1));
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
                result = snapshot.current(db) ? db.update("media", values,
                        "owner=? AND dialog=? AND mid=?" + (onlyAbsent ? " AND metadata=''" : ""), snapshot.args) : 0;
            } catch (Exception e) { FileLog.e("Tj metadata save failed", e); }
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

    public void collections(int account, Callback<ArrayList<String>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<String> names = new ArrayList<>();
            boolean success = false;
            if (ownerActive(account, owner)) try (Cursor cursor = getReadableDatabase().rawQuery(
                    "SELECT DISTINCT collection FROM media WHERE owner=? AND collection<>'' ORDER BY collection COLLATE NOCASE",
                    new String[]{Long.toString(owner)})) {
                while (cursor.moveToNext()) names.add(cursor.getString(0));
                success = true;
            } catch (Exception e) { FileLog.e("Tj media lists load failed", e); }
            boolean result = success;
            AndroidUtilities.runOnUIThread(() -> callback.run(result && ownerActive(account, owner) ? names : null));
        });
    }

    private static TjMediaMetadata.Title metadata(String json, boolean series) {
        if (json == null || json.isEmpty()) return null;
        try { return new TjMediaMetadata.Title(new org.json.JSONObject(json), series); }
        catch (org.json.JSONException ignored) { return null; }
    }

    /** mode: all, continue, history, favorites, watched, named collection. */
    public void load(int account, int mode, String query, String collection, int offset,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, Callback<Page> callback) {
        loadInternal(account, mode, query, collection, offset, selectedSources, sourceType, mediaType, 0, callback);
    }

    public void loadTitle(int account, long titleId, boolean series, int offset,
                          java.util.Set<Long> selectedSources, int sourceType, Callback<Page> callback) {
        loadInternal(account, series ? 8 : 7, "", "", offset, selectedSources, sourceType, 0, titleId, callback);
    }

    private void loadInternal(int account, int mode, String query, String collection, int offset,
                     java.util.Set<Long> selectedSources, int sourceType, int mediaType, long titleId, Callback<Page> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        java.util.Set<Long> sources = selectedSources == null ? java.util.Collections.emptySet() : new java.util.HashSet<>(selectedSources);
        queue.postRunnable(() -> {
            Page result = new Page();
            result.nextOffset = Math.max(0, offset);
            if (ownerActive(account, owner)) try {
                String where = "owner=?";
                ArrayList<String> args = new ArrayList<>();
                args.add(Long.toString(owner));
                if (titleId > 0) { where += " AND metadata_id=?"; args.add(Long.toString(titleId)); }
                if (!sources.isEmpty()) {
                    StringBuilder peers = new StringBuilder();
                    for (long peer : sources) { if (peers.length() > 0) peers.append(','); peers.append(peer); }
                    // Only primitive long IDs enter this expression, never user text.
                    where += " AND dialog IN (" + peers + ")";
                }
                if (sourceType > 0) { where += " AND source_type=?"; args.add(Integer.toString(sourceType)); }
                if (mediaType > 0) { where += " AND media_type=?"; args.add(Integer.toString(mediaType)); }
                if (mode == 1) where += " AND position>0 AND duration>0 AND position<duration*0.98 AND watched=0";
                if (mode == 2) where += " AND played>0";
                if (mode == 3) where += " AND favorite=1";
                if (mode == 4) where += " AND watched=1";
                if (mode == 5) { where += " AND collection=?"; args.add(collection == null ? "" : collection); }
                if (mode == 7 || mode == 8) where += " AND metadata<>'' AND series=" + (mode == 8 ? 1 : 0);
                // Match filename and caption together, without SQL wildcard interpretation.
                for (String token : TjMediaTitle.normalizeSearch(query).split("\\s+")) if (!token.isEmpty()) {
                    where += " AND instr(search_text || ' ' || title_text, ?)>0";
                    args.add(token);
                }
                try (Cursor cursor = getReadableDatabase().query("media", new String[]{"data", "position", "duration", "played", "watched", "favorite", "collection", "metadata", "series", "source_type", "season_override", "episode_override", "metadata_origin"},
                        where, args.toArray(new String[0]), null, null, (mode == 1 || mode == 2 ? "played DESC" : "date DESC") + ", dialog, mid", "201 OFFSET " + Math.max(0, offset))) {
                    int read = 0;
                    while (cursor.moveToNext()) {
                        if (read == 200) { result.hasMore = true; break; }
                        read++;
                        result.nextOffset++;
                        TLRPC.Message raw = deserialize(cursor.getBlob(0));
                        if (raw == null) continue;
                        Record record = new Record();
                        record.message = new MessageObject(account, raw, false, false);
                        if (!eligible(record.message)) continue;
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
                }
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
