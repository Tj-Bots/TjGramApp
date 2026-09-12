import java.util.*;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.TjMediaSources;
import org.telegram.tgnet.TLRPC;
public class MediaSourcesTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("source check " + checks); checks++; }
    public static void main(String[] args) {
        MessagesController controller = MessagesController.getInstance(0);
        for (long id : new long[]{1, 2, 3, Long.MAX_VALUE}) controller.dialogs_dict.add(new TLRPC.Dialog(id));
        controller.dialogs_dict.add(new TLRPC.TL_dialogFolder(99));
        MessagesController.DialogFilter folder = new MessagesController.DialogFilter();
        folder.id = 7; folder.members.addAll(Set.of(1L, 2L, 99L, Long.MAX_VALUE));
        controller.dialogFilters.add(folder);
        Map<Long, Set<Long>> chats = new HashMap<>(), folders = new HashMap<>();
        check(TjMediaSources.resolve(List.of(0), chats, folders).isEmpty());
        folders.put(100L, new HashSet<>(Set.of(7L)));
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(1L, 2L)));
        chats.put(100L, new HashSet<>(Set.of(2L, 3L)));
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(1L, 2L, 3L)));
        check(chats.get(100L).equals(Set.of(2L, 3L))); // no mutation of saved selection
        chats.clear(); folder.members.remove(1L); folder.members.add(3L);
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(2L, 3L)));
        folder.alwaysShow.add(44L); folder.neverShow.add(2L);
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(3L, 44L)));
        check(!TjMediaSources.resolve(List.of(1), chats, folders).containsKey(100L));
        check(TjMediaSources.resolve(List.of(1), chats, folders).get(101L).equals(Set.of(0L)));
        check(TjMediaSources.resolve(List.of(0), Map.of(100L, Set.of()), Map.of()).get(100L).equals(Set.of(0L)));
        controller.dialogFilters.clear();
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(0L)));
        chats.put(100L, new HashSet<>(Set.of(3L)));
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(3L)));
        chats.clear(); folder.id = 1000001; folder.members.clear();
        folder.members.add(3L); controller.dialogFilters.add(folder); folders.put(100L, Set.of(1000001L));
        check(TjMediaSources.resolve(List.of(0), chats, folders).get(100L).equals(Set.of(3L)));
        System.out.println(checks + " production source selection checks passed; no Telegram calls");
    }
}
