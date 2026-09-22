package org.telegram.messenger.tj;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;

/**
 * The archive, read through the chat folders.
 *
 * Everything put away lands in one long list where a work group sits next to a shop's channel
 * next to somebody you stopped talking to. The chats already belong to folders, so the archive is
 * shown under those names - each chat under the first folder that claims it, and whatever no
 * folder claims under a heading of its own.
 *
 * Nothing is moved or changed: this is the same list in the same order, with headings in it.
 */
public final class TjArchiveSections {

    private TjArchiveSections() { }

    public static ArrayList<TLRPC.Dialog> split(int accountId, int dialogsType, int folderId, ArrayList<TLRPC.Dialog> dialogs) {
        if (!TjConfig.archiveFolderSections() || folderId != 1
                || dialogsType != DialogsActivity.DIALOGS_TYPE_DEFAULT
                || dialogs == null || dialogs.size() < 2) {
            return dialogs;
        }
        final AccountInstance account = AccountInstance.getInstance(accountId);
        final ArrayList<MessagesController.DialogFilter> folders = new ArrayList<>();
        for (MessagesController.DialogFilter filter : account.getMessagesController().getDialogFilters()) {
            if (filter != null && !filter.isDefault()) {
                folders.add(filter);
            }
        }
        if (folders.isEmpty()) {
            return dialogs;
        }

        // One bucket per folder, in the order the folders are in, plus one for everything else.
        final ArrayList<ArrayList<TLRPC.Dialog>> buckets = new ArrayList<>();
        for (int a = 0; a < folders.size() + 1; a++) {
            buckets.add(new ArrayList<>());
        }
        boolean anyFolderMatched = false;
        for (int a = 0; a < dialogs.size(); a++) {
            final TLRPC.Dialog dialog = dialogs.get(a);
            if (dialog instanceof DialogsActivity.DialogsHeader) {
                // Somebody else already sectioned this list; leave it alone.
                return dialogs;
            }
            int bucket = folders.size();
            for (int f = 0; f < folders.size(); f++) {
                if (includes(folders.get(f), account, dialog)) {
                    bucket = f;
                    anyFolderMatched = true;
                    break;
                }
            }
            buckets.get(bucket).add(dialog);
        }
        if (!anyFolderMatched) {
            return dialogs;
        }

        final ArrayList<TLRPC.Dialog> out = new ArrayList<>(dialogs.size() + buckets.size());
        for (int a = 0; a < folders.size(); a++) {
            if (buckets.get(a).isEmpty()) {
                continue;
            }
            out.add(new DialogsActivity.DialogsHeader(folders.get(a).name));
            out.addAll(buckets.get(a));
        }
        final ArrayList<TLRPC.Dialog> rest = buckets.get(folders.size());
        if (!rest.isEmpty()) {
            // Only worth naming when there is something above it to tell it apart from.
            if (out.isEmpty()) {
                return dialogs;
            }
            out.add(new DialogsActivity.DialogsHeader(TjLocale.getString(R.string.TjArchiveOtherChats)));
            out.addAll(rest);
        }
        return out;
    }

    private static boolean includes(MessagesController.DialogFilter filter, AccountInstance account, TLRPC.Dialog dialog) {
        try {
            return filter.includesDialog(account, dialog.id, dialog);
        } catch (Throwable ignored) {
            // A folder that cannot answer is a folder that does not claim this chat.
            return false;
        }
    }
}
