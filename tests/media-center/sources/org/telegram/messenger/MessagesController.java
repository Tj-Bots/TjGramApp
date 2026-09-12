package org.telegram.messenger;
import java.util.*;
import org.telegram.tgnet.TLRPC;
public class MessagesController {
    private static final Map<Integer, MessagesController> instances = new HashMap<>();
    public static MessagesController getInstance(int account) { return instances.computeIfAbsent(account, x -> new MessagesController()); }
    public final List<DialogFilter> dialogFilters = new ArrayList<>();
    public final Dialogs dialogs_dict = new Dialogs();
    public static class Dialogs extends ArrayList<TLRPC.Dialog> {
        public long keyAt(int index) { return get(index).id; }
        public TLRPC.Dialog valueAt(int index) { return get(index); }
    }
    public static class DialogFilter {
        public int id;
        public Set<Long> members = new HashSet<>(), alwaysShow = new HashSet<>(), neverShow = new HashSet<>();
        public boolean isDefault() { return id == 0; }
        public boolean includesDialog(AccountInstance account, long id, TLRPC.Dialog dialog) {
            return !neverShow.contains(id) && (members.contains(id) || alwaysShow.contains(id));
        }
    }
}
