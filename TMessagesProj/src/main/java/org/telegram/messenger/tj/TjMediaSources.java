package org.telegram.messenger.tj;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** UI-thread snapshot of explicitly selected chats plus current folder membership. */
public final class TjMediaSources {
    private TjMediaSources() { }

    public static HashMap<Long, Set<Long>> copy(Map<Long, Set<Long>> input) {
        HashMap<Long, Set<Long>> result = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> entry : input.entrySet())
            result.put(entry.getKey(), new HashSet<>(entry.getValue()));
        return result;
    }

    public static HashMap<Long, Set<Long>> resolve(List<Integer> accounts,
            Map<Long, Set<Long>> chats, Map<Long, Set<Long>> folders) {
        HashMap<Long, Set<Long>> result = copy(chats);
        boolean explicit = !chats.isEmpty() || !folders.isEmpty();
        for (int account : accounts) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            if (explicit) {
                Set<Long> peers = result.computeIfAbsent(owner, ignored -> new HashSet<>());
                if (peers.isEmpty()) peers.add(0L);
            }
            Set<Long> selected = folders.get(owner);
            if (selected == null || selected.isEmpty()) continue;
            Set<Long> peers = result.computeIfAbsent(owner, ignored -> new HashSet<>());
            MessagesController controller = MessagesController.getInstance(account);
            AccountInstance instance = AccountInstance.getInstance(account);
            for (MessagesController.DialogFilter folder : controller.dialogFilters) {
                if (!selected.contains((long) folder.id) || folder.isDefault()) continue;
                for (int i = 0; i < controller.dialogs_dict.size(); i++) {
                    long id = controller.dialogs_dict.keyAt(i);
                    TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
                    if (id != 0 && !DialogObject.isEncryptedDialog(id)
                            && !(dialog instanceof TLRPC.TL_dialogFolder)
                            && folder.includesDialog(instance, id, dialog)) peers.add(id);
                }
                // Explicit cloud members can exist before their dialog page is loaded.
                for (long id : folder.alwaysShow) {
                    if (!TjLocalFolders.isLocal(folder.id) && id != 0 && !DialogObject.isEncryptedDialog(id)
                            && !folder.neverShow.contains(id)) peers.add(id);
                }
            }
            // A missing/deleted/empty folder must never expand to all conversations.
            peers.remove(0L);
            if (peers.isEmpty()) peers.add(0L);
        }
        return result;
    }
}
