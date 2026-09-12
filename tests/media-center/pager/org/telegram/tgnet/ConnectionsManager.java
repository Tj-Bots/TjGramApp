package org.telegram.tgnet;
import java.util.ArrayList;
public final class ConnectionsManager {
    public interface Callback { void run(TLObject response, Object error); }
    public static final class Pending {
        public int id, account;
        public TLObject request;
        public Callback callback;
        public boolean canceled, completed;
    }
    private final int account;
    private ConnectionsManager(int account) { this.account = account; }
    public static ConnectionsManager getInstance(int account) { return new ConnectionsManager(account); }
    public static int now = 1000;
    public int getCurrentTime() { return now; }
    public static final ArrayList<Pending> requests = new ArrayList<>();
    public int sendRequest(TLObject request, Callback callback) {
        Pending pending = new Pending();
        pending.id = requests.size() + 1;
        pending.account = account;
        pending.request = request;
        pending.callback = callback;
        requests.add(pending);
        return pending.id;
    }
    public void cancelRequest(int id, boolean notifyServer) { requests.get(id - 1).canceled = true; }
    public static void respond(Pending pending, TLObject result, Object error) {
        pending.completed = true;
        pending.callback.run(result, error); // Deliberately permits a late canceled callback.
    }
    public static ArrayList<Pending> active() {
        ArrayList<Pending> active = new ArrayList<>();
        for (Pending request : requests) if (!request.completed && !request.canceled) active.add(request);
        return active;
    }
}
