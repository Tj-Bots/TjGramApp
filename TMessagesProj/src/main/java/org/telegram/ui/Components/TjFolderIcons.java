package org.telegram.ui.Components;

import android.text.SpannableStringBuilder;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.TjSettingsActivity;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Icons for chat folder tabs. The emoji -> icon table comes from Nekogram by way of exteraGram.
 *
 * Telegram's wire format still carries an emoticon on TL_dialogFilter, but this client's
 * DialogFilter model drops it and the folders table has no column for it, so the chosen icon is
 * kept in our own preferences instead. Folders the user never assigned an icon to fall back to
 * one derived from the folder's own include/exclude flags (unread, groups, channels, bots...).
 */
public class TjFolderIcons {

    private static final Map<String, Integer> ICONS = new LinkedHashMap<>();

    /** Reserved for the main "All chats" folder. */
    public static final String ALL_CHATS = "\uD83D\uDCAC";
    /** The crown, used by the dynamic local Managing folder. */
    public static final String MANAGING = "\uD83D\uDC51";

    static {
        ICONS.put("\uD83D\uDC31", R.drawable.filter_cat);
        ICONS.put("\uD83D\uDCD5", R.drawable.filter_book);
        ICONS.put("\uD83D\uDCB0", R.drawable.filter_money);
        ICONS.put("\uD83C\uDFAE", R.drawable.filter_game);
        ICONS.put("\uD83D\uDCA1", R.drawable.filter_light);
        ICONS.put("\uD83D\uDC4C", R.drawable.filter_like);
        ICONS.put("\uD83C\uDFB5", R.drawable.filter_note);
        ICONS.put("\uD83C\uDFA8", R.drawable.filter_palette);
        ICONS.put("\u2708", R.drawable.filter_travel);
        ICONS.put("\u26BD", R.drawable.filter_sport);
        ICONS.put("\u2B50", R.drawable.filter_favorite);
        ICONS.put("\uD83C\uDF93", R.drawable.filter_study);
        ICONS.put("\uD83D\uDEEB", R.drawable.filter_airplane);
        ICONS.put("\uD83D\uDC64", R.drawable.filter_private);
        ICONS.put("\uD83D\uDC65", R.drawable.filter_group);
        ICONS.put("\uD83D\uDCAC", R.drawable.filter_all);
        ICONS.put("\u2705", R.drawable.filter_unread);
        ICONS.put("\uD83E\uDD16", R.drawable.filter_bots);
        ICONS.put("\uD83D\uDC51", R.drawable.filter_crown);
        ICONS.put("\uD83C\uDF39", R.drawable.filter_flower);
        ICONS.put("\uD83C\uDFE0", R.drawable.filter_home);
        ICONS.put("\u2764", R.drawable.filter_love);
        ICONS.put("\uD83C\uDFAD", R.drawable.filter_mask);
        ICONS.put("\uD83C\uDF78", R.drawable.filter_party);
        ICONS.put("\uD83D\uDCC8", R.drawable.filter_trade);
        ICONS.put("\uD83D\uDCBC", R.drawable.filter_work);
        ICONS.put("\uD83D\uDD14", R.drawable.filter_unmuted);
        ICONS.put("\uD83D\uDCE2", R.drawable.filter_channels);
        ICONS.put("\uD83D\uDCC1", R.drawable.filter_custom);
        ICONS.put("\uD83D\uDCCB", R.drawable.filter_setup);
    }

    /** Tab style, shared with TjSettingsActivity. */
    public static final int STYLE_ICON_AND_NAME = 0;
    public static final int STYLE_ICON_ONLY = 1;
    public static final int STYLE_NAME_ONLY = 2;

    public static String[] emoticons() {
        return ICONS.keySet().toArray(new String[0]);
    }

    public static int getTabIcon(String emoticon) {
        Integer res = emoticon == null ? null : ICONS.get(emoticon);
        return res != null ? res : R.drawable.filter_custom;
    }

    public static String getEmoticonFromFlags(int newFilterFlags) {
        int flags = newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS;
        String newEmoticon = "";
        if ((flags & MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) == MessagesController.DIALOG_FILTER_FLAG_ALL_CHATS) {
            if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_READ) != 0) {
                newEmoticon = "\u2705";
            } else if ((newFilterFlags & MessagesController.DIALOG_FILTER_FLAG_EXCLUDE_MUTED) != 0) {
                newEmoticon = "\uD83D\uDD14";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CONTACTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_CONTACTS;
            if (flags == 0) {
                newEmoticon = "\uD83D\uDC64";
            } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
                flags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
                if (flags == 0) {
                    newEmoticon = "\uD83D\uDC64";
                }
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_NON_CONTACTS;
            if (flags == 0) {
                newEmoticon = "\uD83D\uDC64";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_GROUPS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_GROUPS;
            if (flags == 0) {
                newEmoticon = "\uD83D\uDC65";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_BOTS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_BOTS;
            if (flags == 0) {
                newEmoticon = "\uD83E\uDD16";
            }
        } else if ((flags & MessagesController.DIALOG_FILTER_FLAG_CHANNELS) != 0) {
            flags &= ~MessagesController.DIALOG_FILTER_FLAG_CHANNELS;
            if (flags == 0) {
                newEmoticon = "\uD83D\uDCE2";
            }
        }
        return TextUtils.isEmpty(newEmoticon) ? null : newEmoticon;
    }

    public static String getFolderEmoticon(MessagesController.DialogFilter filter) {
        if (filter == null) {
            return null;
        }
        // The main folder always keeps the same icon - it is not user-changeable.
        if (filter.isDefault()) {
            return ALL_CHATS;
        }
        String saved = TjSettingsActivity.getFolderEmoticon(UserConfig.selectedAccount, filter.id);
        if (!TextUtils.isEmpty(saved)) {
            return saved;
        }
        if (filter.id == org.telegram.messenger.tj.TjLocalFolders.MANAGING) return MANAGING;
        if (filter.id == org.telegram.messenger.tj.TjLocalFolders.FAVORITES) return "⭐";
        return getEmoticonFromFlags(filter.flags);
    }

    /**
     * Prepends the folder's icon to its tab title. Going through an ImageSpan means the existing
     * measuring, animation and drawing in FilterTabsView keep working untouched, and the span
     * takes the paint's colour so the icon fades with the tab's selected state.
     */
    /** Icon side, in pixels. A touch larger than the text so the tabs read clearly. */
    public static int getIconSize() {
        return AndroidUtilities.dp(22);
    }

    public static boolean showsTitle() {
        return TjSettingsActivity.getFolderTabStyle() != STYLE_ICON_ONLY;
    }

    public static boolean showsIcon() {
        return TjSettingsActivity.getFolderTabStyle() != STYLE_NAME_ONLY;
    }

    /** Width this folder's icon needs inside a tab, gap included. Zero when it has none. */
    public static int getTotalIconWidth(String emoticon) {
        if (emoticon == null || !showsIcon()) {
            return 0;
        }
        return getIconSize() + (showsTitle() ? AndroidUtilities.dp(6) : 0);
    }

    /**
     * Tab label: the folder name, or nothing at all in icon-only mode. Returns a String because
     * FilterTabsView.Tab.setTitle takes one, and the main tab's title is re-assigned there.
     */
    public static String tabTitle(String name) {
        return showsTitle() ? name : "";
    }
}
