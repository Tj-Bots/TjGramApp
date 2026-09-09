package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.TjFolderIcons;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjBackgroundConnection;
import org.telegram.messenger.tj.TjGhostController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class TjSettingsActivity extends BaseFragment {

    private static final String PREFS_NAME = "tjsettings";

    private static final String KEY_SHOW_CALL_BUTTON = "show_call_button";
    private static final String KEY_HIDE_PHONE_NUMBER = "hide_phone_number";
    private static final String KEY_BOT_API_IDS = "bot_api_ids";
    private static final String KEY_ACCOUNT_ORDER_PREFIX = "account_order_";
    private static final String KEY_GHOST_MODE = "ghost_mode";
    private static final String KEY_GHOST_TYPING = "ghost_hide_typing";
    private static final String KEY_GHOST_ONLINE = "ghost_hide_online";
    private static final String KEY_GHOST_READ = "ghost_hide_read";
    private static final String KEY_GHOST_FORCE_OFFLINE = "ghost_force_offline";
    private static final String KEY_GHOST_READ_AFTER_REPLY = "ghost_read_after_reply";
    private static final String KEY_GHOST_SCHEDULE_MESSAGES = "ghost_schedule_messages";
    private static final String KEY_GHOST_WARNED = "ghost_warning_dismissed";

    private static final String KEY_SUBTITLE_FONT_SIZE = "subtitle_font_size";
    private static final String KEY_SUBTITLE_STYLE = "subtitle_style";
    private static final String KEY_SUBTITLE_POSITION = "subtitle_position";
    private static final String KEY_SUBTITLE_AUTO = "subtitle_auto_enable";

    private static final String KEY_FOLDER_TAB_STYLE = "folder_tab_style";
    private static final String KEY_FOLDER_EMOTICON_PREFIX = "folder_emoticon_";

    private static final String KEY_MENU_MESSAGE_INFO = "menu_message_info";
    private static final String KEY_MENU_COPY_LINK = "menu_copy_message_link";
    private static final String KEY_MENU_COPY_IMAGE = "menu_copy_image";
    private static final String KEY_MENU_COPY_THUMB = "menu_copy_thumbnail";
    private static final String KEY_MENU_SAVE_TO_SAVED = "menu_save_to_saved";
    private static final String KEY_MENU_FORWARD_NO_TAG = "menu_forward_without_tag";
    private static final String KEY_MENU_REPLY_PRIVATELY = "menu_reply_privately";
    private static final String KEY_DELETE_FOR_BOTH = "delete_for_both_default";

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isShowCallButtonEnabled() {
        return getPrefs().getBoolean(KEY_SHOW_CALL_BUTTON, false);
    }

    public static void setShowCallButtonEnabled(boolean value) {
        getPrefs().edit().putBoolean(KEY_SHOW_CALL_BUTTON, value).apply();
    }

    public static boolean isHidePhoneNumberEnabled() {
        return getPrefs().getBoolean(KEY_HIDE_PHONE_NUMBER, false);
    }

    public static void setHidePhoneNumberEnabled(boolean value) {
        getPrefs().edit().putBoolean(KEY_HIDE_PHONE_NUMBER, value).apply();
    }

    /**
     * When enabled (the default) chat ids are shown the way the Bot API reports them:
     * -100... for supergroups and channels, -... for legacy groups.
     */
    public static boolean isBotApiIdsEnabled() {
        return getPrefs().getBoolean(KEY_BOT_API_IDS, true);
    }

    public static void setBotApiIdsEnabled(boolean value) {
        getPrefs().edit().putBoolean(KEY_BOT_API_IDS, value).apply();
    }

    /** One of TjFolderIcons.STYLE_*. Icon plus name by default. */
    /**
     * Ghost mode. The master switch is what the drawer toggles; the three sub-switches say what it
     * actually suppresses, and all three are on by default so the master switch alone does the
     * expected thing.
     */
    public static boolean isGhostModeEnabled() {
        return TjConfig.ghostEnabled();
    }

    public static void setGhostModeEnabled(boolean value) {
        getPrefs().edit().putBoolean(KEY_GHOST_MODE, value).apply();
    }

    public static boolean isGhostHideTyping() {
        return TjConfig.hideTyping();
    }

    public static boolean isGhostHideOnline() {
        return TjConfig.hideOnline();
    }

    public static boolean isGhostHideReadReceipts() {
        return TjConfig.hideReads();
    }

    public static boolean isGhostWarningDismissed() {
        return getPrefs().getBoolean(KEY_GHOST_WARNED, false);
    }

    public static void setGhostWarningDismissed(boolean value) {
        getPrefs().edit().putBoolean(KEY_GHOST_WARNED, value).apply();
    }

    public static final int SUBTITLE_STYLE_OUTLINE = 0;
    public static final int SUBTITLE_STYLE_SHADOW = 1;
    public static final int SUBTITLE_STYLE_BOX = 2;

    /** Sizes offered in the player's subtitle settings, in dp. */
    public static final int[] SUBTITLE_FONT_SIZES = {14, 18, 22, 26};

    public static int getSubtitleFontSize() {
        return getPrefs().getInt(KEY_SUBTITLE_FONT_SIZE, 18);
    }

    public static void setSubtitleFontSize(int size) {
        getPrefs().edit().putInt(KEY_SUBTITLE_FONT_SIZE, size).apply();
    }

    /**
     * Turn subtitles on by themselves, preferring a track in the app's own language and falling
     * back to English. Off by default; a track you pick by hand always wins.
     */
    public static boolean isSubtitleAutoEnabled() {
        return getPrefs().getBoolean(KEY_SUBTITLE_AUTO, false);
    }

    public static void setSubtitleAutoEnabled(boolean value) {
        getPrefs().edit().putBoolean(KEY_SUBTITLE_AUTO, value).apply();
    }

    /** How far up from the bottom of the picture subtitles sit, as a percentage of its height. */
    public static final int SUBTITLE_POSITION_MAX = 50;

    public static int getSubtitlePosition() {
        return Math.max(0, Math.min(SUBTITLE_POSITION_MAX, getPrefs().getInt(KEY_SUBTITLE_POSITION, 0)));
    }

    public static void setSubtitlePosition(int percent) {
        getPrefs().edit().putInt(KEY_SUBTITLE_POSITION, Math.max(0, Math.min(SUBTITLE_POSITION_MAX, percent))).apply();
    }

    public static int getSubtitleStyle() {
        return getPrefs().getInt(KEY_SUBTITLE_STYLE, SUBTITLE_STYLE_OUTLINE);
    }

    public static void setSubtitleStyle(int style) {
        getPrefs().edit().putInt(KEY_SUBTITLE_STYLE, style).apply();
    }

    public static int getFolderTabStyle() {
        return getPrefs().getInt(KEY_FOLDER_TAB_STYLE, 0);
    }

    public static void setFolderTabStyle(int style) {
        getPrefs().edit().putInt(KEY_FOLDER_TAB_STYLE, style).apply();
    }

    private static String getAccountFolderEmoticonKey(int account, int filterId) {
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        return KEY_FOLDER_EMOTICON_PREFIX + (ownerId != 0 ? "user_" + ownerId : "slot_" + account) + "_" + filterId;
    }

    public static String getFolderEmoticon(int account, int filterId) {
        SharedPreferences preferences = getPrefs();
        String accountKey = getAccountFolderEmoticonKey(account, filterId);
        if (preferences.contains(accountKey)) {
            return preferences.getString(accountKey, null);
        }
        String slotKey = KEY_FOLDER_EMOTICON_PREFIX + "slot_" + account + "_" + filterId;
        String legacy = preferences.getString(slotKey, null);
        if (legacy == null) {
            // Versions before account isolation stored either account_filter or just filter.
            legacy = preferences.getString(KEY_FOLDER_EMOTICON_PREFIX + account + "_" + filterId, null);
        }
        String legacyKey = KEY_FOLDER_EMOTICON_PREFIX + filterId;
        if (legacy == null) {
            legacy = preferences.getString(legacyKey, null);
        }
        if (legacy != null) {
            preferences.edit().putString(accountKey, legacy).apply();
        }
        return legacy;
    }

    public static void setFolderEmoticon(int account, int filterId, String emoticon) {
        String key = getAccountFolderEmoticonKey(account, filterId);
        if (emoticon == null) {
            getPrefs().edit().remove(key).apply();
        } else {
            getPrefs().edit().putString(key, emoticon).apply();
        }
    }

    public static boolean isMessageInfoEnabled() {
        return getPrefs().getBoolean(KEY_MENU_MESSAGE_INFO, true);
    }

    public static boolean isCopyMessageLinkEnabled() {
        return getPrefs().getBoolean(KEY_MENU_COPY_LINK, true);
    }

    public static boolean isCopyImageEnabled() {
        return getPrefs().getBoolean(KEY_MENU_COPY_IMAGE, true);
    }

    public static boolean isCopyThumbnailEnabled() {
        return getPrefs().getBoolean(KEY_MENU_COPY_THUMB, true);
    }

    /**
     * Tick "delete also for X" by default when deleting a message in a private chat. On by
     * default - deleting only your own copy is rarely what anyone means.
     */
    public static boolean isDeleteForBothDefault() {
        return getPrefs().getBoolean(KEY_DELETE_FOR_BOTH, true);
    }

    /** "Reply privately" in the message menu of a group. On by default. */
    public static boolean isReplyPrivatelyEnabled() {
        return getPrefs().getBoolean(KEY_MENU_REPLY_PRIVATELY, true);
    }

    public static boolean isSaveToSavedEnabled() {
        return getPrefs().getBoolean(KEY_MENU_SAVE_TO_SAVED, true);
    }

    public static boolean isForwardWithoutTagEnabled() {
        return getPrefs().getBoolean(KEY_MENU_FORWARD_NO_TAG, true);
    }

    /**
     * Position of an account in the side menu. Accounts that were never reordered keep
     * a large order so they stay after the ones the user moved around.
     */
    public static int getAccountOrder(int account) {
        return getPrefs().getInt(KEY_ACCOUNT_ORDER_PREFIX + account, 1000 + account);
    }

    public static void setAccountOrder(int account, int order) {
        getPrefs().edit().putInt(KEY_ACCOUNT_ORDER_PREFIX + account, order).apply();
    }

    private RecyclerListView listView;
    private ListAdapter adapter;
    private final ArrayList<Item> items = new ArrayList<>();

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHECK = 1;
    private static final int VIEW_TYPE_SHADOW = 2;
    private static final int VIEW_TYPE_SETTING = 3;

    private static final int ID_HIDE_PHONE = 1;
    private static final int ID_BOT_API_IDS = 2;
    private static final int ID_SHOW_CALL_BUTTON = 3;
    private static final int ID_MENU_MESSAGE_INFO = 4;
    private static final int ID_MENU_COPY_LINK = 5;
    private static final int ID_MENU_COPY_IMAGE = 6;
    private static final int ID_MENU_COPY_THUMB = 7;
    private static final int ID_MENU_SAVE_TO_SAVED = 8;
    private static final int ID_MENU_FORWARD_NO_TAG = 9;
    private static final int ID_FOLDER_TAB_STYLE = 10;
    private static final int ID_GHOST_MODE = 11;
    private static final int ID_GHOST_TYPING = 12;
    private static final int ID_GHOST_ONLINE = 13;
    private static final int ID_GHOST_READ = 14;
    private static final int ID_SUBTITLE_AUTO = 15;
    private static final int ID_MENU_REPLY_PRIVATELY = 16;
    private static final int ID_DELETE_FOR_BOTH = 17;
    private static final int ID_GHOST_FORCE_OFFLINE = 18;
    private static final int ID_GHOST_READ_AFTER_REPLY = 19;
    private static final int ID_GHOST_SCHEDULE_MESSAGES = 20;
    private static final int ID_PRIVACY_ARCHIVE = 21;
    private static final int ID_GHOST_SETTINGS = 22;
    private static final int ID_BACKGROUND_CONNECTION = 23;
    private static final int ID_BATTERY_OPTIMIZATION = 24;
    private static final int ID_ONLINE_INDICATOR = 25;

    private static class Item {
        final int viewType;
        final int id;
        final CharSequence text;

        Item(int viewType, int id, CharSequence text) {
            this.viewType = viewType;
            this.id = id;
            this.text = text;
        }
    }

    private static boolean isChecked(int id) {
        switch (id) {
            case ID_HIDE_PHONE: return isHidePhoneNumberEnabled();
            case ID_BOT_API_IDS: return isBotApiIdsEnabled();
            case ID_SHOW_CALL_BUTTON: return isShowCallButtonEnabled();
            case ID_MENU_MESSAGE_INFO: return isMessageInfoEnabled();
            case ID_MENU_COPY_LINK: return isCopyMessageLinkEnabled();
            case ID_MENU_COPY_IMAGE: return isCopyImageEnabled();
            case ID_MENU_COPY_THUMB: return isCopyThumbnailEnabled();
            case ID_MENU_SAVE_TO_SAVED: return isSaveToSavedEnabled();
            case ID_MENU_FORWARD_NO_TAG: return isForwardWithoutTagEnabled();
            case ID_GHOST_MODE: return isGhostModeEnabled();
            case ID_GHOST_TYPING: return getPrefs().getBoolean(KEY_GHOST_TYPING, true);
            case ID_GHOST_ONLINE: return getPrefs().getBoolean(KEY_GHOST_ONLINE, true);
            case ID_GHOST_READ: return getPrefs().getBoolean(KEY_GHOST_READ, true);
            case ID_GHOST_FORCE_OFFLINE: return getPrefs().getBoolean(KEY_GHOST_FORCE_OFFLINE, true);
            case ID_GHOST_READ_AFTER_REPLY: return getPrefs().getBoolean(KEY_GHOST_READ_AFTER_REPLY, false);
            case ID_GHOST_SCHEDULE_MESSAGES: return getPrefs().getBoolean(KEY_GHOST_SCHEDULE_MESSAGES, false);
            case ID_SUBTITLE_AUTO: return isSubtitleAutoEnabled();
            case ID_MENU_REPLY_PRIVATELY: return isReplyPrivatelyEnabled();
            case ID_DELETE_FOR_BOTH: return isDeleteForBothDefault();
            case ID_BACKGROUND_CONNECTION: return TjConfig.backgroundConnection();
            case ID_ONLINE_INDICATOR: return TjConfig.showOnlineIndicator();
        }
        return false;
    }

    private static void setChecked(int id, boolean value) {
        String key = null;
        switch (id) {
            case ID_HIDE_PHONE: key = KEY_HIDE_PHONE_NUMBER; break;
            case ID_BOT_API_IDS: key = KEY_BOT_API_IDS; break;
            case ID_SHOW_CALL_BUTTON: key = KEY_SHOW_CALL_BUTTON; break;
            case ID_MENU_MESSAGE_INFO: key = KEY_MENU_MESSAGE_INFO; break;
            case ID_MENU_COPY_LINK: key = KEY_MENU_COPY_LINK; break;
            case ID_MENU_COPY_IMAGE: key = KEY_MENU_COPY_IMAGE; break;
            case ID_MENU_COPY_THUMB: key = KEY_MENU_COPY_THUMB; break;
            case ID_MENU_SAVE_TO_SAVED: key = KEY_MENU_SAVE_TO_SAVED; break;
            case ID_MENU_FORWARD_NO_TAG: key = KEY_MENU_FORWARD_NO_TAG; break;
            case ID_GHOST_MODE: key = KEY_GHOST_MODE; break;
            case ID_GHOST_TYPING: key = KEY_GHOST_TYPING; break;
            case ID_GHOST_ONLINE: key = KEY_GHOST_ONLINE; break;
            case ID_GHOST_READ: key = KEY_GHOST_READ; break;
            case ID_GHOST_FORCE_OFFLINE: key = KEY_GHOST_FORCE_OFFLINE; break;
            case ID_GHOST_READ_AFTER_REPLY: key = KEY_GHOST_READ_AFTER_REPLY; break;
            case ID_GHOST_SCHEDULE_MESSAGES: key = KEY_GHOST_SCHEDULE_MESSAGES; break;
            case ID_SUBTITLE_AUTO: key = KEY_SUBTITLE_AUTO; break;
            case ID_MENU_REPLY_PRIVATELY: key = KEY_MENU_REPLY_PRIVATELY; break;
            case ID_DELETE_FOR_BOTH: key = KEY_DELETE_FOR_BOTH; break;
            case ID_BACKGROUND_CONNECTION: key = "background_connection"; break;
            case ID_ONLINE_INDICATOR: key = "show_online_indicator"; break;
        }
        if (key != null) {
            getPrefs().edit().putBoolean(key, value).apply();
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjSettings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        updateItems();

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) {
                return;
            }
            Item item = items.get(position);
            if (item.id == ID_FOLDER_TAB_STYLE) {
                showFolderTabStyleAlert();
                return;
            }
            if (item.id == ID_PRIVACY_ARCHIVE) {
                presentFragment(new TjPrivacySettingsActivity());
                return;
            }
            if (item.id == ID_GHOST_SETTINGS) {
                presentFragment(new TjPrivacySettingsActivity(true));
                return;
            }
            if (item.id == ID_BATTERY_OPTIMIZATION) {
                TjBackgroundConnection.requestIgnoreBatteryOptimizations(getParentActivity());
                return;
            }
            if (item.viewType != VIEW_TYPE_CHECK) {
                return;
            }
            boolean value = !isChecked(item.id);
            setChecked(item.id, value);
            ((TextCheckCell) view).setChecked(value);
            if (value && (item.id == ID_GHOST_MODE || item.id == ID_GHOST_FORCE_OFFLINE)) {
                TjGhostController.sendOfflineStatusForActiveAccounts();
            }
            if (item.id == ID_BACKGROUND_CONNECTION) {
                // Restart the service so it picks up (or drops) its foreground notification.
                ApplicationLoader.startPushService();
                adapter.notifyDataSetChanged();
            }
            if (item.id == ID_GHOST_READ) {
                TjGhostController.clearReadExceptions();
            } else if (value && item.id == ID_GHOST_READ_AFTER_REPLY) {
                setChecked(ID_GHOST_SCHEDULE_MESSAGES, false);
                adapter.notifyDataSetChanged();
            } else if (value && item.id == ID_GHOST_SCHEDULE_MESSAGES) {
                setChecked(ID_GHOST_READ_AFTER_REPLY, false);
                adapter.notifyDataSetChanged();
            }
        });

        return fragmentView;
    }

    private void updateItems() {
        items.clear();
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjGeneralHeader)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_HIDE_PHONE, TjLocale.getString(R.string.TjHidePhoneNumber)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjHidePhoneNumberInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjChatIdHeader)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_BOT_API_IDS, TjLocale.getString(R.string.TjBotApiIds)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjBotApiIdsInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjChatsHeader)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_SHOW_CALL_BUTTON, TjLocale.getString(R.string.TjShowCallButton)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_ONLINE_INDICATOR, TjLocale.getString(R.string.TjOnlineIndicator)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjOnlineIndicatorInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjGhostMode)));
        items.add(new Item(VIEW_TYPE_SETTING, ID_GHOST_SETTINGS, TjLocale.getString(R.string.TjGhostSettings)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjGhostModeInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjPrivacyArchive)));
        items.add(new Item(VIEW_TYPE_SETTING, ID_PRIVACY_ARCHIVE, TjLocale.getString(R.string.TjPrivacyArchive)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjPrivacyArchiveInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, LocaleController.getString(R.string.Filters)));
        items.add(new Item(VIEW_TYPE_SETTING, ID_FOLDER_TAB_STYLE, TjLocale.getString(R.string.TjFolderTabStyle)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjFolderTabStyleInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjSubtitles)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_SUBTITLE_AUTO, TjLocale.getString(R.string.TjSubtitleAuto)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjSubtitleAutoInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjBackgroundConnection)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_BACKGROUND_CONNECTION, TjLocale.getString(R.string.TjBackgroundConnection)));
        if (TjBackgroundConnection.isBatteryOptimized()) {
            items.add(new Item(VIEW_TYPE_SETTING, ID_BATTERY_OPTIMIZATION, TjLocale.getString(R.string.TjDisableBatteryOptimization)));
        }
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjBackgroundConnectionInfo)));
        items.add(new Item(VIEW_TYPE_HEADER, 0, TjLocale.getString(R.string.TjMessageMenuHeader)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_MENU_MESSAGE_INFO, TjLocale.getString(R.string.TjMessageInfo)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_MENU_SAVE_TO_SAVED, TjLocale.getString(R.string.TjSaveToSaved)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_MENU_COPY_LINK, TjLocale.getString(R.string.TjCopyMessageLink)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_MENU_FORWARD_NO_TAG, TjLocale.getString(R.string.TjForwardWithoutTag)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_MENU_REPLY_PRIVATELY, TjLocale.getString(R.string.TjReplyPrivately)));
        items.add(new Item(VIEW_TYPE_CHECK, ID_DELETE_FOR_BOTH, TjLocale.getString(R.string.TjDeleteForBoth)));
        items.add(new Item(VIEW_TYPE_SHADOW, 0, TjLocale.getString(R.string.TjMessageMenuInfo)));
    }

    private static String folderTabStyleName() {
        switch (getFolderTabStyle()) {
            case TjFolderIcons.STYLE_ICON_ONLY: return TjLocale.getString(R.string.TjFolderTabIconOnly);
            case TjFolderIcons.STYLE_NAME_ONLY: return TjLocale.getString(R.string.TjFolderTabNameOnly);
            default: return TjLocale.getString(R.string.TjFolderTabIconAndName);
        }
    }

    private void showFolderTabStyleAlert() {
        if (getParentActivity() == null) {
            return;
        }
        CharSequence[] options = new CharSequence[]{
                TjLocale.getString(R.string.TjFolderTabIconAndName),
                TjLocale.getString(R.string.TjFolderTabIconOnly),
                TjLocale.getString(R.string.TjFolderTabNameOnly)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(TjLocale.getString(R.string.TjFolderTabStyle));
        builder.setItems(options, (dialog, which) -> {
            setFolderTabStyle(which);
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            // dialogFiltersUpdated is account-scoped - posting it on the global instance reaches
            // nobody, which is why the style only took effect after an app restart.
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.dialogFiltersUpdated);
                }
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
            } else if (viewType == VIEW_TYPE_CHECK) {
                view = new TextCheckCell(parent.getContext());
            } else if (viewType == VIEW_TYPE_SETTING) {
                view = new TextSettingsCell(parent.getContext());
            } else {
                view = new TextInfoPrivacyCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            Item item = items.get(position);
            applyCardStyle(holder.itemView, position, item.viewType);
            if (item.viewType == VIEW_TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (item.viewType == VIEW_TYPE_SHADOW) {
                ((TextInfoPrivacyCell) holder.itemView).setText(item.text);
            } else if (item.viewType == VIEW_TYPE_SETTING) {
                ((TextSettingsCell) holder.itemView).setTextAndValue(
                        item.text, item.id == ID_FOLDER_TAB_STYLE ? folderTabStyleName() : null, false);
            } else {
                boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == VIEW_TYPE_CHECK;
                ((TextCheckCell) holder.itemView).setTextAndCheck(item.text, isChecked(item.id), divider);
            }
        }

        private void applyCardStyle(View view, int position, int type) {
            ViewGroup.LayoutParams currentParams = view.getLayoutParams();
            RecyclerView.LayoutParams params;
            if (currentParams instanceof RecyclerView.LayoutParams) {
                params = (RecyclerView.LayoutParams) currentParams;
            } else {
                int width = currentParams != null ? currentParams.width : ViewGroup.LayoutParams.MATCH_PARENT;
                int height = currentParams != null ? currentParams.height : ViewGroup.LayoutParams.WRAP_CONTENT;
                params = new RecyclerView.LayoutParams(width, height);
                view.setLayoutParams(params);
            }
            if (type == VIEW_TYPE_HEADER || type == VIEW_TYPE_SHADOW) {
                params.leftMargin = params.rightMargin = 0;
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                return;
            }
            params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
            boolean top = position == 0 || items.get(position - 1).viewType == VIEW_TYPE_HEADER
                    || items.get(position - 1).viewType == VIEW_TYPE_SHADOW;
            boolean bottom = position + 1 == items.size() || items.get(position + 1).viewType == VIEW_TYPE_HEADER
                    || items.get(position + 1).viewType == VIEW_TYPE_SHADOW;
            view.setBackground(Theme.createRoundRectDrawable(
                    top ? AndroidUtilities.dp(14) : 0,
                    bottom ? AndroidUtilities.dp(14) : 0,
                    Theme.getColor(Theme.key_windowBackgroundWhite)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return VIEW_TYPE_SHADOW;
            }
            return items.get(position).viewType;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int t = holder.getItemViewType();
            return t == VIEW_TYPE_CHECK || t == VIEW_TYPE_SETTING;
        }
    }
}
