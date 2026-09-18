package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * The chat the account log is written into.
 *
 * It is a real chat: a user in the local database, and real messages stored the way any other
 * message is stored. That is the whole design - nothing about these messages is special by the
 * time they are drawn, so copying, pinning, selecting, deleting, the unread mark and the profile
 * page are Telegram's own and behave exactly as they do everywhere else.
 *
 * Nothing is ever sent. The account behind it does not exist on any server; it exists in this
 * install, and the messages in it were written by this install.
 */
public final class TjAccountLogChat {

    /**
     * Far above anything Telegram has handed out, and never addressed to anyone: no request ever
     * carries it, so it cannot reach a real person even if the number is allocated one day.
     */
    public static final long USER_ID = 900000000001L;

    private static final String NEXT_ID = "account_log_next_id";

    private TjAccountLogChat() { }

    public static boolean is(long dialogId) {
        return dialogId == USER_ID;
    }

    /** Makes sure the account exists locally, so a message can have somebody to be from. */
    public static void ensure(int account) {
        if (UserConfig.getInstance(account).getClientUserId() == 0) {
            return;
        }
        MessagesController controller = MessagesController.getInstance(account);
        TLRPC.User existing = controller.getUser(USER_ID);
        String name = TjLocale.getString(R.string.TjAccountLogChatName);
        if (existing != null && TextUtils.equals(existing.first_name, name)) {
            return;
        }
        TLRPC.TL_user user = new TLRPC.TL_user();
        user.id = USER_ID;
        user.first_name = name;
        user.last_name = "";
        user.verified = true;
        user.access_hash = 0;
        user.status = null;
        controller.putUser(user, false);
        ArrayList<TLRPC.User> users = new ArrayList<>();
        users.add(user);
        MessagesStorage.getInstance(account).putUsersAndChats(users, null, true, true);
    }

    /**
     * Writes one message into the chat. Ids only ever go up, because a chat whose messages jump
     * around in time is a chat that draws itself wrong.
     */
    public static void send(int account, CharSequence text, ArrayList<TLRPC.MessageEntity> entities, int date) {
        if (text == null || text.length() == 0 || UserConfig.getInstance(account).getClientUserId() == 0) {
            return;
        }
        ensure(account);
        TLRPC.TL_message message = new TLRPC.TL_message();
        message.id = nextId(account);
        message.message = text.toString();
        message.date = date > 0 ? date : (int) (System.currentTimeMillis() / 1000L);
        message.out = false;
        message.unread = true;
        message.media_unread = false;
        message.dialog_id = USER_ID;
        message.peer_id = new TLRPC.TL_peerUser();
        message.peer_id.user_id = USER_ID;
        message.from_id = new TLRPC.TL_peerUser();
        message.from_id.user_id = USER_ID;
        message.media = new TLRPC.TL_messageMediaEmpty();
        if (entities != null && !entities.isEmpty()) {
            message.entities.addAll(entities);
            message.flags |= 128;
        }
        MessagesController.getInstance(account).tjPutLocalMessage(USER_ID, message);
    }

    private static int nextId(int account) {
        android.content.SharedPreferences preferences = ApplicationLoader.applicationContext
                .getSharedPreferences("tjsettings", android.content.Context.MODE_PRIVATE);
        String key = NEXT_ID + "_" + UserConfig.getInstance(account).getClientUserId();
        int id = Math.max(1, preferences.getInt(key, 1));
        preferences.edit().putInt(key, id + 1).apply();
        return id;
    }
}
