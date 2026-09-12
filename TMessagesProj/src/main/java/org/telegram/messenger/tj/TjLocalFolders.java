package org.telegram.messenger.tj;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Device-local, account-owned folders. Never inserted into Telegram's cloud filter tables. */
public final class TjLocalFolders {
    public static final int FIRST_ID = 1_000_001;
    public static final int PRIVATE = FIRST_ID, GROUPS = FIRST_ID + 1, CHANNELS = FIRST_ID + 2,
            BOTS = FIRST_ID + 3, UNREAD = FIRST_ID + 4, UNMUTED = FIRST_ID + 5,
            FAVORITES = FIRST_ID + 6, MANAGING = FIRST_ID + 7;
    public static final int[] IDS = {PRIVATE, GROUPS, CHANNELS, BOTS, UNREAD, UNMUTED, FAVORITES, MANAGING};

    private TjLocalFolders() {}

    public static boolean isLocal(int id) { return id >= FIRST_ID && id <= MANAGING; }

    public static String title(int id) {
        int[] resources = {R.string.TjLocalPrivate, R.string.TjGroups, R.string.TjChannels,
                R.string.TjBots, R.string.TjLocalUnread, R.string.TjLocalUnmuted,
                R.string.TjLocalFavorites, R.string.TjFolderManaging};
        return TjLocale.getString(resources[id - FIRST_ID]);
    }

    public static String description(int id) {
        return TjLocale.getString(id == MANAGING ? R.string.TjLocalManagingInfo
                : id == FAVORITES ? R.string.TjLocalFavoritesInfo : R.string.TjLocalFolderInfo);
    }

    public static boolean offered(int account) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        return prefs == null || prefs.getBoolean("offered", false);
    }

    public static void markOffered(int account) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs != null) prefs.edit().putBoolean("offered", true).apply();
    }

    public static boolean enabled(int account, int id) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        // Missing preferences use the defaults; explicit user opt-outs remain untouched.
        return isLocal(id) && prefs != null && prefs.getBoolean("enabled_" + id, true);
    }

    public static void setEnabled(int account, int id, boolean enabled) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs == null || !isLocal(id)) return;
        prefs.edit().putBoolean("enabled_" + id, enabled).apply();
    }

    public static int cloudCount(int account) {
        int count = 0;
        for (MessagesController.DialogFilter filter : MessagesController.getInstance(account).dialogFilters) {
            if (!isLocal(filter.id)) count++;
        }
        return count;
    }

    public static boolean isManaged(TLRPC.Chat chat) {
        return chat != null && !chat.left && !chat.kicked && !chat.deactivated
                && ChatObject.hasAdminRights(chat);
    }

    private static class LocalFilter extends MessagesController.DialogFilter {
        @Override
        public boolean includesDialog(AccountInstance account, long dialogId, TLRPC.Dialog dialog) {
            if (id == MANAGING) {
                TLRPC.Chat chat = dialogId < 0 ? account.getMessagesController().getChat(-dialogId) : null;
                return isManaged(chat);
            }
            if (id == FAVORITES) return alwaysShow.contains(dialog.id);
            return super.includesDialog(account, dialogId, dialog);
        }
    }

    private static MessagesController.DialogFilter create(int account, int id) {
        LocalFilter filter = new LocalFilter();
        filter.id = id;
        filter.name = title(id);
        filter.color = -1;
        switch (id) {
            case PRIVATE: filter.flags = MessagesController.DIALOG_FILTER_FLAG_CONTACTS | MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS; break;
            case GROUPS: filter.flags = MessagesController.DIALOG_FILTER_FLAG_GROUPS; break;
            case CHANNELS: filter.flags = MessagesController.DIALOG_FILTER_FLAG_CHANNELS; break;
            case BOTS: filter.flags = MessagesController.DIALOG_FILTER_FLAG_BOTS; break;
            case UNREAD: filter.flags = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ; break;
            case UNMUTED: filter.flags = MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS | MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED; break;
        }
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs != null) {
            filter.name = prefs.getString("name_" + id, title(id));
            for (String value : prefs.getStringSet("peers_" + id, Collections.emptySet())) {
                try { filter.alwaysShow.add(Long.parseLong(value)); } catch (NumberFormatException ignored) {}
            }
            for (String value : prefs.getStringSet("pins_" + id, Collections.emptySet())) {
                String[] parts = value.split(":");
                if (parts.length == 2) {
                    try { filter.pinnedDialogs.put(Long.parseLong(parts[0]), Integer.parseInt(parts[1])); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return filter;
    }

    /** Called after cloud loading; reuse selected local objects so an open tab stays valid. */
    public static void attach(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        ArrayList<MessagesController.DialogFilter> combined = new ArrayList<>();
        for (MessagesController.DialogFilter filter : controller.dialogFilters) {
            if (!isLocal(filter.id)) combined.add(filter);
        }
        for (int id : IDS) {
            if (!enabled(account, id)) continue;
            MessagesController.DialogFilter filter = controller.dialogFiltersById.get(id);
            if (!(filter instanceof LocalFilter)) filter = create(account, id);
            SharedPreferences saved = TjConfig.localFolders(account);
            filter.name = saved == null ? title(id) : saved.getString("name_" + id, title(id));
            filter.locked = false;
            combined.add(filter);
        }
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs != null) {
            try {
                JSONArray order = new JSONArray(prefs.getString("order", "[]"));
                ArrayList<Integer> ids = new ArrayList<>();
                for (int i = 0; i < order.length(); i++) ids.add(order.getInt(i));
                // Persist local placement while preserving the current cloud-to-cloud order.
                ArrayList<MessagesController.DialogFilter> locals = new ArrayList<>(combined);
                combined.removeIf(f -> isLocal(f.id));
                for (int i = 0; i < ids.size(); i++) {
                    final int id = ids.get(i);
                    if (!isLocal(id)) continue;
                    for (MessagesController.DialogFilter filter : locals) {
                        if (filter.id == id) { combined.add(Math.min(i, combined.size()), filter); break; }
                    }
                }
                for (MessagesController.DialogFilter filter : locals) {
                    if (isLocal(filter.id) && !combined.contains(filter)) combined.add(filter);
                }
            } catch (Exception e) { FileLog.e(e); }
        }
        controller.dialogFilters = combined;
        controller.dialogFiltersById.clear();
        for (MessagesController.DialogFilter filter : combined) controller.dialogFiltersById.put(filter.id, filter);
        for (int i = 0; i < controller.selectedDialogFilter.length; i++) {
            MessagesController.DialogFilter selected = controller.selectedDialogFilter[i];
            if (selected != null) controller.selectedDialogFilter[i] = controller.dialogFiltersById.get(selected.id);
        }
    }

    public static void refresh(int account) {
        attach(account);
        MessagesController controller = MessagesController.getInstance(account);
        controller.sortDialogs(null);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static void save(int account, MessagesController.DialogFilter filter) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs == null) return;
        Set<String> peers = new HashSet<>();
        for (long peer : filter.alwaysShow) peers.add(String.valueOf(peer));
        Set<String> pins = new HashSet<>();
        for (int i = 0; i < filter.pinnedDialogs.size(); i++) {
            pins.add(filter.pinnedDialogs.keyAt(i) + ":" + filter.pinnedDialogs.valueAt(i));
        }
        SharedPreferences.Editor editor = prefs.edit().putStringSet("peers_" + filter.id, peers)
                .putStringSet("pins_" + filter.id, pins);
        if (title(filter.id).equals(filter.name)) editor.remove("name_" + filter.id);
        else editor.putString("name_" + filter.id, filter.name);
        editor.apply();
    }

    public static void saveOrder(int account) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs == null) return;
        JSONArray order = new JSONArray();
        for (MessagesController.DialogFilter filter : MessagesController.getInstance(account).dialogFilters) order.put(filter.id);
        prefs.edit().putString("order", order.toString()).apply();
    }

    public static void reset(int account) {
        SharedPreferences prefs = TjConfig.localFolders(account);
        if (prefs == null) return;
        prefs.edit().clear().putBoolean("offered", true).apply();
        refresh(account);
    }

    public static void updateCounters(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        for (MessagesController.DialogFilter filter : controller.dialogFilters) {
            if (!isLocal(filter.id)) continue;
            int count = 0;
            for (TLRPC.Dialog dialog : controller.getAllDialogs()) {
                if (dialog instanceof TLRPC.TL_dialog && filter.includesDialog(AccountInstance.getInstance(account), dialog.id, dialog)
                        && (dialog.unread_count > 0 || dialog.unread_mark)) count++;
            }
            filter.pendingUnreadCount = filter.unreadCount = count;
        }
    }
}
