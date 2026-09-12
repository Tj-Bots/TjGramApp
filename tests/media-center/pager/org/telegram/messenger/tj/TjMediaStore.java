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
    public static final class Pending {
        MessageObject message;
        long revision, owner;
        Callback<Boolean> callback;
    }
    public long revision(int account) { return revisions[account]; }
    public void index(MessageObject message, long expectedRevision, Callback<Boolean> callback) {
        Pending write = new Pending();
        write.message = message; write.revision = expectedRevision; write.callback = callback;
        write.owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
        pending.add(write);
    }
    public void complete(boolean success) {
        Pending write = pending.remove(0);
        success &= revision(write.message.currentAccount) == write.revision
                && UserConfig.getInstance(write.message.currentAccount).getClientUserId() == write.owner;
        if (success) saved++;
        write.callback.run(success);
    }
    public void reset() { pending.clear(); saved = 0; java.util.Arrays.fill(revisions, 0); }
}
