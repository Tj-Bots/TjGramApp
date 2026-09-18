package org.telegram.messenger.tj;

import android.util.SparseArray;

import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * The account log as a chat in the chat list.
 *
 * There is no such account and nothing is ever sent: the entry in the list is built here, out of
 * what the log already holds, and the tap on it opens the log rather than a conversation. It is put
 * back after every sort, which is what makes it survive the chat list being rebuilt from the
 * server - the list is the server's, this row is not, and neither has to know about the other.
 *
 * The id is far above anything Telegram has handed out and is never sent anywhere, so it cannot
 * collide with a real person in any way that matters: nothing is addressed to it.
 */
public final class TjAccountLogDialog {

    public static final long USER_ID = 900000000001L;
    public static final long DIALOG_ID = USER_ID;

    private static final int MESSAGE_ID = 1;

    private TjAccountLogDialog() { }

    public static boolean is(long dialogId) {
        return dialogId == DIALOG_ID;
    }

    /** The stand-in this chat is drawn as: a name, and no photo of its own. */
    public static TLRPC.User user() {
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = USER_ID;
        user.first_name = TjLocale.getString(R.string.TjAccountLog);
        user.last_name = "";
        user.verified = true;
        user.status = null;
        user.access_hash = 0;
        return user;
    }

    /** What the row says it last heard, and when. Rebuilt from the log rather than stored twice. */
    private static volatile String preview = "";
    private static volatile int previewDate;
    private static volatile int unread;

    public static void setPreview(String text, int date, int unreadCount) {
        preview = text == null ? "" : text;
        previewDate = date;
        unread = Math.max(0, unreadCount);
    }

    public static int unreadCount() {
        return unread;
    }

    /**
     * Puts the row back into the chat list. Called at the end of every sort, so whatever the
     * server did to the list a moment ago, the row is there again by the time it is drawn.
     */
    public static void attach(int account, SparseArray<ArrayList<TLRPC.Dialog>> dialogsByFolder) {
        if (!TjConfig.accountLog() || previewDate == 0) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        if (UserConfig.getInstance(account).getClientUserId() == 0) {
            return;
        }
        if (controller.getUser(USER_ID) == null) {
            controller.putUser(user(), true);
        }
        TLRPC.Dialog dialog = controller.dialogs_dict.get(DIALOG_ID);
        if (dialog == null) {
            dialog = new TLRPC.TL_dialog();
            dialog.id = DIALOG_ID;
            dialog.folder_id = 0;
            dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
            controller.dialogs_dict.put(DIALOG_ID, dialog);
        }
        dialog.top_message = MESSAGE_ID;
        dialog.last_message_date = previewDate;
        dialog.unread_count = unread;

        ArrayList<MessageObject> shown = controller.dialogMessage.get(DIALOG_ID);
        if (shown == null || shown.isEmpty() || shown.get(0).messageOwner.date != previewDate) {
            MessageObject object = previewMessage(account);
            ArrayList<MessageObject> messages = new ArrayList<>();
            if (object != null) messages.add(object);
            controller.dialogMessage.put(DIALOG_ID, messages);
        }

        ArrayList<TLRPC.Dialog> folder = dialogsByFolder.get(0);
        if (folder == null) {
            folder = new ArrayList<>();
            dialogsByFolder.put(0, folder);
        }
        folder.remove(dialog);
        int index = 0;
        while (index < folder.size()) {
            TLRPC.Dialog other = folder.get(index);
            if (other instanceof TLRPC.TL_dialogFolder || other.pinned
                    || other.last_message_date > previewDate) {
                index++;
                continue;
            }
            break;
        }
        folder.add(index, dialog);
    }

    /** The message the row shows as its last one, built fresh because it is never stored. */
    public static MessageObject previewMessage(int account) {
        if (preview.isEmpty()) {
            return null;
        }
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = MESSAGE_ID;
        message.message = preview;
        message.date = previewDate;
        message.out = false;
        message.unread = unread > 0;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = USER_ID;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = USER_ID;
        message.dialog_id = DIALOG_ID;
        MessageObject object = new MessageObject(account, message, false, false);
        object.setIsRead();
        return object;
    }
}
