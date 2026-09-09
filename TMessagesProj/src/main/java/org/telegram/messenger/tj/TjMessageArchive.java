package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.security.MessageDigest;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Stores complete Telegram message snapshots before edits and deletions. */
public final class TjMessageArchive extends SQLiteOpenHelper {
    public static final int KIND_DELETED = 0;
    public static final int KIND_EDITED = 1;

    private static final String DATABASE_NAME = "tj_message_archive.db";
    private static final int DATABASE_VERSION = 2;
    private static volatile TjMessageArchive instance;
    private final Set<String> revisionIndex = ConcurrentHashMap.newKeySet();

    public interface Callback<T> {
        void onResult(T result);
    }

    public static final class Snapshot {
        public long id;
        public int accountId;
        public long ownerUserId;
        public long dialogId;
        public int topicId;
        public int messageId;
        public long groupedId;
        public int kind;
        public long capturedAt;
        public int editDate;
        public String mediaPath;
        public TLRPC.Message message;
    }

    private static final class PendingSnapshot {
        int accountId;
        long ownerUserId;
        long dialogId;
        int topicId;
        int messageId;
        long groupedId;
        int kind;
        long capturedAt;
        int editDate;
        String fingerprint;
        String mediaPath;
        byte[] data;
    }

    private TjMessageArchive(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        Utilities.globalQueue.postRunnable(this::loadRevisionIndex);
    }

    public static TjMessageArchive getInstance() {
        TjMessageArchive local = instance;
        if (local == null) {
            synchronized (TjMessageArchive.class) {
                local = instance;
                if (local == null) {
                    local = instance = new TjMessageArchive(ApplicationLoader.applicationContext);
                }
            }
        }
        return local;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE snapshots (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "account_id INTEGER NOT NULL," +
                "owner_user_id INTEGER NOT NULL," +
                "dialog_id INTEGER NOT NULL," +
                "topic_id INTEGER NOT NULL DEFAULT 0," +
                "message_id INTEGER NOT NULL," +
                "grouped_id INTEGER NOT NULL DEFAULT 0," +
                "kind INTEGER NOT NULL," +
                "captured_at INTEGER NOT NULL," +
                "edit_date INTEGER NOT NULL DEFAULT 0," +
                "fingerprint TEXT NOT NULL," +
                "media_path TEXT," +
                "data BLOB NOT NULL)");
        db.execSQL("CREATE INDEX snapshots_range_idx ON snapshots(owner_user_id, account_id, dialog_id, topic_id, kind, message_id)");
        db.execSQL("CREATE INDEX snapshots_revisions_idx ON snapshots(owner_user_id, account_id, dialog_id, message_id, kind, captured_at)");
        db.execSQL("CREATE UNIQUE INDEX snapshots_deleted_unique ON snapshots(owner_user_id, dialog_id, message_id) WHERE kind = 0");
        db.execSQL("CREATE UNIQUE INDEX snapshots_content_unique ON snapshots(owner_user_id, dialog_id, message_id, kind, fingerprint)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // V1 used the reusable local account slot as ownership. It cannot be migrated without
            // risking that a new login sees the previous user's messages.
            db.execSQL("DROP TABLE IF EXISTS snapshots");
            deleteSavedAttachments();
            onCreate(db);
        }
    }

    public boolean saveDeleted(int accountId, TLRPC.Message message) {
        if (!TjConfig.saveDeletedMessages() || !shouldArchive(accountId, message)) {
            return false;
        }
        enqueue(message, accountId, KIND_DELETED);
        return true;
    }

    /**
     * Saves one-time media and reports success only after both the serialized message and the
     * attachment are durable. Callers must not acknowledge the view to Telegram before success.
     */
    public void saveViewOnce(int accountId, TLRPC.Message message, Callback<Boolean> callback) {
        if (!TjConfig.saveDeletedMessages() || !shouldArchive(accountId, message)
                || !shouldSaveMedia(accountId, message)) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(false));
            return;
        }
        byte[] data = serializeForArchive(message);
        enqueue(message, accountId, KIND_DELETED, data, true, callback);
    }

    public void saveEdited(int accountId, TLRPC.Message oldMessage, TLRPC.Message newMessage) {
        if (!TjConfig.saveEditedMessages() || oldMessage == null || newMessage == null ||
                !shouldArchive(accountId, oldMessage)) {
            return;
        }
        if (!hasEditableContentChanged(oldMessage, newMessage)) {
            return;
        }
        byte[] oldData = serializeForArchive(oldMessage);
        if (oldData == null) {
            return;
        }
        enqueue(oldMessage, accountId, KIND_EDITED, oldData);
    }

    private static boolean hasEditableContentChanged(TLRPC.Message oldMessage, TLRPC.Message newMessage) {
        if (!TextUtils.equals(oldMessage.message, newMessage.message) || !hasSameMedia(oldMessage, newMessage)) {
            return true;
        }
        return TjConfig.saveFormatting()
                && !Arrays.equals(serializeEntities(oldMessage), serializeEntities(newMessage));
    }

    private static boolean hasSameMedia(TLRPC.Message first, TLRPC.Message second) {
        if (first.media == second.media) {
            return true;
        }
        if (first.media == null || second.media == null || first.media.getClass() != second.media.getClass()) {
            return false;
        }
        TLRPC.Photo firstPhoto = MessageObject.getPhoto(first);
        TLRPC.Photo secondPhoto = MessageObject.getPhoto(second);
        if (firstPhoto != null || secondPhoto != null) {
            return firstPhoto != null && secondPhoto != null && firstPhoto.id == secondPhoto.id;
        }
        TLRPC.Document firstDocument = MessageObject.getDocument(first);
        TLRPC.Document secondDocument = MessageObject.getDocument(second);
        if (firstDocument != null || secondDocument != null) {
            return firstDocument != null && secondDocument != null && firstDocument.id == secondDocument.id;
        }
        return true;
    }

    private static byte[] serializeEntities(TLRPC.Message message) {
        if (message.entities == null || message.entities.isEmpty()) {
            return new byte[0];
        }
        NativeByteBuffer buffer = null;
        try {
            int size = 4;
            for (TLRPC.MessageEntity entity : message.entities) {
                size += entity.getObjectSize();
            }
            buffer = new NativeByteBuffer(size);
            buffer.writeInt32(message.entities.size());
            for (TLRPC.MessageEntity entity : message.entities) {
                entity.serializeToStream(buffer);
            }
            int length = buffer.position();
            buffer.position(0);
            return buffer.readData(length, true);
        } catch (Throwable error) {
            FileLog.e("Tj message entities serialization failed", error);
            return new byte[0];
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
    }

    private void enqueue(TLRPC.Message message, int accountId, int kind) {
        enqueue(message, accountId, kind, serializeForArchive(message));
    }

    private void enqueue(TLRPC.Message message, int accountId, int kind, byte[] data) {
        enqueue(message, accountId, kind, data, false, null);
    }

    private void enqueue(TLRPC.Message message, int accountId, int kind, byte[] data,
                         boolean requireMedia, Callback<Boolean> callback) {
        if (message == null || data == null || message.id == 0) {
            postResult(callback, false);
            return;
        }
        PendingSnapshot snapshot = new PendingSnapshot();
        snapshot.accountId = accountId;
        snapshot.ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        if (snapshot.ownerUserId == 0) {
            postResult(callback, false);
            return;
        }
        snapshot.dialogId = MessageObject.getDialogId(message);
        snapshot.topicId = (int) MessageObject.getTopicId(accountId, message, false);
        snapshot.messageId = message.id;
        snapshot.groupedId = message.grouped_id;
        snapshot.kind = kind;
        snapshot.capturedAt = System.currentTimeMillis();
        snapshot.editDate = message.edit_date;
        snapshot.data = data;
        String attachPath = message.attachPath;
        Utilities.globalQueue.postRunnable(() -> {
            snapshot.fingerprint = fingerprint(snapshot.data);
            TLRPC.Message archivedMessage = deserialize(snapshot.data);
            if (archivedMessage != null) {
                archivedMessage.attachPath = attachPath;
                snapshot.mediaPath = copyMediaIfNeeded(accountId, snapshot.ownerUserId, archivedMessage);
            }
            boolean success = !requireMedia || !TextUtils.isEmpty(snapshot.mediaPath);
            if (success) {
                success = insert(snapshot);
                pruneToQuota(snapshot.ownerUserId);
                if (requireMedia) {
                    success = success && hasDurableMedia(snapshot);
                }
            }
            postResult(callback, success);
        });
    }

    private boolean insert(PendingSnapshot snapshot) {
        try {
            ContentValues values = new ContentValues();
            values.put("account_id", snapshot.accountId);
            values.put("owner_user_id", snapshot.ownerUserId);
            values.put("dialog_id", snapshot.dialogId);
            values.put("topic_id", snapshot.topicId);
            values.put("message_id", snapshot.messageId);
            values.put("grouped_id", snapshot.groupedId);
            values.put("kind", snapshot.kind);
            values.put("captured_at", snapshot.capturedAt);
            values.put("edit_date", snapshot.editDate);
            values.put("fingerprint", snapshot.fingerprint);
            values.put("media_path", snapshot.mediaPath);
            values.put("data", snapshot.data);
            long rowId = getWritableDatabase().insertWithOnConflict("snapshots", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            boolean stored = rowId != -1;
            if (!stored && snapshot.kind == KIND_DELETED && !TextUtils.isEmpty(snapshot.mediaPath)) {
                ContentValues media = new ContentValues();
                media.put("media_path", snapshot.mediaPath);
                stored = getWritableDatabase().update("snapshots", media,
                        "owner_user_id=? AND dialog_id=? AND message_id=? AND kind=?",
                        new String[]{String.valueOf(snapshot.ownerUserId), String.valueOf(snapshot.dialogId),
                                String.valueOf(snapshot.messageId), String.valueOf(KIND_DELETED)}) > 0;
            }
            if (stored && snapshot.kind == KIND_EDITED) {
                revisionIndex.add(revisionKey(snapshot.ownerUserId, snapshot.accountId,
                        snapshot.dialogId, snapshot.messageId));
            }
            return stored;
        } catch (Throwable error) {
            FileLog.e("Tj message archive insert failed", error);
            return false;
        }
    }

    private boolean hasDurableMedia(PendingSnapshot snapshot) {
        try (Cursor cursor = getReadableDatabase().query("snapshots", new String[]{"media_path"},
                "owner_user_id=? AND account_id=? AND dialog_id=? AND message_id=? AND kind=?",
                new String[]{String.valueOf(snapshot.ownerUserId), String.valueOf(snapshot.accountId),
                        String.valueOf(snapshot.dialogId), String.valueOf(snapshot.messageId),
                        String.valueOf(snapshot.kind)}, null, null, null, "1")) {
            if (!cursor.moveToFirst()) return false;
            String path = cursor.getString(0);
            return !TextUtils.isEmpty(path) && new File(path).isFile() && new File(path).length() > 0;
        } catch (Throwable error) {
            FileLog.e("Tj durable media verification failed", error);
            return false;
        }
    }

    private static void postResult(Callback<Boolean> callback, boolean result) {
        if (callback != null) {
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        }
    }

    public void getDeleted(int accountId, long dialogId, int topicId, int minId, int maxId,
                           int limit, Callback<ArrayList<Snapshot>> callback) {
        query(accountId, dialogId, topicId, minId, maxId, KIND_DELETED, limit, callback);
    }

    public ArrayList<Snapshot> getDeletedSync(int accountId, long dialogId, int topicId,
                                              int minId, int maxId, int limit) {
        ArrayList<Snapshot> result = new ArrayList<>();
        long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        String selection = "owner_user_id=? AND account_id=? AND dialog_id=? AND topic_id=? AND kind=? AND message_id>=? AND message_id<=?";
        String[] args = {String.valueOf(ownerUserId), String.valueOf(accountId), String.valueOf(dialogId), String.valueOf(topicId),
                String.valueOf(KIND_DELETED), String.valueOf(minId), String.valueOf(maxId)};
        try (Cursor cursor = getReadableDatabase().query(
                "snapshots", null, selection, args, null, null, "message_id ASC",
                String.valueOf(Math.max(1, limit)))) {
            while (cursor.moveToNext()) {
                Snapshot snapshot = read(cursor);
                if (snapshot != null) {
                    result.add(snapshot);
                }
            }
        } catch (Throwable error) {
            FileLog.e("Tj deleted messages sync query failed", error);
        }
        return result;
    }

    public void getRevisions(int accountId, long dialogId, int messageId,
                             Callback<ArrayList<Snapshot>> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<Snapshot> result = new ArrayList<>();
            try (Cursor cursor = getReadableDatabase().query(
                    "snapshots", null,
                    "owner_user_id=? AND account_id=? AND dialog_id=? AND message_id=? AND kind=?",
                    new String[]{String.valueOf(UserConfig.getInstance(accountId).getClientUserId()), String.valueOf(accountId), String.valueOf(dialogId),
                            String.valueOf(messageId), String.valueOf(KIND_EDITED)},
                    null, null, "captured_at ASC")) {
                while (cursor.moveToNext()) {
                    Snapshot snapshot = read(cursor);
                    if (snapshot != null) {
                        result.add(snapshot);
                    }
                }
            } catch (Throwable error) {
                FileLog.e("Tj message revisions query failed", error);
            }
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    public boolean hasRevisionsSync(int accountId, long dialogId, int messageId) {
        long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        return ownerUserId != 0 && revisionIndex.contains(revisionKey(ownerUserId, accountId, dialogId, messageId));
    }

    private void query(int accountId, long dialogId, int topicId, int minId, int maxId,
                       int kind, int limit, Callback<ArrayList<Snapshot>> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<Snapshot> result = new ArrayList<>();
            long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
            String selection = "owner_user_id=? AND account_id=? AND dialog_id=? AND topic_id=? AND kind=? AND message_id>=? AND message_id<=?";
            String[] args = {String.valueOf(ownerUserId), String.valueOf(accountId), String.valueOf(dialogId), String.valueOf(topicId),
                    String.valueOf(kind), String.valueOf(minId), String.valueOf(maxId)};
            try (Cursor cursor = getReadableDatabase().query(
                    "snapshots", null, selection, args, null, null, "message_id ASC",
                    String.valueOf(Math.max(1, limit)))) {
                while (cursor.moveToNext()) {
                    Snapshot snapshot = read(cursor);
                    if (snapshot != null) {
                        result.add(snapshot);
                    }
                }
            } catch (Throwable error) {
                FileLog.e("Tj deleted messages query failed", error);
            }
            AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
        });
    }

    /**
     * Drops every locally archived copy of the given messages. Used when the user deletes a
     * message and explicitly asks not to keep it on the device, so nothing survives the removal.
     */
    /**
     * Resolves the archived attachment for one message, so preserved one-time media can be
     * re-used after Telegram has dropped its own copy. Answers null when nothing is stored.
     */
    public void getArchivedMediaPath(int accountId, long dialogId, int messageId, Callback<String> callback) {
        long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        if (ownerUserId == 0 || callback == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            String result = null;
            try (Cursor cursor = getReadableDatabase().query("snapshots",
                    new String[]{"media_path"},
                    "owner_user_id=? AND account_id=? AND dialog_id=? AND message_id=? AND kind=?",
                    new String[]{String.valueOf(ownerUserId), String.valueOf(accountId),
                            String.valueOf(dialogId), String.valueOf(messageId), String.valueOf(KIND_DELETED)},
                    null, null, "captured_at DESC")) {
                while (cursor.moveToNext()) {
                    String mediaPath = cursor.getString(0);
                    if (!TextUtils.isEmpty(mediaPath) && new File(mediaPath).isFile()) {
                        result = mediaPath;
                        break;
                    }
                }
            } catch (Throwable error) {
                FileLog.e("Tj archived media lookup failed", error);
            }
            String path = result;
            AndroidUtilities.runOnUIThread(() -> callback.onResult(path));
        });
    }

    public void deleteSnapshots(int accountId, long dialogId, ArrayList<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        if (ownerUserId == 0) {
            return;
        }
        ArrayList<Integer> ids = new ArrayList<>(messageIds);
        Utilities.globalQueue.postRunnable(() -> {
            for (Integer messageId : ids) {
                if (messageId == null) {
                    continue;
                }
                String[] arguments = new String[]{String.valueOf(ownerUserId), String.valueOf(accountId),
                        String.valueOf(dialogId), String.valueOf(messageId)};
                try (Cursor cursor = getReadableDatabase().query("snapshots",
                        new String[]{"media_path"},
                        "owner_user_id=? AND account_id=? AND dialog_id=? AND message_id=?",
                        arguments, null, null, null)) {
                    while (cursor.moveToNext()) {
                        String mediaPath = cursor.getString(0);
                        if (!TextUtils.isEmpty(mediaPath)) {
                            File file = new File(mediaPath);
                            if (file.isFile() && !file.delete()) {
                                FileLog.e("Could not delete Tj archived attachment " + file.getName());
                            }
                        }
                    }
                } catch (Throwable error) {
                    FileLog.e("Tj archived attachment lookup failed", error);
                }
                try {
                    getWritableDatabase().delete("snapshots",
                            "owner_user_id=? AND account_id=? AND dialog_id=? AND message_id=?", arguments);
                } catch (Throwable error) {
                    FileLog.e("Tj archived snapshot delete failed", error);
                }
                revisionIndex.remove(revisionKey(ownerUserId, accountId, dialogId, messageId));
            }
        });
    }

    public void clear(int accountId, Callback<Boolean> callback) {
        clearOwner(UserConfig.getInstance(accountId).getClientUserId(), callback);
    }

    public void enforceQuota(int accountId) {
        long ownerUserId = UserConfig.getInstance(accountId).getClientUserId();
        if (ownerUserId != 0) {
            Utilities.globalQueue.postRunnable(() -> pruneToQuota(ownerUserId));
        }
    }

    public void clearOwner(long ownerUserId, Callback<Boolean> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean success = false;
            try {
                getWritableDatabase().delete("snapshots", "owner_user_id=?", new String[]{String.valueOf(ownerUserId)});
                deleteSavedAttachments(ownerUserId);
                String prefix = ownerUserId + ":";
                for (String key : revisionIndex) {
                    if (key.startsWith(prefix)) {
                        revisionIndex.remove(key);
                    }
                }
                success = true;
            } catch (Throwable error) {
                FileLog.e("Tj message archive clear failed", error);
            }
            boolean result = success;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onResult(result));
            }
        });
    }

    private void loadRevisionIndex() {
        try (Cursor cursor = getReadableDatabase().query(
                true, "snapshots",
                new String[]{"owner_user_id", "account_id", "dialog_id", "message_id"},
                "kind=?", new String[]{String.valueOf(KIND_EDITED)},
                null, null, null, null)) {
            while (cursor.moveToNext()) {
                revisionIndex.add(revisionKey(cursor.getLong(0), cursor.getInt(1),
                        cursor.getLong(2), cursor.getInt(3)));
            }
        } catch (Throwable error) {
            FileLog.e("Tj message revision index load failed", error);
        }
    }

    private static String revisionKey(long ownerUserId, int accountId, long dialogId, int messageId) {
        return ownerUserId + ":" + accountId + ":" + dialogId + ":" + messageId;
    }

    private static void deleteSavedAttachments() {
        deleteSavedAttachments(0);
    }

    private static void deleteSavedAttachments(long ownerUserId) {
        File root = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) {
            root = ApplicationLoader.applicationContext.getFilesDir();
        }
        File directory = new File(root, "TjGram/Saved Attachments");
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile() && (ownerUserId == 0 || file.getName().startsWith(ownerUserId + "_")) && !file.delete()) {
                FileLog.e("Could not delete Tj archived attachment " + file.getName());
            }
        }
    }

    private static Snapshot read(Cursor cursor) {
        byte[] data = cursor.getBlob(cursor.getColumnIndexOrThrow("data"));
        TLRPC.Message message = deserialize(data);
        if (message == null) {
            return null;
        }
        Snapshot snapshot = new Snapshot();
        snapshot.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        snapshot.accountId = cursor.getInt(cursor.getColumnIndexOrThrow("account_id"));
        snapshot.ownerUserId = cursor.getLong(cursor.getColumnIndexOrThrow("owner_user_id"));
        snapshot.dialogId = cursor.getLong(cursor.getColumnIndexOrThrow("dialog_id"));
        snapshot.topicId = cursor.getInt(cursor.getColumnIndexOrThrow("topic_id"));
        snapshot.messageId = cursor.getInt(cursor.getColumnIndexOrThrow("message_id"));
        snapshot.groupedId = cursor.getLong(cursor.getColumnIndexOrThrow("grouped_id"));
        snapshot.kind = cursor.getInt(cursor.getColumnIndexOrThrow("kind"));
        snapshot.capturedAt = cursor.getLong(cursor.getColumnIndexOrThrow("captured_at"));
        snapshot.editDate = cursor.getInt(cursor.getColumnIndexOrThrow("edit_date"));
        snapshot.mediaPath = cursor.getString(cursor.getColumnIndexOrThrow("media_path"));
        if (!TextUtils.isEmpty(snapshot.mediaPath) && new File(snapshot.mediaPath).exists()) {
            message.attachPath = snapshot.mediaPath;
        }
        snapshot.message = message;
        return snapshot;
    }

    private static String copyMediaIfNeeded(int accountId, long ownerUserId, TLRPC.Message message) {
        if (!shouldSaveMedia(accountId, message)) {
            return null;
        }
        try {
            File source = !TextUtils.isEmpty(message.attachPath) ? new File(message.attachPath) : null;
            if (source == null || !source.isFile()) {
                source = FileLoader.getInstance(accountId).getPathToMessage(message);
            }
            if (source == null || !source.isFile() || source.length() == 0) {
                return null;
            }
            File root = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (root == null) {
                root = ApplicationLoader.applicationContext.getFilesDir();
            }
            File directory = new File(root, "TjGram/Saved Attachments");
            if (!directory.exists() && !directory.mkdirs()) {
                return null;
            }
            String safeName = source.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            long mediaId = 0;
            TLRPC.Photo photo = MessageObject.getPhoto(message);
            TLRPC.Document document = MessageObject.getDocument(message);
            if (photo != null) {
                mediaId = photo.id;
            } else if (document != null) {
                mediaId = document.id;
            }
            File destination = new File(directory,
                    ownerUserId + "_" + MessageObject.getDialogId(message) + "_" + message.id + "_" + mediaId + "_" + safeName);
            if (destination.isFile() && destination.length() == source.length()) {
                return destination.getAbsolutePath();
            }
            File temporary = new File(directory, destination.getName() + ".tmp");
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            if (!temporary.renameTo(destination)) {
                temporary.delete();
                return null;
            }
            return destination.getAbsolutePath();
        } catch (Throwable error) {
            FileLog.e("Tj message media copy failed", error);
            return null;
        }
    }

    private void pruneToQuota(long ownerUserId) {
        long limit = TjConfig.archiveLimitGb() * 1024L * 1024L * 1024L;
        long total = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(length(data)), 0) FROM snapshots WHERE owner_user_id=?",
                new String[]{String.valueOf(ownerUserId)})) {
            if (cursor.moveToFirst()) {
                total = cursor.getLong(0);
            }
        } catch (Throwable error) {
            FileLog.e("Tj archive size query failed", error);
            return;
        }
        File root = ApplicationLoader.applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (root == null) {
            root = ApplicationLoader.applicationContext.getFilesDir();
        }
        File directory = new File(root, "TjGram/Saved Attachments");
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().startsWith(ownerUserId + "_") && !file.getName().endsWith(".tmp")) {
                    total += file.length();
                }
            }
        }
        if (total <= limit) {
            return;
        }
        try (Cursor cursor = getReadableDatabase().query("snapshots",
                new String[]{"id", "media_path", "length(data) AS data_size"},
                "owner_user_id=?", new String[]{String.valueOf(ownerUserId)},
                null, null, "captured_at ASC")) {
            while (total > limit && cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String mediaPath = cursor.getString(1);
                long reclaimed = cursor.getLong(2);
                if (!TextUtils.isEmpty(mediaPath)) {
                    File media = new File(mediaPath);
                    reclaimed += media.length();
                    if (media.isFile() && !media.delete()) {
                        FileLog.e("Could not prune Tj archived attachment " + media.getName());
                    }
                }
                getWritableDatabase().delete("snapshots", "id=?", new String[]{String.valueOf(id)});
                total -= reclaimed;
            }
        } catch (Throwable error) {
            FileLog.e("Tj archive pruning failed", error);
        }
    }

    private static boolean shouldSaveMedia(int accountId, TLRPC.Message message) {
        if (!TjConfig.saveMedia() || message == null || message.media == null) {
            return false;
        }
        long dialogId = MessageObject.getDialogId(message);
        if (dialogId >= 0 || org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId)) {
            return TjConfig.savePrivateMedia();
        }
        TLRPC.Chat chat = MessagesController.getInstance(accountId).getChat(-dialogId);
        boolean isPublic = chat != null && ChatObject.isPublic(chat);
        boolean isChannel = chat != null && ChatObject.isChannel(chat) && !chat.megagroup;
        if (isChannel) {
            return isPublic ? TjConfig.savePublicChannelMedia() : TjConfig.savePrivateChannelMedia();
        }
        return isPublic ? TjConfig.savePublicGroupMedia() : TjConfig.savePrivateGroupMedia();
    }

    private static boolean shouldArchive(int accountId, TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        if (TjConfig.saveBotMessages()) {
            return true;
        }
        if (message.via_bot_id != 0) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(accountId);
        if (message.from_id != null && message.from_id.user_id != 0) {
            TLRPC.User sender = controller.getUser(message.from_id.user_id);
            if (sender != null && sender.bot) {
                return false;
            }
        }
        long dialogId = MessageObject.getDialogId(message);
        if (dialogId > 0) {
            TLRPC.User peer = controller.getUser(dialogId);
            return peer == null || !peer.bot;
        }
        return true;
    }

    private static byte[] serializeForArchive(TLRPC.Message message) {
        byte[] original = serialize(message);
        if (original == null || TjConfig.saveFormatting() && TjConfig.saveReactions()) {
            return original;
        }
        TLRPC.Message snapshot = deserialize(original);
        if (snapshot == null) {
            return original;
        }
        if (!TjConfig.saveFormatting()) {
            snapshot.entities = new ArrayList<>();
            snapshot.flags &= ~(1 << 7);
        }
        if (!TjConfig.saveReactions()) {
            snapshot.reactions = null;
            snapshot.flags &= ~(1 << 20);
        }
        return serialize(snapshot);
    }

    private static byte[] serialize(TLRPC.Message message) {
        if (message == null) {
            return null;
        }
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
            int length = buffer.position();
            buffer.position(0);
            return buffer.readData(length, true);
        } catch (Throwable error) {
            FileLog.e("Tj message serialization failed", error);
            return null;
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
    }

    private static TLRPC.Message deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(data.length);
            buffer.writeBytes(data);
            buffer.position(0);
            return TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
        } catch (Throwable error) {
            FileLog.e("Tj message deserialization failed", error);
            return null;
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
    }

    private static String fingerprint(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format(java.util.Locale.US, "%02x", value & 0xff));
            }
            return result.toString();
        } catch (Throwable error) {
            return String.valueOf(java.util.Arrays.hashCode(data));
        }
    }
}
