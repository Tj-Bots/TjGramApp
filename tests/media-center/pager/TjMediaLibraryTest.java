import org.telegram.messenger.UserConfig;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.Collections;

public final class TjMediaLibraryTest {
    private static int checks;
    private static final TjMediaStore store = TjMediaStore.getInstance();
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static TjMediaLibrary start(boolean scan) {
        ConnectionsManager.requests.clear(); store.reset();
        for (int account = 0; account < 3; account++) {
            UserConfig.getInstance(account).owner = 10 + account;
            UserConfig.getInstance(account).active = true;
        }
        TjMediaLibrary library = new TjMediaLibrary(() -> {}, scan);
        library.reset(Collections.singletonList(0), Collections.emptyMap(), 1, "caption movie");
        return library;
    }
    private static TLRPC.messages_Messages page(int first, int count) {
        TLRPC.messages_Messages result = new TLRPC.messages_Messages();
        for (int i = 0; i < count; i++) {
            TLRPC.Message message = new TLRPC.TL_message();
            message.id = first - i; message.date = first - i; message.peer_id.user_id = 77;
            message.media = new TLRPC.TL_messageMediaDocument();
            result.messages.add(message);
        }
        return result;
    }
    private static void emptyNetwork() {
        int guard = 100;
        while (!ConnectionsManager.active().isEmpty() && guard-- > 0)
            ConnectionsManager.respond(ConnectionsManager.active().get(0), page(0, 0), null);
        check(guard > 0, "network terminates");
    }
    public static void main(String[] args) {
        TjMediaLibrary library = start(false);
        check(ConnectionsManager.active().size() == 3, "three initial requests maximum");
        TLRPC.TL_messages_searchGlobal request = (TLRPC.TL_messages_searchGlobal) ConnectionsManager.active().get(0).request;
        check(request.users_only && !request.groups_only && !request.broadcasts_only, "private source filter");
        check(request.limit == 40 && request.q.equals("caption movie"), "bounded search with query");
        ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        emptyNetwork();
        check(library.isLoading() && store.pending.size() == 1, "wait for storage after network");
        store.complete(false);
        check(library.hasError() && !library.isLoading(), "failed storage is actionable");
        library.loadMore();
        check(store.pending.size() == 1 && ConnectionsManager.active().isEmpty(), "retry writes before next page");
        store.complete(true);
        check(!library.hasError() && library.snapshot().size() == 1, "successful retry preserves card");
        library.close();

        library = start(false);
        ConnectionsManager.Pending late = ConnectionsManager.active().get(0);
        store.revisions[0]++;
        ConnectionsManager.respond(late, page(100, 1), null);
        check(library.hasError() && library.snapshot().isEmpty() && store.pending.isEmpty(), "late page rejected after clear/delete");
        library.loadMore();
        check(ConnectionsManager.active().size() == 3, "fresh requests on retry");
        request = (TLRPC.TL_messages_searchGlobal) ConnectionsManager.active().get(0).request;
        check(request.offset_id == 0, "invalidated source restarts from fresh cursor");
        library.close();

        library = start(false);
        ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        emptyNetwork(); store.complete(false);
        store.revisions[0]++;
        library.loadMore();
        check(store.pending.isEmpty() && library.snapshot().isEmpty(), "failed write snapshot not replayed after deletion");
        library.loadMore();
        check(!ConnectionsManager.active().isEmpty(), "stale write retry can refetch");
        library.close();

        library = start(false);
        late = ConnectionsManager.active().get(0);
        UserConfig.getInstance(0).owner = 99;
        ConnectionsManager.respond(late, page(100, 1), null);
        check(library.snapshot().isEmpty() && store.pending.isEmpty(), "replacement account cannot inherit page");
        library.close();

        library = start(false);
        late = ConnectionsManager.active().get(0);
        library.close();
        ConnectionsManager.respond(late, page(100, 1), null);
        check(library.snapshot().isEmpty() && store.pending.isEmpty(), "close rejects late callback");

        library = start(true);
        TLRPC.messages_Messages restricted = page(100, 4);
        restricted.messages.get(1).media.ttl_seconds = 1;
        restricted.messages.get(2).ttl_period = 1;
        restricted.messages.get(3).peer_id.user_id = 1L << 40;
        ConnectionsManager.respond(ConnectionsManager.active().get(0), restricted, null);
        check(store.pending.size() == 1 && library.scannedCount() == 1, "TTL and secret media excluded");
        check(library.snapshot().isEmpty(), "scan does not retain growing message list");
        library.close();

        library = start(false);
        library.reset(java.util.Arrays.asList(0, 1, 0, -1, 3), Collections.emptyMap(), 0, "");
        int guard = 100;
        while (!ConnectionsManager.active().isEmpty() && guard-- > 0)
            ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        check(guard > 0 && library.snapshot().size() == 2, "deduplicate per owner, not across owners");
        check(!library.snapshot().get(0).key.equals(library.snapshot().get(1).key), "owner belongs to entry identity");
        while (!store.pending.isEmpty()) store.complete(true);
        check(!library.isLoading(), "all account writes drain");
        library.close();

        library = start(false);
        library.reset(Collections.singletonList(0), Collections.singletonMap(10L, Collections.singleton(-88L)), 0, "caption");
        check(ConnectionsManager.active().size() == 3, "selected source uses bounded parallel requests");
        TLRPC.TL_messages_search selected = (TLRPC.TL_messages_search) ConnectionsManager.active().get(0).request;
        check(selected.peer.channel_id == 88 && selected.q.equals("caption"), "selected dialog and caption query sent");
        library.close();

        library = start(false);
        library.reset(Collections.singletonList(0), Collections.singletonMap(10L, Collections.singleton(0L)), 0, "");
        check(ConnectionsManager.active().isEmpty() && !library.hasMore(), "explicit no sources never falls back to all chats");
        library.close();

        library = start(true);
        library.reset(java.util.Arrays.asList(0, 1, 2), Collections.emptyMap(), 0, "");
        guard = 100;
        while (!ConnectionsManager.active().isEmpty() && guard-- > 0)
            ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 40), null);
        check(guard > 0 && store.pending.size() <= 200, "slow storage bounds queued message snapshots");
        check(store.pending.size() >= 120 && library.isLoading(), "backpressure pauses before remaining streams");
        check(ConnectionsManager.active().isEmpty(), "backpressure does not start more requests");
        while (!store.pending.isEmpty()) store.complete(true);
        check(ConnectionsManager.active().size() == 3, "network pumping resumes after writes drain");
        library.close();

        library = start(false);
        ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        emptyNetwork();
        store.revisions[0]++;
        store.complete(true);
        check(store.saved == 0 && library.snapshot().isEmpty() && library.hasError(), "revision rejects an already queued stale write");
        library.loadMore();
        check(ConnectionsManager.active().size() == 3, "pending count drains after rejected write");
        library.close();

        library = start(false);
        ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        emptyNetwork(); store.complete(true);
        library.loadMore();
        ConnectionsManager.respond(ConnectionsManager.active().get(0), page(100, 1), null);
        while (!store.pending.isEmpty()) store.complete(true);
        check(library.hasError() && !library.isLoading(), "repeated server cursor exposes retry instead of looping");
        check(library.snapshot().size() == 1, "repeated cursor does not duplicate cards");
        library.close();
        System.out.println(checks + " production pager sequencing checks passed; no Telegram requests");
    }
}
