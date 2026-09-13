package org.telegram.messenger.tj;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class TjOfflineAccounts {
    private static final String PREFS = "tj_offline_accounts";
    private static final String KEY_OWNER_IDS = "owner_ids";
    private static final Object lock = new Object();
    private static final boolean[] retaining = new boolean[UserConfig.MAX_ACCOUNT_COUNT];

    private TjOfflineAccounts() {
    }

    public static void capture(int account, Utilities.Callback<Boolean> callback) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        boolean success = owner != 0;
        if (success) {
            addOwner(owner);
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (callback != null) {
                callback.run(success);
            }
        });
    }

    public static void setRetaining(int account, boolean keepLocal) {
        if (account < 0 || account >= retaining.length) {
            return;
        }
        synchronized (lock) {
            retaining[account] = keepLocal;
        }
    }

    public static boolean isRetaining(int account) {
        if (account < 0 || account >= retaining.length) {
            return false;
        }
        synchronized (lock) {
            boolean keep = retaining[account];
            retaining[account] = false;
            return keep;
        }
    }

    public static boolean hasArchives() {
        return !getPrefs().getStringSet(KEY_OWNER_IDS, Collections.emptySet()).isEmpty();
    }

    private static void addOwner(long ownerUserId) {
        SharedPreferences prefs = getPrefs();
        Set<String> stored = prefs.getStringSet(KEY_OWNER_IDS, Collections.emptySet());
        HashSet<String> updated = new HashSet<>(stored);
        updated.add(Long.toString(ownerUserId));
        prefs.edit().putStringSet(KEY_OWNER_IDS, updated).apply();
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
