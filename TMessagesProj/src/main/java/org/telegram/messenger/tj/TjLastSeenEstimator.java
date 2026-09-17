package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps the newest locally observed activity time for users whose precise status is hidden. */
public final class TjLastSeenEstimator extends SQLiteOpenHelper {
    public static final int SOURCE_MESSAGE = 1;
    public static final int SOURCE_READ = 2;
    public static final int SOURCE_TYPING = 3;
    public static final int SOURCE_STATUS = 4;
    public static final int SOURCE_EDIT = 5;
    public static final int SOURCE_REACTION = 6;
    public static final int SOURCE_STORY_VIEW = 7;

    private static final String DATABASE_NAME = "tj_last_seen.db";
    private static final int DATABASE_VERSION = 1;
    private static volatile TjLastSeenEstimator instance;

    /**
     * How long observations are allowed to pile up before they are written, and how often the
     * interface is told that somebody's time changed.
     *
     * Both are what make this survive coming back to a backlog. Every message, read receipt,
     * typing notice, edit, reaction and status change in that backlog passes through here on the
     * main thread; writing one row and posting one full interface refresh each was thousands of
     * database round trips and thousands of redraws queued ahead of the first frame, which is a
     * frozen app and then "TjGram has stopped".
     */
    private static final int FLUSH_DELAY_MS = 2000;
    private static final int NOTIFY_DELAY_MS = 1000;

    private static final DispatchQueue queue = new DispatchQueue("tj-last-seen");

    private final ConcurrentHashMap<String, Integer> lastSeen = new ConcurrentHashMap<>();
    /** Observed since the last write, as owner:user -> {date, source}. */
    private final ConcurrentHashMap<String, int[]> unsaved = new ConcurrentHashMap<>();
    private final Set<Long> loadedOwners = ConcurrentHashMap.newKeySet();
    private final Set<Long> loadingOwners = ConcurrentHashMap.newKeySet();
    private final Set<Integer> notifyScheduled = ConcurrentHashMap.newKeySet();
    private volatile boolean flushScheduled;

    private TjLastSeenEstimator(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static TjLastSeenEstimator getInstance() {
        TjLastSeenEstimator local = instance;
        if (local == null) {
            synchronized (TjLastSeenEstimator.class) {
                local = instance;
                if (local == null) {
                    local = instance = new TjLastSeenEstimator(ApplicationLoader.applicationContext);
                }
            }
        }
        return local;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE presence (" +
                "owner_user_id INTEGER NOT NULL," +
                "user_id INTEGER NOT NULL," +
                "last_seen INTEGER NOT NULL," +
                "source INTEGER NOT NULL," +
                "PRIMARY KEY(owner_user_id, user_id))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no migrations.
    }

    public int getLastSeen(int account, long userId) {
        if (!TjConfig.estimatedLastSeen() || userId <= 0) {
            return 0;
        }
        long ownerId = ownerId(account);
        if (ownerId == 0 || ownerId == userId) {
            return 0;
        }
        ensureLoaded(account, ownerId);
        Integer value = lastSeen.get(key(ownerId, userId));
        return value == null ? 0 : value;
    }

    public void record(int account, long userId, int date, int source) {
        if (!TjConfig.estimatedLastSeen() || userId <= 0 || date <= 0) {
            return;
        }
        long ownerId = ownerId(account);
        if (ownerId == 0 || ownerId == userId) {
            return;
        }
        TLRPC.User user = MessagesController.getInstance(account).getUser(userId);
        if (user != null && (user.bot || UserObject.isDeleted(user))) {
            return;
        }
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        if (date > now) {
            date = now;
        }

        String key = key(ownerId, userId);
        Integer previous = lastSeen.get(key);
        while (previous == null || date > previous) {
            if (previous == null) {
                if (lastSeen.putIfAbsent(key, date) == null) {
                    break;
                }
            } else if (lastSeen.replace(key, previous, date)) {
                break;
            }
            previous = lastSeen.get(key);
        }
        if (previous != null && date <= previous) {
            return;
        }

        unsaved.put(key, new int[]{date, source});
        scheduleNotify(account);
        scheduleFlush();
    }

    /** One interface refresh per account per second, however many observations arrive in it. */
    private void scheduleNotify(int account) {
        if (!notifyScheduled.add(account)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            notifyScheduled.remove(account);
            NotificationCenter.getInstance(account).postNotificationName(
                    NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_STATUS);
        }, NOTIFY_DELAY_MS);
    }

    private void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        queue.postRunnable(this::flush, FLUSH_DELAY_MS);
    }

    /** Everything observed since the last write, in one transaction. */
    private void flush() {
        flushScheduled = false;
        if (unsaved.isEmpty()) {
            return;
        }
        SQLiteDatabase db;
        try {
            db = getWritableDatabase();
        } catch (Throwable error) {
            FileLog.e("Tj estimated last seen write failed", error);
            return;
        }
        db.beginTransaction();
        try {
            for (String key : new java.util.ArrayList<>(unsaved.keySet())) {
                int[] observation = unsaved.remove(key);
                if (observation == null) {
                    continue;
                }
                int separator = key.indexOf(':');
                if (separator <= 0) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("owner_user_id", Long.parseLong(key.substring(0, separator)));
                values.put("user_id", Long.parseLong(key.substring(separator + 1)));
                values.put("last_seen", observation[0]);
                values.put("source", observation[1]);
                // The row is replaced wholesale, and the value here is the newest one seen, so
                // there is nothing to read back first.
                db.insertWithOnConflict("presence", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } catch (Throwable error) {
            FileLog.e("Tj estimated last seen write failed", error);
        } finally {
            try { db.endTransaction(); } catch (Throwable ignored) { }
        }
    }

    private void ensureLoaded(int account, long ownerId) {
        if (loadedOwners.contains(ownerId) || !loadingOwners.add(ownerId)) {
            return;
        }
        queue.postRunnable(() -> {
            try (Cursor cursor = getReadableDatabase().query(
                    "presence", new String[]{"user_id", "last_seen"}, "owner_user_id=?",
                    new String[]{String.valueOf(ownerId)}, null, null, null)) {
                while (cursor.moveToNext()) {
                    long userId = cursor.getLong(0);
                    int date = cursor.getInt(1);
                    String key = key(ownerId, userId);
                    Integer current = lastSeen.get(key);
                    if (current == null || date > current) {
                        lastSeen.put(key, date);
                    }
                }
            } catch (Throwable error) {
                FileLog.e("Tj estimated last seen load failed", error);
            } finally {
                loadingOwners.remove(ownerId);
                loadedOwners.add(ownerId);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                        .postNotificationName(NotificationCenter.updateInterfaces,
                                MessagesController.UPDATE_MASK_STATUS));
            }
        });
    }

    private static long ownerId(int account) {
        return account >= 0 && account < UserConfig.MAX_ACCOUNT_COUNT
                ? UserConfig.getInstance(account).getClientUserId() : 0;
    }

    private static String key(long ownerId, long userId) {
        return ownerId + ":" + userId;
    }
}
