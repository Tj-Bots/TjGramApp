package org.telegram.messenger.tj;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Screen-owned, UI-thread-only pager over Telegram media, without file downloads. */
public final class TjMediaLibrary {
    public interface Listener {
        void onChanged();
    }

    public static final class Entry {
        public final int account;
        public final long ownerId;
        public final MessageObject message;
        public final String key;
        final long storeRevision;

        public Entry(int account, long ownerId, MessageObject message) {
            this.account = account;
            this.ownerId = ownerId;
            this.message = message;
            storeRevision = TjMediaStore.getInstance().revision(account);
            key = ownerId + ":" + message.getDialogId() + ":" + message.getId();
        }

        public boolean isAccountAvailable() {
            UserConfig config = UserConfig.getInstance(account);
            return config.isClientActivated() && config.getClientUserId() == ownerId;
        }
    }

    private static final int PAGE_SIZE = 40;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final HashSet<String> keys = new HashSet<>();
    private final ArrayList<Stream> streams = new ArrayList<>();
    private final Listener listener;
    private int generation;
    private boolean closed;
    private String query = "";
    private int sourceType;
    private final boolean indexOnly;
    private long scannedCount;
    private int pendingWrites;
    private final java.util.LinkedHashMap<String, Entry> failedWrites = new java.util.LinkedHashMap<>();

    private static final class Stream {
        final int account;
        final long ownerId;
        final int type;
        final long dialogId;
        int offsetId;
        int offsetRate;
        TLRPC.InputPeer offsetPeer = new TLRPC.TL_inputPeerEmpty();
        int requestId;
        int requestGeneration;
        boolean loading;
        boolean complete;
        boolean failed;
        boolean queued;

        Stream(int account, int type, long dialogId) {
            this.account = account;
            this.type = type;
            this.dialogId = dialogId;
            ownerId = UserConfig.getInstance(account).getClientUserId();
        }
    }

    public TjMediaLibrary(Listener listener) {
        this(listener, false);
    }

    public TjMediaLibrary(Listener listener, boolean indexOnly) {
        this.listener = listener;
        this.indexOnly = indexOnly;
    }

    public long scannedCount() { return scannedCount; }

    /** Empty per-owner source sets mean all chats; source type: all/private/groups/channels. */
    public void reset(List<Integer> accounts, Map<Long, Set<Long>> sources, int sourceType, String query) {
        cancelRequests();
        closed = false;
        entries.clear();
        keys.clear();
        scannedCount = 0;
        pendingWrites = 0;
        failedWrites.clear();
        streams.clear();
        this.query = query == null ? "" : query.trim();
        this.sourceType = sourceType;
        HashSet<Integer> seen = new HashSet<>();
        for (int account : accounts) {
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT || !seen.add(account)
                    || !UserConfig.getInstance(account).isClientActivated()) continue;
            Set<Long> selected = sources.get(UserConfig.getInstance(account).getClientUserId());
            if (selected == null || selected.isEmpty()) {
                for (int type = 0; type < 5; type++) streams.add(new Stream(account, type, 0));
            } else {
                for (long source : selected) {
                    if (source == 0 || DialogObject.isEncryptedDialog(source)) continue;
                    for (int type = 0; type < 5; type++) streams.add(new Stream(account, type, source));
                }
            }
        }
        loadMore();
        listener.onChanged();
    }

    public List<Entry> snapshot() {
        ArrayList<Entry> copy = new ArrayList<>();
        for (Entry entry : entries) if (entry.isAccountAvailable()) copy.add(entry);
        return Collections.unmodifiableList(copy);
    }

    public boolean isLoading() {
        if (pendingWrites > 0) return true;
        for (Stream stream : streams) if (stream.loading || stream.queued) return true;
        return false;
    }

    public boolean hasMore() {
        if (!failedWrites.isEmpty()) return true;
        for (Stream stream : streams) if (!stream.complete) return true;
        return false;
    }

    public boolean hasError() {
        if (!failedWrites.isEmpty()) return true;
        for (Stream stream : streams) if (stream.failed) return true;
        return false;
    }

    public void loadMore() {
        if (closed || pendingWrites > 0) return;
        if (!failedWrites.isEmpty()) {
            ArrayList<Entry> retry = new ArrayList<>(failedWrites.values());
            failedWrites.clear();
            for (Entry entry : retry) if (entry.isAccountAvailable()) {
                if (entry.storeRevision != TjMediaStore.getInstance().revision(entry.account)) invalidateSource(entry.account);
                else index(entry);
            }
            listener.onChanged();
            return;
        }
        for (Stream stream : streams) {
            if (!stream.loading && !stream.complete) stream.queued = true;
        }
        pump();
    }

    private void pump() {
        // Bound queued database snapshots when storage is slower than the network.
        if (pendingWrites >= PAGE_SIZE * 3) return;
        int active = 0;
        for (Stream stream : streams) if (stream.loading) active++;
        for (Stream stream : streams) {
            if (active >= 3) break;
            if (!stream.queued || stream.loading) continue;
            stream.queued = false;
            request(stream);
            if (stream.loading) active++;
        }
    }

    private static TLRPC.MessagesFilter filter(int type) {
        switch (type) {
            case 0: return new TLRPC.TL_inputMessagesFilterPhotoVideo();
            case 1: return new TLRPC.TL_inputMessagesFilterDocument();
            case 2: return new TLRPC.TL_inputMessagesFilterMusic();
            case 3: return new TLRPC.TL_inputMessagesFilterRoundVoice();
            default: return new TLRPC.TL_inputMessagesFilterGif();
        }
    }

    private void request(Stream stream) {
        UserConfig config = UserConfig.getInstance(stream.account);
        if (!config.isClientActivated() || config.getClientUserId() != stream.ownerId) {
            stream.complete = true;
            return;
        }
        MessagesController controller = MessagesController.getInstance(stream.account);
        org.telegram.tgnet.TLObject request;
        if (stream.dialogId == 0) {
            TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
            req.q = query;
            req.limit = PAGE_SIZE;
            req.filter = filter(stream.type);
            req.users_only = sourceType == 1;
            req.groups_only = sourceType == 2;
            req.broadcasts_only = sourceType == 3;
            req.offset_id = stream.offsetId;
            req.offset_rate = stream.offsetRate;
            req.offset_peer = stream.offsetPeer;
            request = req;
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.q = query;
            req.limit = PAGE_SIZE;
            req.filter = filter(stream.type);
            req.offset_id = stream.offsetId;
            req.peer = controller.getInputPeer(stream.dialogId);
            if (req.peer == null) {
                stream.failed = true;
                return;
            }
            request = req;
        }
        stream.loading = true;
        stream.failed = false;
        int epoch = generation;
        int requestEpoch = ++stream.requestGeneration;
        long storeRevision = TjMediaStore.getInstance().revision(stream.account);
        stream.requestId = ConnectionsManager.getInstance(stream.account).sendRequest(request,
                (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (closed || epoch != generation || requestEpoch != stream.requestGeneration) return;
                    stream.loading = false;
                    stream.requestId = 0;
                    if (!config.isClientActivated() || config.getClientUserId() != stream.ownerId) {
                        stream.complete = true;
                        pump();
                        listener.onChanged();
                        return;
                    }
                    if (storeRevision != TjMediaStore.getInstance().revision(stream.account)) {
                        invalidateSource(stream.account);
                        pump();
                        listener.onChanged();
                        return;
                    }
                    if (error != null || !(response instanceof TLRPC.messages_Messages)) {
                        stream.failed = true;
                        pump();
                        listener.onChanged();
                        return;
                    }
                    TLRPC.messages_Messages page = (TLRPC.messages_Messages) response;
                    controller.putUsers(page.users, false);
                    controller.putChats(page.chats, false);
                    MessagesStorage.getInstance(stream.account).putUsersAndChats(page.users, page.chats, true, true);
                    if (page.messages.isEmpty()) {
                        stream.complete = true;
                    } else {
                        TLRPC.Message last = page.messages.get(page.messages.size() - 1);
                        long lastDialog = MessageObject.getPeerId(last.peer_id);
                        TLRPC.InputPeer nextPeer = controller.getInputPeer(lastDialog);
                        // A repeated cursor cannot make progress; expose retry instead of looping.
                        if (last.id == stream.offsetId && page.next_rate == stream.offsetRate
                                && stream.offsetPeer != null
                                && MessageObject.getPeerId(last.peer_id) == peerDialog(stream.offsetPeer)) {
                            stream.failed = true;
                        } else if (nextPeer == null) {
                            stream.failed = true;
                        } else {
                            stream.offsetId = last.id;
                            stream.offsetRate = page.next_rate;
                            stream.offsetPeer = nextPeer;
                        }
                        for (TLRPC.Message message : page.messages) {
                            TLRPC.MessageMedia media = MessageObject.getMedia(message);
                            if (!(message instanceof TLRPC.TL_message) || media == null
                                    || media.ttl_seconds != 0 || message.ttl_period != 0
                                    || DialogObject.isEncryptedDialog(MessageObject.getPeerId(message.peer_id))
                                    || !(media instanceof TLRPC.TL_messageMediaPhoto
                                    || media instanceof TLRPC.TL_messageMediaDocument)) continue;
                            MessageObject object = new MessageObject(stream.account, message, false, !indexOnly);
                            Entry entry = new Entry(stream.account, stream.ownerId, object);
                            index(entry);
                            scannedCount++;
                            if (!indexOnly) {
                                if (keys.add(entry.key)) entries.add(entry);
                            }
                        }
                        entries.sort((a, b) -> {
                            int date = Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date);
                            return date != 0 ? date : a.key.compareTo(b.key);
                        });
                    }
                    pump();
                    listener.onChanged();
                }));
    }

    private void index(Entry entry) {
        int epoch = generation;
        pendingWrites++;
        TjMediaStore.getInstance().index(entry.message, entry.storeRevision, success -> {
            if (closed || epoch != generation) return;
            pendingWrites--;
            if (entry.storeRevision != TjMediaStore.getInstance().revision(entry.account)) invalidateSource(entry.account);
            else if (!success && entry.isAccountAvailable()) failedWrites.put(entry.key, entry);
            if (pendingWrites == 0) {
                pump();
                listener.onChanged();
            }
        });
    }

    /** A retry after deletion/clear must fetch again, never replay an old message snapshot. */
    private void invalidateSource(int account) {
        for (Stream stream : streams) if (stream.account == account) {
            stream.requestGeneration++;
            if (stream.requestId != 0) ConnectionsManager.getInstance(account).cancelRequest(stream.requestId, true);
            stream.requestId = 0;
            stream.loading = stream.queued = stream.complete = false;
            stream.failed = true;
            stream.offsetId = stream.offsetRate = 0;
            stream.offsetPeer = new TLRPC.TL_inputPeerEmpty();
        }
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i).account == account) {
            keys.remove(entries.remove(i).key);
        }
        java.util.Iterator<Entry> failed = failedWrites.values().iterator();
        while (failed.hasNext()) if (failed.next().account == account) failed.remove();
    }

    private static long peerDialog(TLRPC.InputPeer peer) {
        if (peer.user_id != 0) return peer.user_id;
        return peer.channel_id != 0 ? -peer.channel_id : -peer.chat_id;
    }

    private void cancelRequests() {
        generation++;
        for (Stream stream : streams) {
            if (stream.requestId != 0) {
                ConnectionsManager.getInstance(stream.account).cancelRequest(stream.requestId, true);
            }
        }
    }

    public void close() {
        closed = true;
        cancelRequests();
        pendingWrites = 0;
        failedWrites.clear();
        streams.clear();
        entries.clear();
        keys.clear();
    }
}
