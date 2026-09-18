package org.telegram.messenger.tj;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;

/**
 * What happened to this account, written down where the person can see it.
 *
 * Telegram tells you almost none of this: rights are taken away, a device signs in, somebody
 * restricts you in a group, and the first you know of it is that something stopped working. Every
 * entry here is built from an update the server already sent this device - nothing is asked for,
 * nothing is inferred, and nothing leaves the phone.
 *
 * One thing is deliberately missing: being blocked by someone. Telegram does not report it, and
 * guessing it from what stops working would mean reporting something the other person chose not
 * to tell you.
 */
public final class TjAccountLog extends SQLiteOpenHelper {

    public static final int TYPE_ADMIN_RIGHTS = 1;
    public static final int TYPE_RESTRICTED = 2;
    public static final int TYPE_MEMBERSHIP = 3;
    public static final int TYPE_NEW_DEVICE = 4;

    private static final String DATABASE_NAME = "tj_account_log.db";
    private static final int DATABASE_VERSION = 1;
    /** Enough to look back over, small enough that the list never becomes the problem. */
    private static final int LIMIT = 500;

    private static final DispatchQueue queue = new DispatchQueue("tj-account-log");
    private static volatile TjAccountLog instance;

    public interface Callback<T> { void run(T value); }

    /** One thing that happened, as it was recorded. */
    public static final class Entry {
        public long id;
        public int account, type, date;
        public JSONObject payload;

        public String text(String key) {
            return payload == null ? "" : payload.optString(key, "");
        }

        public long number(String key) {
            return payload == null ? 0 : payload.optLong(key, 0);
        }

        public ArrayList<String> list(String key) {
            ArrayList<String> result = new ArrayList<>();
            JSONArray array = payload == null ? null : payload.optJSONArray(key);
            for (int a = 0; array != null && a < array.length(); a++) {
                String value = array.optString(a, "");
                if (!value.isEmpty()) result.add(value);
            }
            return result;
        }
    }

    private TjAccountLog() {
        super(ApplicationLoader.applicationContext, DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static TjAccountLog getInstance() {
        TjAccountLog local = instance;
        if (local == null) {
            synchronized (TjAccountLog.class) {
                local = instance;
                if (local == null) local = instance = new TjAccountLog();
            }
        }
        return local;
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "owner INTEGER NOT NULL," +
                "account INTEGER NOT NULL," +
                "type INTEGER NOT NULL," +
                "date INTEGER NOT NULL," +
                "payload TEXT NOT NULL)");
        db.execSQL("CREATE INDEX events_owner ON events(owner, date DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Version 1 has no migrations.
    }

    /** True while the log is on at all and this kind of event is one the person asked to see. */
    public static boolean wants(int type) {
        return TjConfig.accountLog() && TjConfig.accountLogType(type);
    }

    public void record(int account, int type, int date, JSONObject payload) {
        if (!wants(type) || payload == null) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) return;
        final int when = date > 0 ? date : (int) (System.currentTimeMillis() / 1000L);
        final String text = payload.toString();
        queue.postRunnable(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("owner", owner);
                values.put("account", account);
                values.put("type", type);
                values.put("date", when);
                values.put("payload", text);
                SQLiteDatabase db = getWritableDatabase();
                db.insertOrThrow("events", null, values);
                db.execSQL("DELETE FROM events WHERE owner=? AND id NOT IN "
                        + "(SELECT id FROM events WHERE owner=? ORDER BY date DESC, id DESC LIMIT " + LIMIT + ")",
                        new Object[]{owner, owner});
            } catch (Throwable error) {
                FileLog.e("Tj account log write failed", error);
            }
            AndroidUtilities.runOnUIThread(() -> { notifyChanged(); refresh(account); });
        });
    }

    /**
     * Rebuilds what the chat list row says and how many entries have not been looked at, then asks
     * the list to draw itself again. The row holds nothing of its own; this is where it comes from.
     */
    public void refresh(int account) {
        load(account, entries -> {
            int lastRead = TjConfig.accountLogRead();
            int unread = 0;
            for (Entry entry : entries) if (entry.date > lastRead) unread++;
            if (entries.isEmpty()) {
                TjAccountLogDialog.setPreview("", 0, 0);
            } else {
                Entry newest = entries.get(0);
                TjAccountLogDialog.setPreview(TjAccountEvents.title(newest), newest.date, unread);
            }
            org.telegram.messenger.NotificationCenter.getInstance(account)
                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        });
    }

    /** Everything up to now has been looked at. */
    public void markRead(int account) {
        load(account, entries -> {
            if (!entries.isEmpty()) TjConfig.setAccountLogRead(entries.get(0).date);
            refresh(account);
        });
    }

    public void load(int account, Callback<ArrayList<Entry>> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            ArrayList<Entry> result = new ArrayList<>();
            if (owner != 0) {
                try (Cursor cursor = getReadableDatabase().query("events",
                        new String[]{"id", "account", "type", "date", "payload"},
                        "owner=?", new String[]{Long.toString(owner)},
                        null, null, "date DESC, id DESC", Integer.toString(LIMIT))) {
                    while (cursor.moveToNext()) {
                        Entry entry = new Entry();
                        entry.id = cursor.getLong(0);
                        entry.account = cursor.getInt(1);
                        entry.type = cursor.getInt(2);
                        entry.date = cursor.getInt(3);
                        try { entry.payload = new JSONObject(cursor.getString(4)); }
                        catch (Throwable ignored) { continue; }
                        result.add(entry);
                    }
                } catch (Throwable error) {
                    FileLog.e("Tj account log read failed", error);
                }
            }
            AndroidUtilities.runOnUIThread(() -> callback.run(result));
        });
    }

    public void clear(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        if (owner == 0) return;
        queue.postRunnable(() -> {
            try {
                getWritableDatabase().delete("events", "owner=?", new String[]{Long.toString(owner)});
            } catch (Throwable error) {
                FileLog.e("Tj account log clear failed", error);
            }
            AndroidUtilities.runOnUIThread(() -> { notifyChanged(); refresh(account); });
        });
    }

    private static final ArrayList<Runnable> listeners = new ArrayList<>();

    public static void addListener(Runnable listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static void notifyChanged() {
        for (Runnable listener : new ArrayList<>(listeners)) listener.run();
    }
}
