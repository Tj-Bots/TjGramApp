package org.telegram.messenger.tj;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_account;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Network-level policy for presence, typing and read receipts. */
public final class TjGhostController {
    private static final ConcurrentLinkedQueue<ReadAllowance> allowedReadRequests = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<String, Long> automaticallyScheduledDialogs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ReadContext> readContexts = new ConcurrentHashMap<>();
    private static final long AUTOMATIC_SCHEDULE_WINDOW_MS = 30_000L;

    private TjGhostController() {
    }

    private static final class ReadAllowance {
        final int account;
        final long dialogId;
        final int messageId;
        final long expiresAt;

        ReadAllowance(int account, long dialogId, int messageId) {
            this.account = account;
            this.dialogId = dialogId;
            this.messageId = messageId;
            this.expiresAt = android.os.SystemClock.elapsedRealtime() + 10_000L;
        }
    }

    private static final class ReadContext {
        final long dialogId;
        final long expiresAt;

        ReadContext(long dialogId) {
            this.dialogId = dialogId;
            expiresAt = android.os.SystemClock.elapsedRealtime() + 30_000L;
        }
    }

    public static void registerReadContext(int account, long dialogId, int messageId) {
        if (dialogId != 0 && messageId > 0) {
            readContexts.put(account + ":" + messageId, new ReadContext(dialogId));
        }
    }

    public static void clearSession(int account) {
        for (ReadAllowance allowance : allowedReadRequests) {
            if (allowance.account == account) allowedReadRequests.remove(allowance);
        }
        String prefix = account + ":";
        automaticallyScheduledDialogs.keySet().removeIf(key -> key.startsWith(prefix));
        readContexts.keySet().removeIf(key -> key.startsWith(prefix));
    }

    public static void allowReadRequest(int account, long dialogId, int messageId) {
        allowedReadRequests.add(new ReadAllowance(account, dialogId, messageId));
    }

    public static void clearReadExceptions() {
        allowedReadRequests.clear();
    }

    public static int getAutomaticScheduleDate(int account, long dialogId, int scheduleDate,
                                               boolean isDocument, boolean isPhoto,
                                               boolean isRetry, boolean isTemplateOrQuickReply) {
        if (!TjConfig.scheduleMessages() || scheduleDate != 0 || isRetry
                || isTemplateOrQuickReply || dialogId == 0
                || org.telegram.messenger.DialogObject.isEncryptedDialog(dialogId)) {
            return scheduleDate;
        }
        int delaySeconds = isDocument ? 25 : isPhoto ? 20 : 11;
        automaticallyScheduledDialogs.put(account + ":" + dialogId,
                android.os.SystemClock.elapsedRealtime() + AUTOMATIC_SCHEDULE_WINDOW_MS);
        return ConnectionsManager.getInstance(account).getCurrentTime() + delaySeconds;
    }

    public static boolean wasAutomaticallyScheduled(int account, long dialogId) {
        String key = account + ":" + dialogId;
        Long expiresAt = automaticallyScheduledDialogs.get(key);
        if (expiresAt == null) {
            return false;
        }
        if (expiresAt < android.os.SystemClock.elapsedRealtime()) {
            automaticallyScheduledDialogs.remove(key, expiresAt);
            return false;
        }
        return true;
    }

    public static boolean shouldDropTyping(int account, TLObject request) {
        if (!(request instanceof TLRPC.TL_messages_setTyping)
                && !(request instanceof TLRPC.TL_messages_setEncryptedTyping)) {
            return false;
        }
        long dialogId = 0;
        if (request instanceof TLRPC.TL_messages_setTyping) {
            dialogId = getDialogId(((TLRPC.TL_messages_setTyping) request).peer);
        } else {
            dialogId = org.telegram.messenger.DialogObject.makeEncryptedDialogId(
                    ((TLRPC.TL_messages_setEncryptedTyping) request).peer.chat_id);
        }
        return TjConfig.hideTyping(account, dialogId);
    }

    public static boolean shouldForceOffline(TLObject request) {
        return TjConfig.hideOnline() && request instanceof TL_account.updateStatus;
    }

    public static void sendOfflineStatusForActiveAccounts() {
        if (!TjConfig.ghostEnabled() || !TjConfig.forceOffline()) {
            return;
        }
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            TL_account.updateStatus request = new TL_account.updateStatus();
            request.offline = true;
            ConnectionsManager.getInstance(account).sendRequest(request, null);
        }
    }

    /**
     * True when a read receipt for this dialog would be suppressed. Ghost keeps the server
     * unaware, so anything that relies on the server confirming a read has to be tracked locally.
     */
    public static boolean suppressesReads(int account, long dialogId) {
        return TjConfig.hideReads(account, dialogId);
    }

    /**
     * Records that the user viewed this dialog's reactions. Without it the suppressed
     * readReactions call leaves the server counting them as unread, and the next dialog sync
     * brings the badge straight back even though the user has seen every one of them.
     */
    public static void onReactionsRead(int account, long dialogId, long topicId) {
        TjConfig.setGhostReactionsReadLocally(account, dialogId, topicId, suppressesReads(account, dialogId));
    }

    /** Called when a genuinely new reaction arrives, so the badge is allowed back. */
    public static void onNewReactions(int account, long dialogId, long topicId) {
        TjConfig.setGhostReactionsReadLocally(account, dialogId, topicId, false);
    }

    /** Server-reported unread reaction counts are stale for dialogs the user already viewed. */
    public static int filterUnreadReactionsCount(int account, long dialogId, long topicId, int count) {
        if (count > 0 && TjConfig.ghostReactionsReadLocally(account, dialogId, topicId)) {
            return 0;
        }
        return TjReactionReadState.filterCount(account, dialogId, topicId, count);
    }

    public static boolean shouldDropRead(int account, TLObject request) {
        if (!isReadRequest(request)) {
            return false;
        }
        long requestDialogId = getReadDialogId(account, request);
        if (!TjConfig.hideReads(account, requestDialogId)) {
            return false;
        }
        long now = android.os.SystemClock.elapsedRealtime();
        Iterator<ReadAllowance> iterator = allowedReadRequests.iterator();
        while (iterator.hasNext()) {
            ReadAllowance allowance = iterator.next();
            if (allowance.expiresAt < now) {
                allowedReadRequests.remove(allowance);
                continue;
            }
            if (allowance.account == account
                    && (requestDialogId != 0 && requestDialogId == allowance.dialogId
                    || containsReadMessage(request, allowance.messageId))) {
                allowedReadRequests.remove(allowance);
                return false;
            }
        }
        return true;
    }

    private static long getReadDialogId(int account, TLObject request) {
        if (request instanceof TLRPC.TL_messages_readHistory) {
            return getDialogId(((TLRPC.TL_messages_readHistory) request).peer);
        } else if (request instanceof TLRPC.TL_channels_readHistory) {
            return -((TLRPC.TL_channels_readHistory) request).channel.channel_id;
        } else if (request instanceof TLRPC.TL_messages_readEncryptedHistory) {
            return org.telegram.messenger.DialogObject.makeEncryptedDialogId(
                    ((TLRPC.TL_messages_readEncryptedHistory) request).peer.chat_id);
        } else if (request instanceof TLRPC.TL_messages_readDiscussion) {
            return getDialogId(((TLRPC.TL_messages_readDiscussion) request).peer);
        } else if (request instanceof TLRPC.TL_messages_readSavedHistory) {
            return getDialogId(((TLRPC.TL_messages_readSavedHistory) request).parent_peer);
        } else if (request instanceof TLRPC.TL_channels_readMessageContents) {
            return -((TLRPC.TL_channels_readMessageContents) request).channel.channel_id;
        } else if (request instanceof TLRPC.TL_messages_readMentions) {
            return getDialogId(((TLRPC.TL_messages_readMentions) request).peer);
        } else if (request instanceof TLRPC.TL_messages_readReactions) {
            return getDialogId(((TLRPC.TL_messages_readReactions) request).peer);
        } else if (request instanceof TLRPC.TL_messages_readMessageContents) {
            for (Integer id : ((TLRPC.TL_messages_readMessageContents) request).id) {
                ReadContext context = readContexts.remove(account + ":" + id);
                if (context != null && context.expiresAt >= android.os.SystemClock.elapsedRealtime()) {
                    return context.dialogId;
                }
            }
        }
        return 0;
    }

    private static boolean containsReadMessage(TLObject request, int messageId) {
        if (messageId <= 0) return false;
        if (request instanceof TLRPC.TL_messages_readMessageContents) {
            return ((TLRPC.TL_messages_readMessageContents) request).id.contains(messageId);
        } else if (request instanceof TLRPC.TL_channels_readMessageContents) {
            return ((TLRPC.TL_channels_readMessageContents) request).id.contains(messageId);
        }
        return false;
    }

    public static boolean isReadRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_channels_readHistory
                || request instanceof TLRPC.TL_messages_readEncryptedHistory
                || request instanceof TLRPC.TL_messages_readDiscussion
                || request instanceof TLRPC.TL_messages_readSavedHistory
                || request instanceof TLRPC.TL_messages_readMessageContents
                || request instanceof TLRPC.TL_channels_readMessageContents
                || request instanceof TLRPC.TL_messages_readMentions
                || request instanceof TLRPC.TL_messages_readReactions;
    }

    public static TLObject createSuppressedReadResponse(TLObject request) {
        if (request instanceof TLRPC.TL_messages_readHistory
                || request instanceof TLRPC.TL_messages_readMessageContents) {
            TLRPC.TL_messages_affectedMessages response = new TLRPC.TL_messages_affectedMessages();
            response.pts = -1;
            response.pts_count = 0;
            return response;
        }
        if (request instanceof TLRPC.TL_messages_readMentions
                || request instanceof TLRPC.TL_messages_readReactions) {
            TLRPC.TL_messages_affectedHistory response = new TLRPC.TL_messages_affectedHistory();
            response.pts = -1;
            response.pts_count = 0;
            response.offset = 0;
            return response;
        }
        return new TLRPC.TL_boolTrue();
    }

    public static TLRPC.InputPeer getPeerFromSendRequest(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            return ((TLRPC.TL_messages_sendMessage) request).peer;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            return ((TLRPC.TL_messages_sendMedia) request).peer;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            return ((TLRPC.TL_messages_sendMultiMedia) request).peer;
        }
        return null;
    }

    /** Applies Ghost delivery options immediately before request serialization. */
    public static void applySendPolicy(TLObject request) {
        if (!TjConfig.sendWithoutSound()) return;
        if (request instanceof TLRPC.TL_messages_sendMessage) {
            ((TLRPC.TL_messages_sendMessage) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_sendMedia) {
            ((TLRPC.TL_messages_sendMedia) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            ((TLRPC.TL_messages_sendMultiMedia) request).silent = true;
        } else if (request instanceof TLRPC.TL_messages_forwardMessages) {
            ((TLRPC.TL_messages_forwardMessages) request).silent = true;
        }
    }

    public static long getDialogId(TLRPC.InputPeer peer) {
        if (peer == null) {
            return 0;
        } else if (peer.channel_id != 0) {
            return -peer.channel_id;
        } else if (peer.chat_id != 0) {
            return -peer.chat_id;
        }
        return peer.user_id;
    }

    public static void sendReadReceipt(int account, TLRPC.InputPeer peer, int maxId) {
        if (peer == null || maxId <= 0) {
            return;
        }
        TLObject request;
        if (peer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.TL_channels_readHistory read = new TLRPC.TL_channels_readHistory();
            read.channel = MessagesController.getInstance(account).getInputChannel(peer.channel_id);
            read.max_id = maxId;
            request = read;
        } else {
            TLRPC.TL_messages_readHistory read = new TLRPC.TL_messages_readHistory();
            read.peer = peer;
            read.max_id = maxId;
            request = read;
        }
        allowReadRequest(account, getDialogId(peer), maxId);
        ConnectionsManager.getInstance(account).sendRequest(request, (response, error) -> {
            if (error == null && response instanceof TLRPC.TL_messages_affectedMessages) {
                TLRPC.TL_messages_affectedMessages affected = (TLRPC.TL_messages_affectedMessages) response;
                MessagesController.getInstance(account).processNewDifferenceParams(-1, affected.pts, -1, affected.pts_count);
            }
        });
    }
}
