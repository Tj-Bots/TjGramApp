package org.telegram.messenger.tj;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_update;

import java.util.ArrayList;

/**
 * Turns the updates the server already sends into entries for the account log.
 *
 * Names are written down as they are at the moment it happens: a chat you are later removed from,
 * or a person you never look up again, still has to read as something other than a number a month
 * from now.
 */
public final class TjAccountEvents {

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

    private TjAccountEvents() { }

    /** A change to what this account may do in a channel or supergroup, and who made it. */
    public static void onChannelParticipant(int account, TL_update.TL_updateChannelParticipant update) {
        if (update == null || update.user_id != UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        TLRPC.TL_chatAdminRights was = adminRights(update.prev_participant);
        TLRPC.TL_chatAdminRights now = adminRights(update.new_participant);
        TLRPC.TL_chatBannedRights wasBanned = bannedRights(update.prev_participant);
        TLRPC.TL_chatBannedRights nowBanned = bannedRights(update.new_participant);

        if (was != null || now != null) {
            JSONObject payload = base(account, -update.channel_id, update.actor_id);
            put(payload, "state", now == null ? "removed" : was == null ? "granted" : "changed");
            rights(payload, ADMIN, was, now, false);
            TjAccountLog.getInstance().record(account, TjAccountLog.TYPE_ADMIN_RIGHTS, update.date, payload);
            return;
        }
        if (wasBanned != null || nowBanned != null) {
            JSONObject payload = base(account, -update.channel_id, update.actor_id);
            boolean kicked = nowBanned != null && nowBanned.view_messages;
            put(payload, "state", nowBanned == null ? "lifted" : kicked ? "banned" : "restricted");
            if (nowBanned != null && nowBanned.until_date > 0) {
                put(payload, "until", nowBanned.until_date);
            }
            // A banned right is a thing taken away, so it is shown the way it is felt: what is
            // still allowed is on, what was taken is off.
            rights(payload, BANNED, wasBanned, nowBanned, true);
            TjAccountLog.getInstance().record(account, TjAccountLog.TYPE_RESTRICTED, update.date, payload);
            return;
        }
        JSONObject payload = base(account, -update.channel_id, update.actor_id);
        put(payload, "state", update.new_participant == null ? "left" : "joined");
        TjAccountLog.getInstance().record(account, TjAccountLog.TYPE_MEMBERSHIP, update.date, payload);
    }

    /** The same thing in a plain group, which reports far less: only whether you are an admin. */
    public static void onChatParticipantAdmin(int account, TL_update.TL_updateChatParticipantAdmin update) {
        if (update == null || update.user_id != UserConfig.getInstance(account).getClientUserId()) {
            return;
        }
        JSONObject payload = base(account, -update.chat_id, 0);
        put(payload, "state", update.is_admin ? "granted" : "removed");
        TjAccountLog.getInstance().record(account, TjAccountLog.TYPE_ADMIN_RIGHTS, 0, payload);
    }

    /** A device that signed in. Telegram sends this before it shows its own warning. */
    public static void onNewAuthorization(int account, TL_update.TL_updateNewAuthorization update) {
        if (update == null || !update.unconfirmed) {
            return;
        }
        JSONObject payload = new JSONObject();
        put(payload, "device", update.device == null ? "" : update.device);
        put(payload, "location", update.location == null ? "" : update.location);
        TjAccountLog.getInstance().record(account, TjAccountLog.TYPE_NEW_DEVICE, update.date, payload);
    }

    /** The chat it happened in and the person who did it, named now rather than looked up later. */
    private static JSONObject base(int account, long dialogId, long actorId) {
        JSONObject payload = new JSONObject();
        put(payload, "chat", dialogId);
        TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
        put(payload, "chatName", chat == null || chat.title == null ? "" : chat.title);
        if (actorId != 0 && actorId != UserConfig.getInstance(account).getClientUserId()) {
            put(payload, "actor", actorId);
            TLRPC.User actor = MessagesController.getInstance(account).getUser(actorId);
            put(payload, "actorName", actor == null ? ""
                    : ContactsController.formatName(actor.first_name, actor.last_name));
        }
        return payload;
    }

    /**
     * What is on now, and which of those changed. Both lists are kept so the entry can show the
     * whole picture and still point at what moved.
     */
    private static void rights(JSONObject payload, Right[] table, Object was, Object now, boolean invert) {
        JSONArray on = new JSONArray(), off = new JSONArray(), changed = new JSONArray();
        for (Right right : table) {
            boolean before = read(was, right.field) != invert;
            boolean after = read(now, right.field) != invert;
            String name = TjLocale.getString(right.name);
            if (after) on.put(name); else off.put(name);
            if (before != after) changed.put(name);
        }
        try {
            payload.put("on", on);
            payload.put("off", off);
            payload.put("changed", changed);
        } catch (Throwable ignored) {
        }
    }

    private static boolean read(Object rights, String field) {
        if (rights == null) return false;
        try {
            return rights.getClass().getField(field).getBoolean(rights);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TLRPC.TL_chatAdminRights adminRights(TLRPC.ChannelParticipant participant) {
        return participant == null ? null : participant.admin_rights;
    }

    private static TLRPC.TL_chatBannedRights bannedRights(TLRPC.ChannelParticipant participant) {
        return participant == null ? null : participant.banned_rights;
    }

    private static void put(JSONObject payload, String key, Object value) {
        try { payload.put(key, value); } catch (Throwable ignored) { }
    }

    /** The names of the rights this build knows about, for the settings screen to list. */
    public static ArrayList<String> adminRightNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Right right : ADMIN) names.add(TjLocale.getString(right.name));
        return names;
    }
}
