package org.telegram.messenger.tj;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks user-initiated removals so they can disappear normally from the chat UI. */
public final class TjDeletionPolicy {
    // The server can take a while to echo a deletion back (poor connectivity, resumed
    // session). Until the echo arrives the marker is the only thing telling the archive
    // that the user asked for a complete removal, so it has to outlive a slow round trip.
    private static final long EXPIRY_MS = 30 * 60_000L;
    private static final ConcurrentHashMap<String, Long> localRemovals = new ConcurrentHashMap<>();

    private TjDeletionPolicy() {
    }

    public static void markLocalRemoval(int account, long dialogId, ArrayList<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return;
        }
        long expiresAt = android.os.SystemClock.elapsedRealtime() + EXPIRY_MS;
        for (Integer messageId : messageIds) {
            if (messageId != null) {
                localRemovals.put(key(account, dialogId, messageId), expiresAt);
            }
        }
    }

    public static boolean isLocalRemoval(int account, long dialogId, int messageId) {
        String key = key(account, dialogId, messageId);
        Long expiresAt = localRemovals.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < android.os.SystemClock.elapsedRealtime()) {
            localRemovals.remove(key, expiresAt);
            return false;
        }
        return true;
    }

    private static String key(int account, long dialogId, int messageId) {
        return account + ":" + dialogId + ":" + messageId;
    }
}
