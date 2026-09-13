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

/** UI-thread-only pager; index-only scans persist their position independently of views. */
public final class TjMediaLibrary {
    public interface Listener {
        void onChanged();
    }

    public static final class Entry {
        public final int account;
        public final long ownerId;
        public final MessageObject message;
        public final String key;
        public final long storeRevision;

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
    private static final int VISIBLE_LIMIT = 200;
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
    private final ArrayList<PendingPage> failedWrites = new ArrayList<>();
    // UI-thread-only, shared by preview and scan instances; fragment recreation must not bypass FLOOD_WAIT.
    private static final java.util.HashMap<Long, Long> accountRetryAt = new java.util.HashMap<>();
    private final Runnable previewRetry = this::retryPreview;
    private boolean retriesPaused;

    /** Keep a network page intact so retries can carry its own durable cursor. */
    private static final class PendingPage {
        final Stream stream;
        final ArrayList<Entry> entries;
        final long revision;
        final TjMediaScanState nextPosition;

        PendingPage(Stream stream, ArrayList<Entry> entries, long revision) {
            this.stream = stream;
            this.entries = new ArrayList<>(entries);
            this.revision = revision;
            this.nextPosition = stream.position == null ? null : stream.position.advance(stream.refreshLane,
                    new TjMediaScanState.Cursor(stream.cursor.minDate, stream.cursor.maxDate,
                            stream.offsetId, stream.offsetRate, peerKind(stream.offsetPeer),
                            peerId(stream.offsetPeer), stream.offsetPeer.access_hash, stream.complete));
        }

        boolean ownerActive() {
            UserConfig config = UserConfig.getInstance(stream.account);
            return config.isClientActivated() && config.getClientUserId() == stream.ownerId;
        }
    }

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
        String errorCode;
        int failureCount;
        long retryAt = Long.MAX_VALUE;
        boolean deferredByFlood;
        boolean queued;
        boolean writing;
        boolean refreshLane;
        boolean nextRefresh = true;
        TjMediaStore.ScanLease lease;
        TjMediaScanState position;
        TjMediaScanState.Cursor cursor;

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

    private boolean durable() { return indexOnly && query.isEmpty(); }

    /** Empty per-owner source sets mean all chats; source type: all/private/groups/channels. */
    public void reset(List<Integer> accounts, Map<Long, Set<Long>> sources, int sourceType, String query) {
        cancelRequests();
        accountRetryAt.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
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

    public boolean hasWriteError() { return !failedWrites.isEmpty(); }

    public String errorCode() {
        for (Stream stream : streams) if (stream.failed && stream.errorCode != null)
            return stream.errorCode;
        return null;
    }

    public void loadMore() {
        loadMore(false);
    }

    public void loadMoreAutomatic() {
        loadMore(true);
    }

    private void schedulePreviewRetry() {
        AndroidUtilities.cancelRunOnUIThread(previewRetry);
        if (closed || indexOnly || retriesPaused || hasWriteError()) return;
        long now = System.currentTimeMillis(), earliest = Long.MAX_VALUE;
        for (Stream stream : streams) {
            if (stream.complete || stream.loading || !(stream.failed || stream.deferredByFlood)) continue;
            earliest = Math.min(earliest, Math.max(stream.failed ? stream.retryAt : now,
                    accountRetryAt.getOrDefault(stream.ownerId, 0L)));
        }
        if (earliest != Long.MAX_VALUE)
            AndroidUtilities.runOnUIThread(previewRetry, Math.max(500, earliest - now));
    }

    private void retryPreview() {
        if (closed || indexOnly || retriesPaused || hasWriteError()) return;
        if (pendingWrites == 0) {
            long now = System.currentTimeMillis();
            for (Stream stream : streams) {
                if (!stream.complete && !stream.loading && !stream.writing
                        && (stream.failed || stream.deferredByFlood)
                        && (!stream.failed || now >= stream.retryAt)
                        && now >= accountRetryAt.getOrDefault(stream.ownerId, 0L)) stream.queued = true;
            }
            pump();
            listener.onChanged();
        }
        schedulePreviewRetry();
    }

    public void setRetriesPaused(boolean paused) {
        retriesPaused = paused;
        schedulePreviewRetry();
    }

    /** -1 means no automatic work; failed sources remain incomplete and inspectable. */
    public long nextAutomaticDelay() {
        if (closed || hasWriteError()) return -1;
        long now = System.currentTimeMillis(), earliest = Long.MAX_VALUE;
        for (Stream stream : streams) {
            if (stream.complete || stream.loading || stream.writing) continue;
            long eligible = Math.max(stream.failed ? stream.retryAt : now,
                    accountRetryAt.getOrDefault(stream.ownerId, 0L));
            earliest = Math.min(earliest, eligible);
        }
        return earliest == Long.MAX_VALUE ? -1 : Math.max(500, earliest - now);
    }

    private void loadMore(boolean automatic) {
        if (closed || pendingWrites > 0) return;
        if (!failedWrites.isEmpty()) {
            if (automatic) return;
            ArrayList<PendingPage> retry = new ArrayList<>(failedWrites);
            failedWrites.clear();
            for (PendingPage page : retry) if (page.ownerActive()) {
                if (page.revision != TjMediaStore.getInstance().revision(page.stream.account))
                    invalidateSource(page.stream.account);
                else index(page);
            }
            listener.onChanged();
            return;
        }
        for (Stream stream : streams) {
            long now = System.currentTimeMillis();
            if (!stream.loading && !stream.writing && !stream.complete
                    && now >= accountRetryAt.getOrDefault(stream.ownerId, 0L)
                    && (!automatic || !stream.failed || now >= stream.retryAt)) stream.queued = true;
            else if (!stream.complete && now < accountRetryAt.getOrDefault(stream.ownerId, 0L)) stream.deferredByFlood = true;
        }
        pump();
        schedulePreviewRetry();
        listener.onChanged();
    }

    private void pump() {
        // Bound queued database snapshots when storage is slower than the network.
        if (pendingWrites >= PAGE_SIZE * 3) return;
        for (Stream stream : streams) {
            int active = 0;
            for (Stream candidate : streams) if (candidate.loading) active++;
            if (active >= 3) break;
            if (!stream.queued || stream.loading || stream.writing) continue;
            if (System.currentTimeMillis() < accountRetryAt.getOrDefault(stream.ownerId, 0L)) {
                stream.queued = false;
                stream.deferredByFlood = true;
                continue;
            }
            stream.queued = false;
            stream.deferredByFlood = false;
            request(stream);
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
        if (durable() && stream.lease == null) {
            stream.loading = true;
            int epoch = generation;
            long revision = TjMediaStore.getInstance().revision(stream.account);
            TjMediaStore.getInstance().acquireScan(stream.account,
                    "v1/" + sourceType + "/" + stream.dialogId + "/" + stream.type, lease -> {
                if (closed || epoch != generation) return;
                stream.loading = false;
                if (revision != TjMediaStore.getInstance().revision(stream.account)) {
                    invalidateSource(stream.account);
                } else if (lease == null || lease.owner != stream.ownerId) stream.failed = true;
                else {
                    try {
                        int now = ConnectionsManager.getInstance(stream.account).getCurrentTime();
                        stream.position = lease.position().length == 0 ? TjMediaScanState.initial(now)
                                : TjMediaScanState.decode(lease.position()).refresh(now);
                        stream.lease = lease;
                        stream.queued = true;
                    } catch (java.io.IOException | IllegalArgumentException e) {
                        // Do not silently discard an unreadable durable cursor.
                        stream.failed = true;
                    }
                }
                pump();
                listener.onChanged();
            });
            return;
        }
        if (durable()) {
            stream.refreshLane = !stream.position.recent.complete
                    && (stream.position.history.complete || stream.nextRefresh);
            stream.cursor = stream.refreshLane ? stream.position.recent : stream.position.history;
            if (stream.cursor.complete) { stream.complete = true; return; }
            stream.offsetId = stream.cursor.offsetId;
            stream.offsetRate = stream.cursor.offsetRate;
            stream.offsetPeer = inputPeer(stream.cursor);
        }
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
            if (durable()) { req.min_date = stream.cursor.minDate; req.max_date = stream.cursor.maxDate; }
            request = req;
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.q = query;
            req.limit = PAGE_SIZE;
            req.filter = filter(stream.type);
            req.offset_id = stream.offsetId;
            if (durable()) { req.min_date = stream.cursor.minDate; req.max_date = stream.cursor.maxDate; }
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
                        // Only protocol identifiers, never arbitrary server text or peer data.
                        stream.errorCode = error != null && error.text != null && error.text.matches("[A-Z_0-9]{1,64}")
                                ? error.text : error == null ? "UNEXPECTED_RESPONSE" : Integer.toString(error.code);
                        long delay = TjMediaRetryPolicy.delay(stream.errorCode, ++stream.failureCount);
                        if (delay < 0 && error != null && error.code >= 500 && error.code <= 599)
                            delay = TjMediaRetryPolicy.delay(Integer.toString(error.code), stream.failureCount);
                        stream.retryAt = delay < 0 ? Long.MAX_VALUE : System.currentTimeMillis() + delay;
                        if (TjMediaRetryPolicy.serverWait(stream.errorCode) > 0)
                            accountRetryAt.put(stream.ownerId, Math.max(accountRetryAt.getOrDefault(stream.ownerId, 0L), stream.retryAt));
                        pump();
                        schedulePreviewRetry();
                        listener.onChanged();
                        return;
                    }
                    TLRPC.messages_Messages page = (TLRPC.messages_Messages) response;
                    stream.failureCount = 0;
                    stream.retryAt = Long.MAX_VALUE;
                    stream.errorCode = null;
                    controller.putUsers(page.users, false);
                    controller.putChats(page.chats, false);
                    MessagesStorage.getInstance(stream.account).putUsersAndChats(page.users, page.chats, true, true);
                    if (page.messages.isEmpty()) {
                        stream.complete = true;
                        if (durable()) index(new PendingPage(stream, new ArrayList<>(), storeRevision));
                    } else {
                        TLRPC.Message last = page.messages.get(page.messages.size() - 1);
                        long lastDialog = MessageObject.getPeerId(last.peer_id);
                        TLRPC.InputPeer nextPeer = controller.getInputPeer(lastDialog);
                        // searchGlobal requires the last date when next_rate is absent.
                        // Presence is a TL flag, not a non-zero value; explicit zero is valid.
                        int nextRate = stream.dialogId == 0
                                ? page instanceof TLRPC.TL_messages_messagesSlice && (page.flags & 1) != 0
                                    ? page.next_rate : last.date
                                : 0;
                        // A repeated cursor cannot make progress; expose retry instead of looping.
                        if (last.id == stream.offsetId && nextRate == stream.offsetRate
                                && stream.offsetPeer != null
                                && MessageObject.getPeerId(last.peer_id) == peerDialog(stream.offsetPeer)) {
                            stream.failed = true;
                        } else if (nextPeer == null) {
                            stream.failed = true;
                        } else {
                            stream.offsetId = last.id;
                            stream.offsetRate = nextRate;
                            stream.offsetPeer = nextPeer;
                        }
                        ArrayList<Entry> pageEntries = new ArrayList<>();
                        for (TLRPC.Message message : page.messages) {
                            TLRPC.MessageMedia media = MessageObject.getMedia(message);
                            if (!(message instanceof TLRPC.TL_message) || message.id <= 0 || media == null
                                    || media.ttl_seconds != 0 || message.ttl_period != 0
                                    || DialogObject.isEncryptedDialog(MessageObject.getPeerId(message.peer_id))
                                    || !(media instanceof TLRPC.TL_messageMediaPhoto
                                    || media instanceof TLRPC.TL_messageMediaDocument)) continue;
                            MessageObject object = new MessageObject(stream.account, message, false, !indexOnly);
                            Entry entry = new Entry(stream.account, stream.ownerId, object);
                            pageEntries.add(entry);
                            scannedCount++;
                            if (!indexOnly) {
                                if (keys.add(entry.key)) entries.add(entry);
                            }
                        }
                        index(new PendingPage(stream, pageEntries, storeRevision));
                        entries.sort((a, b) -> {
                            int date = Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date);
                            return date != 0 ? date : a.key.compareTo(b.key);
                        });
                        // All eligible messages are still indexed. Only the discovery
                        // preview is bounded; older entries remain reachable via local pages.
                        while (entries.size() > VISIBLE_LIMIT) keys.remove(entries.remove(entries.size() - 1).key);
                    }
                    pump();
                    listener.onChanged();
                }));
    }

    private void index(PendingPage page) {
        ArrayList<Entry> batch = page.entries;
        if (batch.isEmpty() && !durable()) return;
        ArrayList<MessageObject> messages = new ArrayList<>(batch.size());
        for (Entry entry : batch) messages.add(entry.message);
        int epoch = generation;
        int weight = Math.max(1, batch.size());
        pendingWrites += weight;
        page.stream.writing = true;
        TjMediaStore.Callback<Boolean> done = success -> {
            if (closed || epoch != generation) return;
            pendingWrites -= weight;
            page.stream.writing = false;
            if (page.revision != TjMediaStore.getInstance().revision(page.stream.account)) invalidateSource(page.stream.account);
            else if (!success) {
                if (page.ownerActive()) failedWrites.add(page);
                // Do not turn a storage outage into an unbounded failed-page buffer.
                // In-flight requests may drain, but queued sources wait for user retry.
                // Clearing queued also lets isLoading() expose the actionable error.
                for (Stream stream : streams) stream.queued = false;
            }
            if (pendingWrites == 0) {
                pump();
                listener.onChanged();
            }
        };
        if (durable()) {
            TjMediaStore.getInstance().commitScanPage(page.stream.lease, page.nextPosition.encode(),
                    messages, page.revision, committed -> {
                if (closed || epoch != generation) return;
                if (committed != null) {
                    page.stream.lease = committed;
                    page.stream.position = page.nextPosition;
                    page.stream.nextRefresh = !page.stream.refreshLane;
                    page.stream.complete = page.nextPosition.history.complete && page.nextPosition.recent.complete;
                }
                done.run(committed != null);
            });
        } else TjMediaStore.getInstance().indexBatch(messages, page.revision, done);
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
            stream.lease = null;
            stream.position = null;
            stream.writing = false;
        }
        for (int i = entries.size() - 1; i >= 0; i--) if (entries.get(i).account == account) {
            keys.remove(entries.remove(i).key);
        }
        java.util.Iterator<PendingPage> failed = failedWrites.iterator();
        while (failed.hasNext()) if (failed.next().stream.account == account) failed.remove();
    }

    private static long peerDialog(TLRPC.InputPeer peer) {
        if (peer.user_id != 0) return peer.user_id;
        return peer.channel_id != 0 ? -peer.channel_id : -peer.chat_id;
    }

    private static int peerKind(TLRPC.InputPeer peer) {
        if (peer instanceof TLRPC.TL_inputPeerSelf) return 1;
        if (peer.user_id != 0) return 2;
        if (peer.chat_id != 0) return 3;
        return peer.channel_id != 0 ? 4 : 0;
    }

    private static long peerId(TLRPC.InputPeer peer) {
        return peer.user_id != 0 ? peer.user_id : peer.chat_id != 0 ? peer.chat_id : peer.channel_id;
    }

    private static TLRPC.InputPeer inputPeer(TjMediaScanState.Cursor cursor) {
        TLRPC.InputPeer peer;
        switch (cursor.peerKind) {
            case 1: peer = new TLRPC.TL_inputPeerSelf(); break;
            case 2: peer = new TLRPC.TL_inputPeerUser(); peer.user_id = cursor.peerId; break;
            case 3: peer = new TLRPC.TL_inputPeerChat(); peer.chat_id = cursor.peerId; break;
            case 4: peer = new TLRPC.TL_inputPeerChannel(); peer.channel_id = cursor.peerId; break;
            default: peer = new TLRPC.TL_inputPeerEmpty();
        }
        peer.access_hash = cursor.accessHash;
        return peer;
    }

    private void cancelRequests() {
        generation++;
        AndroidUtilities.cancelRunOnUIThread(previewRetry);
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
