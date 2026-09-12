package org.telegram.messenger.tj;

import android.content.SharedPreferences;

import org.telegram.messenger.DialogObject;
import org.telegram.tgnet.TLRPC;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Remembers viewed reaction identities independently of Telegram's read receipts. */
public final class TjReactionReadState {
    private TjReactionReadState() {}

    private static String identity(TLRPC.MessagePeerReaction reaction) {
        if (reaction == null || reaction.peer_id == null || reaction.reaction == null) return null;
        String emoji;
        if (reaction.reaction instanceof TLRPC.TL_reactionEmoji) {
            emoji = "emoji:" + ((TLRPC.TL_reactionEmoji) reaction.reaction).emoticon;
        } else if (reaction.reaction instanceof TLRPC.TL_reactionCustomEmoji) {
            emoji = "custom:" + ((TLRPC.TL_reactionCustomEmoji) reaction.reaction).document_id;
        } else {
            return null;
        }
        return DialogObject.getPeerDialogId(reaction.peer_id) + ":" + reaction.date + ":" + emoji;
    }

    private static String messageKey(long dialogId, int messageId) {
        return "seen_" + dialogId + "_" + messageId;
    }

    private static String pendingKey(long dialogId, long topicId) {
        return "pending_" + dialogId + "_" + topicId;
    }

    private static void pending(SharedPreferences prefs, long dialogId, long topicId,
                                int messageId, boolean value) {
        String key = pendingKey(dialogId, topicId);
        Set<String> ids = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
        boolean changed = value ? ids.add(String.valueOf(messageId)) : ids.remove(String.valueOf(messageId));
        if (changed) {
            if (ids.isEmpty()) prefs.edit().remove(key).apply();
            else prefs.edit().putStringSet(key, ids).apply();
        }
    }

    public static synchronized void remember(int account, long dialogId, long topicId,
                                             int messageId, TLRPC.TL_messageReactions reactions) {
        SharedPreferences prefs = TjConfig.reactionReads(account);
        if (prefs == null || dialogId == 0 || reactions == null) return;
        String key = messageKey(dialogId, messageId);
        Set<String> seen = new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
        boolean changed = false;
        boolean hadUnread = false;
        for (TLRPC.MessagePeerReaction reaction : reactions.recent_reactions) {
            String id = identity(reaction);
            if (id != null) {
                changed |= seen.add(id);
                hadUnread |= reaction.unread;
            }
        }
        if (changed) prefs.edit().putStringSet(key, seen).apply();
        if (hadUnread && TjGhostController.suppressesReads(account, dialogId)) {
            pending(prefs, dialogId, 0, messageId, true);
            if (topicId != 0) pending(prefs, dialogId, topicId, messageId, true);
        }
    }

    /** Apply before counting or displaying reactions. Only live updates can acknowledge a read. */
    public static synchronized void filter(int account, long dialogId, long topicId,
                                           int messageId, TLRPC.TL_messageReactions reactions,
                                           boolean serverUpdate) {
        SharedPreferences prefs = TjConfig.reactionReads(account);
        if (prefs == null || dialogId == 0 || reactions == null) return;
        Set<String> seen = prefs.getStringSet(messageKey(dialogId, messageId), Collections.emptySet());
        boolean serverUnread = false;
        boolean newUnread = false;
        for (TLRPC.MessagePeerReaction reaction : reactions.recent_reactions) {
            if (reaction == null || !reaction.unread) continue;
            serverUnread = true;
            String id = identity(reaction);
            if (id != null && seen.contains(id)) reaction.unread = false;
            else newUnread = true;
        }
        if (newUnread || (serverUpdate && !reactions.min && !serverUnread)) {
            pending(prefs, dialogId, 0, messageId, false);
            if (topicId != 0) pending(prefs, dialogId, topicId, messageId, false);
        }
        if (newUnread) {
            TjGhostController.onNewReactions(account, dialogId, 0);
            if (topicId != 0) TjGhostController.onNewReactions(account, dialogId, topicId);
        }
    }

    public static synchronized int filterCount(int account, long dialogId, long topicId, int serverCount) {
        SharedPreferences prefs = TjConfig.reactionReads(account);
        if (prefs == null) return serverCount;
        String key = pendingKey(dialogId, topicId);
        if (serverCount == 0) {
            prefs.edit().remove(key).apply();
            return 0;
        }
        return Math.max(0, serverCount - prefs.getStringSet(key, Collections.emptySet()).size());
    }
}
