import java.util.*;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.*;
import org.telegram.tgnet.TLRPC;
public class MediaCoordinatorTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("coordinator " + checks); checks++; }
    public static void main(String[] args) {
        TjMediaScanCoordinator job = TjMediaScanCoordinator.getInstance();
        MessagesController controller = MessagesController.getInstance(0);
        controller.dialogs_dict.add(new TLRPC.Dialog(1)); controller.dialogs_dict.add(new TLRPC.Dialog(2));
        MessagesController.DialogFilter folder = new MessagesController.DialogFilter(); folder.id=7; folder.members.add(1L);
        controller.dialogFilters.add(folder);
        NotificationCenter events = NotificationCenter.getInstance(0);
        job.start(List.of(0), Map.of(), Map.of(100L, Set.of(7L)), 0);
        TjMediaLibrary first = job.scanner();
        check(first != null && first.sources.get(100L).equals(Set.of(1L)));
        Runnable screen = () -> {}; job.addListener(screen); job.removeListener(screen);
        check(job.scanner() == first && !first.closed); // fragment exit is not job cancellation
        folder.members.add(2L);
        events.post(NotificationCenter.dialogFiltersUpdated, 0);
        events.post(NotificationCenter.dialogsNeedReload, 0);
        check(AndroidUtilities.queued.size() == 1); // coalesced, no starvation
        AndroidUtilities.drain();
        check(first.closed && job.scanner() != first && job.scanner().sources.get(100L).equals(Set.of(1L, 2L)));
        TjMediaLibrary second = job.scanner();
        ApplicationLoader.mainInterfacePaused=true; job.setForeground(false);
        check(second.closed && job.scanner() == null && job.isRequested());
        folder.members.remove(1L);
        ApplicationLoader.mainInterfacePaused=false; job.setForeground(true);
        check(job.scanner().sources.get(100L).equals(Set.of(2L)));
        job.scanner().finish();
        check(job.scanner() == null && job.isRequested() && events.size() == 4);
        folder.members.add(1L); events.post(NotificationCenter.dialogFiltersUpdated, 0); AndroidUtilities.drain();
        check(job.scanner() != null && job.scanner().sources.get(100L).equals(Set.of(1L, 2L)));
        job.stop(); check(!job.isRequested() && events.size() == 0 && AndroidUtilities.queued.isEmpty());
        job.start(List.of(0), Map.of(), Map.of(), 0); job.scanner().finish();
        check(!job.isRequested() && job.scanner() == null && events.size() == 0);
        job.start(List.of(0), Map.of(), Map.of(), 0);
        ApplicationLoader.mainInterfacePaused=true; job.setForeground(false);
        UserConfig.setOwner(0, 999L);
        ApplicationLoader.mainInterfacePaused=false; job.setForeground(true);
        check(!job.isRequested() && job.scanner() == null && events.size() == 0);
        System.out.println(checks + " production coordinator lifecycle/folder/owner checks passed; transport mocked");
    }
}
