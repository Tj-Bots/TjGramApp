import org.telegram.messenger.UserConfig;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.messenger.tj.TjMediaScanState;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import java.util.*;

public class TjMediaDurableScanTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static TjMediaLibrary open() {
        TjMediaLibrary library = new TjMediaLibrary(() -> {}, true);
        library.reset(Collections.singletonList(0), Collections.emptyMap(), 0, "");
        return library;
    }
    static ConnectionsManager.Pending photos() {
        for (ConnectionsManager.Pending p : ConnectionsManager.active()) {
            if (((TLRPC.TL_messages_searchGlobal)p.request).filter instanceof TLRPC.TL_inputMessagesFilterPhotoVideo) return p;
        }
        throw new AssertionError("Photo stream missing");
    }
    static TLRPC.messages_Messages page(int id, int date) {
        TLRPC.messages_Messages page = new TLRPC.messages_Messages();
        TLRPC.Message m = new TLRPC.TL_message(); m.id = id; m.date = date;
        m.peer_id.user_id = 77; m.media = new TLRPC.TL_messageMediaDocument(); page.messages.add(m);
        return page;
    }
    public static void main(String[] args) throws Exception {
        UserConfig.getInstance(0).active = true; UserConfig.getInstance(0).owner = 12;
        TjMediaStore store = TjMediaStore.getInstance(); store.reset();
        ConnectionsManager.requests.clear(); ConnectionsManager.now = 1000;
        TjMediaLibrary library = open();
        check(ConnectionsManager.active().size() <= 3, "bounded concurrency during lease acquisition");
        TLRPC.TL_messages_searchGlobal first = (TLRPC.TL_messages_searchGlobal)photos().request;
        check(first.min_date == 0 && first.max_date == 1001, "fixed initial history window");
        ConnectionsManager.respond(photos(), page(90,900), null);
        check(store.pendingMessages() == 1, "page waits for atomic write");
        store.complete(true); library.close();
        ConnectionsManager.now = 1100;
        library = open();
        TLRPC.TL_messages_searchGlobal refresh = (TLRPC.TL_messages_searchGlobal)photos().request;
        check(refresh.min_date == 999 && refresh.max_date == 1101, "recent refresh does not restart history");
        ConnectionsManager.respond(photos(), new TLRPC.messages_Messages(), null);
        // Finish the other initial lanes so a new page can be requested explicitly.
        while (!ConnectionsManager.active().isEmpty()) ConnectionsManager.respond(ConnectionsManager.active().get(0), new TLRPC.messages_Messages(), null);
        library.loadMore();
        TLRPC.TL_messages_searchGlobal resumed = (TLRPC.TL_messages_searchGlobal)photos().request;
        check(resumed.offset_id == 90 && resumed.offset_rate == 900, "committed historical boundary restored");
        check(resumed.max_date == 1001 && resumed.offset_peer.user_id == 77, "historical window and peer preserved");
        ConnectionsManager.respond(photos(), page(80,800), null);
        store.complete(false); library.close();
        library = open();
        ConnectionsManager.respond(photos(), new TLRPC.messages_Messages(), null);
        while (!ConnectionsManager.active().isEmpty()) ConnectionsManager.respond(ConnectionsManager.active().get(0), new TLRPC.messages_Messages(), null);
        library.loadMore();
        resumed = (TLRPC.TL_messages_searchGlobal)photos().request;
        check(resumed.offset_id == 90, "failed page never advances durable position");
        library.close();
        TjMediaScanState initial = TjMediaScanState.initial(1000);
        check(TjMediaScanState.decode(initial.encode()).history.maxDate == 1001, "cursor serialization roundtrip");
        byte[] bad = initial.encode(); bad[0] = 1;
        try { TjMediaScanState.decode(bad); throw new AssertionError("bad version accepted"); } catch (java.io.IOException expected) { checks++; }
        System.out.println(checks + " durable scan/resume checks passed");
    }
}
