package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
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

    private final ConcurrentHashMap<String, Integer> lastSeen = new ConcurrentHashMap<>();
    private final Set<Long> loadedOwners = ConcurrentHashMap.newKeySet();
    private final Set<Long> loadingOwners = ConcurrentHashMap.newKeySet();

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

        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(account)
                .postNotificationName(NotificationCenter.updateInterfaces,
                        MessagesController.UPDATE_MASK_STATUS));

        final int storedDate = date;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                try (Cursor cursor = getReadableDatabase().query(
                        "presence", new String[]{"last_seen"},
                        "owner_user_id=? AND user_id=?",
                        new String[]{String.valueOf(ownerId), String.valueOf(userId)},
                        null, null, null, "1")) {
                    if (cursor.moveToFirst() && cursor.getInt(0) >= storedDate) {
                        return;
                    }
                }
                ContentValues values = new ContentValues();
                values.put("owner_user_id", ownerId);
                values.put("user_id", userId);
                values.put("last_seen", storedDate);
                values.put("source", source);
                getWritableDatabase().insertWithOnConflict(
                        "presence", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Throwable error) {
                FileLog.e("Tj estimated last seen write failed", error);
            }
        });
    }

    private void ensureLoaded(int account, long ownerId) {
        if (loadedOwners.contains(ownerId) || !loadingOwners.add(ownerId)) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
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
