package org.telegram.messenger.tj;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import java.util.ArrayList;
/** Controllable storage boundary. Production SQLite is tested separately. */
public final class TjMediaStore {
    public interface Callback<T> { void run(T value); }
    private static final TjMediaStore instance = new TjMediaStore();
    public static TjMediaStore getInstance() { return instance; }
    public final long[] revisions = new long[3];
    public final ArrayList<Pending> pending = new ArrayList<>();
    public int saved;
    private final java.util.HashMap<String, ScanLease> scans = new java.util.HashMap<>();
    public static final class ScanLease {
        public int account;
        public long owner, sequence;
        String scope;
        byte[] bytes = new byte[0];
        public byte[] position() { return bytes.clone(); }
    }
    public void acquireScan(int account, String scope, Callback<ScanLease> callback) {
        String key = UserConfig.getInstance(account).getClientUserId() + ":" + scope;
        ScanLease old = scans.get(key), next = new ScanLease();
        next.account = account; next.owner = UserConfig.getInstance(account).getClientUserId(); next.scope = scope;
        if (old != null) { next.sequence = old.sequence; next.bytes = old.bytes.clone(); }
        scans.put(key, next);
        callback.run(next);
    }
    public void commitScanPage(ScanLease scan, byte[] position, java.util.List<MessageObject> messages,
                               long revision, Callback<ScanLease> callback) {
        String key = scan.owner + ":" + scan.scope;
        Callback<Boolean> completed = success -> {
            if (!success || scans.get(key) != scan || revisions[scan.account] != revision
                    || UserConfig.getInstance(scan.account).getClientUserId() != scan.owner) { callback.run(null); return; }
            ScanLease next = new ScanLease(); next.account = scan.account; next.owner = scan.owner;
            next.scope = scan.scope; next.sequence = scan.sequence + 1; next.bytes = position.clone();
            scans.put(key, next); callback.run(next);
        };
        if (messages.isEmpty()) completed.run(true);
        else indexBatch(messages, revision, completed);
    }
    public static final class Pending {
        ArrayList<MessageObject> messages;
        long revision, owner;
        Callback<Boolean> callback;
    }
    public long revision(int account) { return revisions[account]; }
    public void indexBatch(java.util.List<MessageObject> messages, long expectedRevision, Callback<Boolean> callback) {
        for (MessageObject message : messages) if (message.getId() <= 0) {
            callback.run(false);
            return;
        }
        Pending write = new Pending();
        write.messages = new ArrayList<>(messages); write.revision = expectedRevision; write.callback = callback;
        write.owner = UserConfig.getInstance(messages.get(0).currentAccount).getClientUserId();
        pending.add(write);
    }
    public int pendingMessages() {
        int count = 0;
        for (Pending write : pending) count += write.messages.size();
        return count;
    }
    public java.util.List<Integer> pageIds(int index) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (MessageObject message : pending.get(index).messages) ids.add(message.getId());
        return ids;
    }
    public void complete(boolean success) {
        Pending write = pending.remove(0);
        for (MessageObject message : write.messages) success &= revision(message.currentAccount) == write.revision
                && UserConfig.getInstance(message.currentAccount).getClientUserId() == write.owner;
        if (success) saved += write.messages.size();
        write.callback.run(success);
    }
    public void reset() { pending.clear(); scans.clear(); saved = 0; java.util.Arrays.fill(revisions, 0); }
}
