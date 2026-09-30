package org.telegram.messenger.tj;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * More favourite stickers than Telegram keeps.
 *
 * The server holds a fixed number of favourites (5, or 10 with Premium) and forgets the oldest when
 * one more is added. TjGram lets the list be longer: the newest ones are still Telegram's own and
 * sync to other apps, and the ones past Telegram's limit are kept on this device, per account, and
 * put back after every reload from the server.
 */
public final class TjFavedStickers {

    public static final int[] CHOICES = {5, 10, 15, 20};

    private TjFavedStickers() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("tjfavedstickers", Context.MODE_PRIVATE);
    }

    /** The count chosen in TjGram settings. */
    public static int chosen() {
        return prefs().getInt("limit", 5);
    }

    public static void setChosen(int value) {
        prefs().edit().putInt("limit", value).apply();
    }

    public static int serverLimit(int account) {
        return MessagesController.getInstance(account).maxFaveStickersCount;
    }

    /** How many favourites the app shows: never fewer than Telegram's own limit. */
    public static int limit(int account) {
        return Math.max(serverLimit(account), chosen());
    }

    private static String key(int account) {
        return "extras_" + UserConfig.getInstance(account).getClientUserId();
    }

    /**
     * Keeps the whole list as the app shows it. Which of them the server still holds is not known
     * here - removing one of Telegram's frees a place there without pulling one back - so the list is
     * kept in full and whatever the server no longer returns is put back from it.
     */
    public static void saveExtras(int account, ArrayList<TLRPC.Document> list) {
        try {
            int from = 0;
            int to = Math.min(list.size(), limit(account));
            SerializedData data = new SerializedData();
            data.writeInt32(Math.max(0, to - from));
            for (int i = from; i < to; i++) {
                list.get(i).serializeToStream(data);
            }
            prefs().edit().putString(key(account), Base64.encodeToString(data.toByteArray(), Base64.NO_WRAP)).apply();
            data.cleanup();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static ArrayList<TLRPC.Document> loadExtras(int account) {
        ArrayList<TLRPC.Document> result = new ArrayList<>();
        String stored = prefs().getString(key(account), null);
        if (stored == null) return result;
        try {
            SerializedData data = new SerializedData(Base64.decode(stored, Base64.NO_WRAP));
            int count = data.readInt32(false);
            for (int i = 0; i < count; i++) {
                TLRPC.Document document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
                if (document != null) result.add(document);
            }
            data.cleanup();
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    /** Telegram's list followed by the ones kept here, without repeats and up to the chosen count. */
    public static ArrayList<TLRPC.Document> merge(int account, ArrayList<TLRPC.Document> fromServer) {
        if (fromServer == null || chosen() <= serverLimit(account)) return fromServer;
        ArrayList<TLRPC.Document> result = new ArrayList<>(fromServer);
        HashSet<Long> seen = new HashSet<>();
        for (TLRPC.Document document : fromServer) seen.add(document.id);
        int limit = limit(account);
        for (TLRPC.Document document : loadExtras(account)) {
            if (result.size() >= limit) break;
            if (seen.add(document.id)) result.add(document);
        }
        return result;
    }
}
