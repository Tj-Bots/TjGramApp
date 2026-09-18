package org.telegram.messenger.tj;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns what the server says about this account into messages in the account log chat.
 *
 * The shape is a log entry rather than a sentence: a tag that can be searched for, the chat as
 * both a number that can be copied and a link that can be opened, and the rights that moved listed
 * by their protocol names with a plus or a minus. Protocol names on purpose - they are what
 * Telegram's own tools use, they do not move between builds, and they do not need translating.
 */
public final class TjAccountEvents {

    public static final int TYPE_ADMIN_RIGHTS = 1;
    public static final int TYPE_RESTRICTED = 2;
    public static final int TYPE_MEMBERSHIP = 3;
    public static final int TYPE_NEW_DEVICE = 4;

    private static final String[] ADMIN = {
            "change_info", "post_messages", "edit_messages", "delete_messages", "ban_users",
            "invite_users", "pin_messages", "manage_call", "manage_topics", "anonymous",
            "add_admins", "post_stories", "edit_stories", "delete_stories", "manage_ranks", "other",
    };

    private static final String[] BANNED = {
            "view_messages", "send_messages", "send_media", "send_stickers", "send_gifs",
            "send_games", "send_inline", "embed_links", "send_polls", "change_info",
            "invite_users", "pin_messages", "manage_topics", "send_photos", "send_videos",
            "send_roundvideos", "send_audios", "send_voices", "send_docs", "send_plain",
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
        write(account, update.channel_id, update.actor_id, update.date,
                was, now, wasBanned, nowBanned, update.new_participant == null);
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
        write(account, chat.id, 0, 0, before.admin_rights, chat.admin_rights,
                before.banned_rights, chat.banned_rights, chat.left || chat.kicked);
    }

    /** A plain group reports far less: only whether you are an admin at all. */
    public static void onChatParticipantAdmin(int account, TL_update.TL_updateChatParticipantAdmin update) {
        if (update == null || update.user_id != UserConfig.getInstance(account).getClientUserId()
                || !wants(TYPE_ADMIN_RIGHTS)) {
            return;
        }
        Builder text = new Builder();
        text.tag(update.is_admin ? "#admin_rights" : "#removed_admin_rights");
        where(text, account, update.chat_id);
        TjAccountLogChat.send(account, text.text(), text.entities(), 0);
    }

    /** A device that signed in. Telegram sends this before it shows its own warning. */
    public static void onNewAuthorization(int account, TL_update.TL_updateNewAuthorization update) {
        if (update == null || !update.unconfirmed || !wants(TYPE_NEW_DEVICE)) {
            return;
        }
        Builder text = new Builder();
        text.tag("#new_device");
        text.blank();
        text.newLine();
        if (update.device != null && !update.device.isEmpty()) text.code(update.device);
        if (update.location != null && !update.location.isEmpty()) text.plain(update.location);
        TjAccountLogChat.send(account, text.text(), text.entities(), update.date);
    }

    /** One message for whichever of the three things this change turned out to be. */
    private static void write(int account, long chatId, long actorId, int date,
                              TLRPC.TL_chatAdminRights was, TLRPC.TL_chatAdminRights now,
                              TLRPC.TL_chatBannedRights wasBanned, TLRPC.TL_chatBannedRights nowBanned,
                              boolean gone) {
        Builder text = new Builder();
        if (was != null || now != null) {
            if (!wants(TYPE_ADMIN_RIGHTS)) return;
            text.tag(now == null ? "#removed_admin_rights" : "#admin_rights");
            where(text, account, chatId);
            by(text, account, actorId);
            changes(text, ADMIN, was, now, false);
        } else if (wasBanned != null || nowBanned != null) {
            if (!wants(TYPE_RESTRICTED)) return;
            text.tag(nowBanned == null ? "#unrestricted"
                    : nowBanned.view_messages ? "#kicked" : "#restricted");
            where(text, account, chatId);
            by(text, account, actorId);
            // A banned right is a thing taken away, so it is read the way it is felt: a plus is a
            // thing you may do again, a minus is a thing you no longer may.
            changes(text, BANNED, wasBanned, nowBanned, true);
        } else {
            if (!wants(TYPE_MEMBERSHIP)) return;
            text.tag(gone ? "#left" : "#joined");
            where(text, account, chatId);
            by(text, account, actorId);
        }
        TjAccountLogChat.send(account, text.text(), text.entities(), date);
    }

    /**
     * Which chat: the id as Telegram writes it, the title as a link into it, and the id again as a
     * tag, so a search for one chat finds every entry about it.
     */
    private static void where(Builder text, int account, long chatId) {
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(chatId);
        boolean channel = chat != null && ChatObject.isChannel(chat);
        // A line of its own under the tag, the way a log entry reads.
        text.blank();
        text.newLine();
        text.code(channel ? "-100" + chatId : "-" + chatId);
        String title = chat == null || chat.title == null ? "" : chat.title;
        if (!title.isEmpty()) {
            text.newLine();
            text.bold("chat");
            text.append(" ");
            if (channel) text.link(title, "https://t.me/c/" + chatId + "/1"); else text.append(title);
        }
        text.newLine();
        text.quote("#id_" + chatId);
    }

    /** Who did it, when the server said, with the two ways of opening them from a message. */
    private static void by(Builder text, int account, long actorId) {
        if (actorId == 0 || actorId == UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        TLRPC.User actor = MessagesController.getInstance(account).getUser(actorId);
        String name = actor == null ? "" : ContactsController.formatName(actor.first_name, actor.last_name);
        text.newLine();
        text.bold("by");
        if (!name.isEmpty()) text.append(" " + name);
        text.append(" ");
        text.code(String.valueOf(actorId));
        text.append(" [");
        text.link("iOS", "https://t.me/@id" + actorId);
        text.append(" | ");
        text.link("Android", "tg://openmessage?user_id=" + actorId);
        text.append("]");
    }

    /** Only what moved, each with the sign of which way it went, inside one quote. */
    private static void changes(Builder text, String[] table, Object was, Object now, boolean invert) {
        ArrayList<String> lines = new ArrayList<>();
        for (String field : table) {
            boolean before = read(was, field) != invert;
            boolean after = read(now, field) != invert;
            if (before != after) lines.add((after ? "+ " : "- ") + field);
        }
        if (lines.isEmpty()) {
            return;
        }
        text.blank();
        text.newLine();
        text.bold("Changes");
        text.newLine();
        text.quoteLines(lines);
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
        for (String field : ADMIN) text.append(read(chat.admin_rights, field) ? '1' : '0');
        text.append('|');
        for (String field : BANNED) text.append(read(chat.banned_rights, field) ? '1' : '0');
        return text.toString();
    }

    /**
     * Text and the marks on it, built together so the offsets cannot drift apart. Offsets are in
     * the units Java counts strings in, which is what the entities want.
     */
    private static final class Builder {
        private final StringBuilder text = new StringBuilder();
        private final ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();

        void append(String value) { text.append(value); }
        void newLine() { text.append('\n'); }
        void blank() { if (text.length() > 0) text.append('\n'); }
        void plain(String value) { newLine(); text.append(value); }

        void tag(String value) { bold(value); }

        void bold(String value) {
            TLRPC.TL_messageEntityBold entity = new TLRPC.TL_messageEntityBold();
            mark(entity, value);
        }

        void code(String value) {
            TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
            mark(entity, value);
        }

        void quote(String value) {
            TLRPC.TL_messageEntityBlockquote entity = new TLRPC.TL_messageEntityBlockquote();
            mark(entity, value);
        }

        /** One quote around several lines, each line its own monospace run. */
        void quoteLines(ArrayList<String> lines) {
            int start = text.length();
            for (int a = 0; a < lines.size(); a++) {
                if (a > 0) text.append('\n');
                String line = lines.get(a);
                text.append(line, 0, 2);
                TLRPC.TL_messageEntityCode code = new TLRPC.TL_messageEntityCode();
                code.offset = text.length();
                code.length = line.length() - 2;
                text.append(line, 2, line.length());
                entities.add(code);
            }
            TLRPC.TL_messageEntityBlockquote quote = new TLRPC.TL_messageEntityBlockquote();
            quote.offset = start;
            quote.length = text.length() - start;
            entities.add(quote);
        }

        void link(String value, String url) {
            TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
            entity.url = url;
            mark(entity, value);
        }

        private void mark(TLRPC.MessageEntity entity, String value) {
            entity.offset = text.length();
            entity.length = value.length();
            text.append(value);
            entities.add(entity);
        }

        CharSequence text() { return text; }
        ArrayList<TLRPC.MessageEntity> entities() { return entities; }
    }
}
