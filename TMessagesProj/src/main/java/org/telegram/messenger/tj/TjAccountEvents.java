package org.telegram.messenger.tj;

import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns what the server says about this account into messages in the account log chat.
 *
 * The text is written once, here, with its own formatting - the chat it happened in is a link, the
 * headline is bold, and each right is shown with a tick or a cross. From there it is an ordinary
 * message and nothing downstream needs to know where it came from.
 */
public final class TjAccountEvents {

    public static final int TYPE_ADMIN_RIGHTS = 1;
    public static final int TYPE_RESTRICTED = 2;
    public static final int TYPE_MEMBERSHIP = 3;
    public static final int TYPE_NEW_DEVICE = 4;

    /** A right, and the words the app already uses for it. */
    private static final class Right {
        final String field;
        final int name;
        Right(String field, int name) { this.field = field; this.name = name; }
    }

    private static final Right[] ADMIN = {
            new Right("change_info", R.string.EditAdminChangeChannelInfo),
            new Right("post_messages", R.string.EditAdminPostMessages),
            new Right("edit_messages", R.string.EditAdminEditMessages),
            new Right("delete_messages", R.string.EditAdminDeleteMessages),
            new Right("ban_users", R.string.EditAdminBanUsers),
            new Right("invite_users", R.string.EditAdminAddUsers),
            new Right("pin_messages", R.string.EditAdminPinMessages),
            new Right("manage_call", R.string.StartVoipChatPermission),
            new Right("manage_topics", R.string.ManageTopicsPermission),
            new Right("anonymous", R.string.EditAdminSendAnonymously),
            new Right("add_admins", R.string.EditAdminAddAdmins),
    };

    private static final Right[] BANNED = {
            new Right("send_messages", R.string.UserRestrictionsSend),
            new Right("send_media", R.string.UserRestrictionsSendMedia),
            new Right("send_stickers", R.string.UserRestrictionsSendStickers),
            new Right("embed_links", R.string.UserRestrictionsEmbedLinks),
            new Right("send_polls", R.string.UserRestrictionsSendPolls),
            new Right("change_info", R.string.UserRestrictionsChangeInfo),
            new Right("invite_users", R.string.UserRestrictionsInviteUsers),
            new Right("pin_messages", R.string.UserRestrictionsPinMessages),
    };

    /** What this account could do in a chat, as last seen, so a change can be noticed at all. */
    private static final ConcurrentHashMap<String, String> lastRights = new ConcurrentHashMap<>();

    private TjAccountEvents() { }

    private static boolean wants(int type) {
        return TjConfig.accountLog() && TjConfig.accountLogType(type);
    }

    /** A change the server bothered to attribute: this one knows who made it. */
    public static void onChannelParticipant(int account, TL_update.TL_updateChannelParticipant update) {
        if (update == null || update.user_id != UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        TLRPC.TL_chatAdminRights was = update.prev_participant == null ? null : update.prev_participant.admin_rights;
        TLRPC.TL_chatAdminRights now = update.new_participant == null ? null : update.new_participant.admin_rights;
        TLRPC.TL_chatBannedRights wasBanned = update.prev_participant == null ? null : update.prev_participant.banned_rights;
        TLRPC.TL_chatBannedRights nowBanned = update.new_participant == null ? null : update.new_participant.banned_rights;
        write(account, -update.channel_id, update.actor_id, update.date, was, now, wasBanned, nowBanned,
                update.new_participant == null);
    }

    /**
     * The safety net, and in practice the one that fires.
     *
     * The attributed update is only sent to people the server thinks should see participant
     * changes, so the person it is about often never gets it - being demoted can arrive as nothing
     * more than a fresh copy of the chat with different rights on it. Every chat object carries
     * this account's own rights, so they are compared against the last ones seen. What is lost
     * this way is the name of whoever did it, which is better than losing the event.
     */
    public static void onChatRights(int account, TLRPC.Chat oldChat, TLRPC.Chat chat) {
        if (chat == null || chat.min || !TjConfig.accountLog()) {
            return;
        }
        String signature = signature(chat);
        String previous = lastRights.put(account + ":" + chat.id, signature);
        if (previous == null || previous.equals(signature)) {
            return;
        }
        TLRPC.Chat before = oldChat == null ? chat : oldChat;
        write(account, -chat.id, 0, 0, before.admin_rights, chat.admin_rights,
                before.banned_rights, chat.banned_rights, chat.left || chat.kicked);
    }

    /** A plain group reports far less: only whether you are an admin at all. */
    public static void onChatParticipantAdmin(int account, TL_update.TL_updateChatParticipantAdmin update) {
        if (update == null || update.user_id != UserConfig.getInstance(account).getClientUserId()
                || !wants(TYPE_ADMIN_RIGHTS)) {
            return;
        }
        Builder text = new Builder();
        text.bold(TjLocale.getString(update.is_admin
                ? R.string.TjAccountLogAdminGranted : R.string.TjAccountLogAdminRemoved));
        chatLine(text, account, -update.chat_id);
        TjAccountLogChat.send(account, text.text(), text.entities(), 0);
    }

    /** A device that signed in. Telegram sends this before it shows its own warning. */
    public static void onNewAuthorization(int account, TL_update.TL_updateNewAuthorization update) {
        if (update == null || !update.unconfirmed || !wants(TYPE_NEW_DEVICE)) {
            return;
        }
        Builder text = new Builder();
        text.bold(TjLocale.getString(R.string.TjAccountLogNewDevice));
        if (update.device != null && !update.device.isEmpty()) text.line(update.device);
        if (update.location != null && !update.location.isEmpty()) text.line(update.location);
        TjAccountLogChat.send(account, text.text(), text.entities(), update.date);
    }

    /** One message for whichever of the three things this change turned out to be. */
    private static void write(int account, long dialogId, long actorId, int date,
                              TLRPC.TL_chatAdminRights was, TLRPC.TL_chatAdminRights now,
                              TLRPC.TL_chatBannedRights wasBanned, TLRPC.TL_chatBannedRights nowBanned,
                              boolean gone) {
        if (was != null || now != null) {
            if (!wants(TYPE_ADMIN_RIGHTS)) return;
            Builder text = new Builder();
            text.bold(TjLocale.getString(now == null ? R.string.TjAccountLogAdminRemoved
                    : was == null ? R.string.TjAccountLogAdminGranted : R.string.TjAccountLogAdminChanged));
            chatLine(text, account, dialogId);
            actorLine(text, account, actorId);
            rights(text, ADMIN, was, now, false);
            TjAccountLogChat.send(account, text.text(), text.entities(), date);
            return;
        }
        if (wasBanned != null || nowBanned != null) {
            if (!wants(TYPE_RESTRICTED)) return;
            Builder text = new Builder();
            text.bold(TjLocale.getString(nowBanned == null ? R.string.TjAccountLogLifted
                    : nowBanned.view_messages ? R.string.TjAccountLogBanned : R.string.TjAccountLogRestricted));
            chatLine(text, account, dialogId);
            actorLine(text, account, actorId);
            // A banned right is something taken away, so it is shown the way it is felt: what is
            // still allowed is a tick, what was taken is a cross.
            rights(text, BANNED, wasBanned, nowBanned, true);
            TjAccountLogChat.send(account, text.text(), text.entities(), date);
            return;
        }
        if (!wants(TYPE_MEMBERSHIP)) return;
        Builder text = new Builder();
        text.bold(TjLocale.getString(gone ? R.string.TjAccountLogLeft : R.string.TjAccountLogJoined));
        chatLine(text, account, dialogId);
        actorLine(text, account, actorId);
        TjAccountLogChat.send(account, text.text(), text.entities(), date);
    }

    /** The chat it happened in, as a link that opens it. */
    private static void chatLine(Builder text, int account, long dialogId) {
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        String name = chat == null || chat.title == null ? "" : chat.title;
        if (name.isEmpty()) return;
        text.link(name, "https://t.me/id/" + (-dialogId));
    }

    /** Who did it, when the server said. When it did not, the line is simply not there. */
    private static void actorLine(Builder text, int account, long actorId) {
        if (actorId == 0 || actorId == UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        TLRPC.User actor = MessagesController.getInstance(account).getUser(actorId);
        String name = actor == null ? "" : ContactsController.formatName(actor.first_name, actor.last_name);
        if (name.isEmpty()) return;
        text.line(TjLocale.formatString(R.string.TjAccountLogBy, name));
    }

    /**
     * What is on now, with the ones that moved gathered at the top. Showing the whole list is what
     * makes the change readable: a tick next to a cross says more than a sentence about either.
     */
    private static void rights(Builder text, Right[] table, Object was, Object now, boolean invert) {
        ArrayList<String> changed = new ArrayList<>(), on = new ArrayList<>(), off = new ArrayList<>();
        for (Right right : table) {
            boolean before = read(was, right.field) != invert;
            boolean after = read(now, right.field) != invert;
            String name = TjLocale.getString(right.name);
            if (after) on.add(name); else off.add(name);
            if (before != after) changed.add((after ? "✅ " : "❌ ") + name);
        }
        if (!changed.isEmpty()) {
            text.blank();
            text.bold(TjLocale.getString(R.string.TjAccountLogChanged));
            for (String line : changed) text.line(line);
        }
        if (on.isEmpty() && off.isEmpty()) {
            return;
        }
        text.blank();
        text.bold(TjLocale.getString(R.string.TjAccountLogNow));
        for (String name : on) text.line("✅ " + name);
        for (String name : off) text.line("❌ " + name);
    }

    private static boolean read(Object rights, String field) {
        if (rights == null) return false;
        try {
            return rights.getClass().getField(field).getBoolean(rights);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String signature(TLRPC.Chat chat) {
        StringBuilder text = new StringBuilder(chat.left ? "l" : "-").append(chat.kicked ? "k" : "-").append('|');
        for (Right right : ADMIN) text.append(read(chat.admin_rights, right.field) ? '1' : '0');
        text.append('|');
        for (Right right : BANNED) text.append(read(chat.banned_rights, right.field) ? '1' : '0');
        return text.append(chat.banned_rights != null && chat.banned_rights.view_messages ? 'v' : '-').toString();
    }

    /** Text and the marks on it, built together so the offsets cannot drift apart. */
    private static final class Builder {
        private final StringBuilder text = new StringBuilder();
        private final ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();

        void line(String value) {
            if (text.length() > 0) text.append('\n');
            text.append(value);
        }

        void blank() {
            if (text.length() > 0) text.append('\n');
        }

        void bold(String value) {
            int start = text.length() > 0 ? text.length() + 1 : 0;
            line(value);
            TLRPC.TL_messageEntityBold entity = new TLRPC.TL_messageEntityBold();
            entity.offset = start;
            entity.length = value.length();
            entities.add(entity);
        }

        void link(String value, String url) {
            int start = text.length() > 0 ? text.length() + 1 : 0;
            line(value);
            TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
            entity.offset = start;
            entity.length = value.length();
            entity.url = url;
            entities.add(entity);
        }

        CharSequence text() { return text; }
        ArrayList<TLRPC.MessageEntity> entities() { return entities; }
    }
}
