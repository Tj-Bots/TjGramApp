package org.telegram.ui;

import android.text.TextUtils;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * The shortcut row at the bottom of a message's menu.
 *
 * The menu is one long list, and the handful of actions people actually use every day sit
 * scattered through it. These few are lifted out into a row of icons instead - they leave the list
 * so nothing is offered twice, and the row keeps the order they were picked in.
 */
public final class TjMessageMenu {

    /** Actions that may be put in the row, in the order the chooser lists them. */
    public static final int[] ACTIONS = {
            ChatActivity.OPTION_FORWARD_NO_TAG,
            ChatActivity.OPTION_FORWARD,
            ChatActivity.OPTION_COPY_LINK,
            ChatActivity.OPTION_COPY,
            ChatActivity.OPTION_REPLY,
            ChatActivity.OPTION_EDIT,
            ChatActivity.OPTION_SAVE_TO_SAVED,
            ChatActivity.OPTION_PIN,
            ChatActivity.OPTION_DELETE,
    };

    /** Room for a comfortable tap target across a phone-width menu. */
    public static final int MAX_SHORTCUTS = 5;

    private static final String DEFAULT = ChatActivity.OPTION_FORWARD_NO_TAG + ","
            + ChatActivity.OPTION_FORWARD + ","
            + ChatActivity.OPTION_COPY_LINK + ","
            + ChatActivity.OPTION_COPY;

    private TjMessageMenu() {
    }

    public static boolean enabled() {
        return TjConfig.menuShortcuts();
    }

    public static void setEnabled(boolean value) {
        TjConfig.put("menu_shortcuts", value);
    }

    /** The chosen actions, in the order they will appear. */
    public static ArrayList<Integer> selected() {
        ArrayList<Integer> result = new ArrayList<>();
        String stored = TjConfig.menuShortcutActions(DEFAULT);
        for (String part : stored.split(",")) {
            part = part.trim();
            if (TextUtils.isEmpty(part)) {
                continue;
            }
            try {
                int option = Integer.parseInt(part);
                if (isAvailable(option) && !result.contains(option)) {
                    result.add(option);
                }
            } catch (NumberFormatException ignore) {
            }
        }
        return result;
    }

    public static void setSelected(List<Integer> actions) {
        StringBuilder value = new StringBuilder();
        for (Integer action : actions) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(action);
        }
        TjConfig.put("menu_shortcut_actions", value.toString());
    }

    public static boolean isAvailable(int option) {
        for (int action : ACTIONS) {
            if (action == option) {
                return true;
            }
        }
        return false;
    }

    public static String label(int option) {
        switch (option) {
            case ChatActivity.OPTION_FORWARD_NO_TAG:
                return TjLocale.getString(R.string.TjForwardWithoutTag);
            case ChatActivity.OPTION_FORWARD:
                return LocaleController.getString(R.string.Forward);
            case ChatActivity.OPTION_COPY_LINK:
                return LocaleController.getString(R.string.CopyLink);
            case ChatActivity.OPTION_COPY:
                return LocaleController.getString(R.string.Copy);
            case ChatActivity.OPTION_REPLY:
                return LocaleController.getString(R.string.Reply);
            case ChatActivity.OPTION_EDIT:
                return LocaleController.getString(R.string.Edit);
            case ChatActivity.OPTION_SAVE_TO_SAVED:
                return TjLocale.getString(R.string.TjSaveToSaved);
            case ChatActivity.OPTION_PIN:
                return LocaleController.getString(R.string.PinMessage);
            case ChatActivity.OPTION_DELETE:
                return LocaleController.getString(R.string.Delete);
        }
        return "";
    }

    /**
     * Positions in a menu's option list that belong in the row, in the chosen order. Only actions
     * this particular message actually offers are taken - the row never invents an action.
     */
    public static ArrayList<Integer> shortcutIndexes(List<Integer> options) {
        ArrayList<Integer> indexes = new ArrayList<>();
        if (!enabled() || options == null || options.isEmpty()) {
            return indexes;
        }
        for (Integer action : selected()) {
            int index = options.indexOf(action);
            if (index >= 0) {
                indexes.add(index);
            }
            if (indexes.size() >= MAX_SHORTCUTS) {
                break;
            }
        }
        // A row of one is not a row; that action is better left where it was.
        return indexes.size() < 2 ? new ArrayList<>() : indexes;
    }
}
