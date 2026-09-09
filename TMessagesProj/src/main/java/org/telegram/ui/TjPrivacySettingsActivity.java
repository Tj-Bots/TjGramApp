package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjGhostController;
import org.telegram.messenger.tj.TjMessageArchive;
import org.telegram.messenger.tj.TjSyncController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class TjPrivacySettingsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    public static final int PAGE_ARCHIVE = 0;
    public static final int PAGE_GHOST = 1;
    public static final int PAGE_FILTERS = 2;
    public static final int PAGE_CUSTOMIZATION = 3;
    private static final String PREFS_NAME = "tjsettings";

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_VALUE = 3;
    private static final int TYPE_ACTION = 4;

    private static final int GHOST = 1;
    private static final int HIDE_TYPING = 2;
    private static final int HIDE_ONLINE = 3;
    private static final int HIDE_READS = 4;
    private static final int FORCE_OFFLINE = 5;
    private static final int READ_AFTER_REPLY = 6;
    private static final int SCHEDULE_MESSAGES = 7;
    private static final int HIDE_STORY_READS = 8;
    private static final int SEND_WITHOUT_SOUND = 9;
    private static final int SAVE_DELETED = 10;
    private static final int SAVE_EDITED = 11;
    private static final int SAVE_FORMATTING = 12;
    private static final int SAVE_REACTIONS = 13;
    private static final int SAVE_BOTS = 14;
    private static final int DIM_DELETED = 15;
    private static final int SAVE_MEDIA = 20;
    private static final int MEDIA_PRIVATE = 21;
    private static final int MEDIA_PUBLIC_GROUP = 22;
    private static final int MEDIA_PRIVATE_GROUP = 23;
    private static final int MEDIA_PUBLIC_CHANNEL = 24;
    private static final int MEDIA_PRIVATE_CHANNEL = 25;
    private static final int DELETED_MARKER = 30;
    private static final int EDITED_MARKER = 31;
    private static final int CLEAR_ARCHIVE = 32;
    private static final int ARCHIVE_LIMIT = 33;
    private static final int PROTECTED_FORWARDING = 40;
    private static final int FILTERS = 50;
    private static final int FILTERS_IN_CHATS = 51;
    private static final int FILTERS_CASE_INSENSITIVE = 52;
    private static final int FILTER_EXPRESSIONS = 53;
    private static final int LOCAL_PREMIUM = 60;
    private static final int HIDE_SPONSORED = 61;
    private static final int CRASH_REPORTS = 62;
    private static final int SHOW_GHOST_IN_DRAWER = 63;
    private static final int SHOW_KILL_IN_DRAWER = 64;
    private static final int KEEP_ALIVE = 65;
    private static final int SYNC_ENABLED = 70;
    private static final int SYNC_SECURE = 71;
    private static final int SYNC_SERVER = 72;
    private static final int SYNC_TOKEN = 73;
    private static final int SYNC_FORCE = 74;
    private static final int SYNC_STATUS = 75;
    private static final int SYNC_DEVICE_ID = 76;
    private static final int SYNC_LAST_SENT = 77;
    private static final int SYNC_LAST_RECEIVED = 78;
    private static final int SYNC_REGISTER_STATUS = 79;
    private static final int ADVANCED_SETTINGS = 80;

    private final ArrayList<Item> items = new ArrayList<>();
    private ListAdapter adapter;
    private final int page;

    public TjPrivacySettingsActivity() {
        this(PAGE_ARCHIVE);
    }

    public TjPrivacySettingsActivity(boolean ghostOnly) {
        this(ghostOnly ? PAGE_GHOST : PAGE_ARCHIVE);
    }

    public TjPrivacySettingsActivity(int page) {
        this.page = page;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.updateInterfaces);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.updateInterfaces);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces && adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private static class Item {
        final int type;
        final int id;
        final int text;

        Item(int type, int id, int text) {
            this.type = type;
            this.id = id;
            this.text = text;
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String text(int resource) {
        return TjLocale.getString(resource);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        int title = page == PAGE_GHOST ? R.string.TjGhostSettings
                : page == PAGE_FILTERS ? R.string.TjMessageFilters
                : page == PAGE_CUSTOMIZATION ? R.string.TjCustomization
                : R.string.TjArchiveInsights;
        actionBar.setTitle(text(title));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buildItems();
        FrameLayout frame = new FrameLayout(context);
        fragmentView = frame;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        adapter = new ListAdapter();
        list.setAdapter(adapter);
        frame.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        list.setOnItemClickListener((view, position) -> onItemClick(items.get(position), view));
        list.setOnItemLongClickListener((view, position) -> onItemLongClick(items.get(position)));
        return fragmentView;
    }

    private void buildItems() {
        items.clear();
        if (page == PAGE_GHOST) {
            items.add(new Item(TYPE_HEADER, 0, R.string.TjGhostMode));
            items.add(new Item(TYPE_CHECK, GHOST, R.string.TjGhostMode));
            if (isChecked(GHOST)) {
                items.add(new Item(TYPE_CHECK, HIDE_READS, R.string.TjGhostRead));
                items.add(new Item(TYPE_CHECK, HIDE_STORY_READS, R.string.TjGhostStoryRead));
                items.add(new Item(TYPE_CHECK, HIDE_ONLINE, R.string.TjGhostOnline));
                items.add(new Item(TYPE_CHECK, HIDE_TYPING, R.string.TjGhostTyping));
                items.add(new Item(TYPE_CHECK, FORCE_OFFLINE, R.string.TjGhostForceOffline));
            }
            items.add(new Item(TYPE_INFO, 0, R.string.TjGhostModeInfo));
            items.add(new Item(TYPE_CHECK, READ_AFTER_REPLY, R.string.TjGhostReadAfterReply));
            items.add(new Item(TYPE_INFO, 0, R.string.TjGhostReadAfterReplyInfo));
            items.add(new Item(TYPE_CHECK, SCHEDULE_MESSAGES, R.string.TjGhostScheduleMessages));
            items.add(new Item(TYPE_INFO, 0, R.string.TjGhostScheduleMessagesInfo));
            items.add(new Item(TYPE_CHECK, SEND_WITHOUT_SOUND, R.string.TjGhostSendWithoutSound));
            items.add(new Item(TYPE_INFO, 0, R.string.TjGhostSendWithoutSoundInfo));
            return;
        }

        if (page == PAGE_ARCHIVE) {
            items.add(new Item(TYPE_HEADER, 0, R.string.TjArchiveHeader));
            items.add(new Item(TYPE_CHECK, SAVE_DELETED, R.string.TjSaveDeletedMessages));
            items.add(new Item(TYPE_CHECK, SAVE_EDITED, R.string.TjSaveEditedMessages));
            items.add(new Item(TYPE_CHECK, SAVE_FORMATTING, R.string.TjSaveFormatting));
            items.add(new Item(TYPE_CHECK, SAVE_REACTIONS, R.string.TjSaveReactions));
            items.add(new Item(TYPE_CHECK, SAVE_BOTS, R.string.TjSaveBotMessages));
            items.add(new Item(TYPE_VALUE, ARCHIVE_LIMIT, R.string.TjArchiveLimit));
            items.add(new Item(TYPE_ACTION, CLEAR_ARCHIVE, R.string.TjClearArchive));
            items.add(new Item(TYPE_INFO, 0, R.string.TjArchiveInfo));

            items.add(new Item(TYPE_HEADER, 0, R.string.TjArchiveMediaHeader));
            items.add(new Item(TYPE_CHECK, SAVE_MEDIA, R.string.TjSaveMedia));
            items.add(new Item(TYPE_CHECK, MEDIA_PRIVATE, R.string.TjSavePrivateMedia));
            items.add(new Item(TYPE_CHECK, MEDIA_PUBLIC_GROUP, R.string.TjSavePublicGroupMedia));
            items.add(new Item(TYPE_CHECK, MEDIA_PRIVATE_GROUP, R.string.TjSavePrivateGroupMedia));
            items.add(new Item(TYPE_CHECK, MEDIA_PUBLIC_CHANNEL, R.string.TjSavePublicChannelMedia));
            items.add(new Item(TYPE_CHECK, MEDIA_PRIVATE_CHANNEL, R.string.TjSavePrivateChannelMedia));
            items.add(new Item(TYPE_INFO, 0, R.string.TjArchiveMediaInfo));

            items.add(new Item(TYPE_HEADER, 0, R.string.TjProtectedForwarding));
            items.add(new Item(TYPE_CHECK, PROTECTED_FORWARDING, R.string.TjProtectedForwarding));
            items.add(new Item(TYPE_INFO, 0, R.string.TjProtectedForwardingInfo));
            return;
        }

        if (page == PAGE_FILTERS) {
            items.add(new Item(TYPE_HEADER, 0, R.string.TjMessageFilters));
            items.add(new Item(TYPE_CHECK, FILTERS, R.string.TjMessageFilters));
            items.add(new Item(TYPE_CHECK, FILTERS_IN_CHATS, R.string.TjFiltersInChats));
            items.add(new Item(TYPE_CHECK, FILTERS_CASE_INSENSITIVE, R.string.TjFiltersCaseInsensitive));
            items.add(new Item(TYPE_VALUE, FILTER_EXPRESSIONS, R.string.TjFilterExpressions));
            items.add(new Item(TYPE_INFO, 0, R.string.TjFiltersInfo));
            return;
        }

        items.add(new Item(TYPE_HEADER, 0, R.string.TjDeletedAppearance));
        items.add(new Item(TYPE_CHECK, DIM_DELETED, R.string.TjDimDeletedMessages));
        items.add(new Item(TYPE_VALUE, DELETED_MARKER, R.string.TjDeletedMarker));
        items.add(new Item(TYPE_VALUE, EDITED_MARKER, R.string.TjEditedMarker));
        items.add(new Item(TYPE_INFO, 0, R.string.TjDeletedAppearanceInfo));

        items.add(new Item(TYPE_HEADER, 0, R.string.TjExtras));
        items.add(new Item(TYPE_CHECK, LOCAL_PREMIUM, R.string.TjLocalPremium));
        items.add(new Item(TYPE_CHECK, HIDE_SPONSORED, R.string.TjHideSponsored));
        items.add(new Item(TYPE_INFO, 0, R.string.TjLocalPremiumInfo));
        items.add(new Item(TYPE_CHECK, SHOW_GHOST_IN_DRAWER, R.string.TjShowGhostInDrawer));
        items.add(new Item(TYPE_CHECK, SHOW_KILL_IN_DRAWER, R.string.TjShowKillInDrawer));
        items.add(new Item(TYPE_CHECK, KEEP_ALIVE, R.string.TjKeepAlive));
        items.add(new Item(TYPE_INFO, 0, R.string.TjKeepAliveInfo));
        items.add(new Item(TYPE_CHECK, CRASH_REPORTS, R.string.TjCrashReports));
        items.add(new Item(TYPE_INFO, 0, R.string.TjCrashReportsInfo));
        items.add(new Item(TYPE_HEADER, 0, R.string.TjSync));
        items.add(new Item(TYPE_CHECK, SYNC_ENABLED, R.string.TjSyncEnabled));
        items.add(new Item(TYPE_VALUE, SYNC_SERVER, R.string.TjSyncServer));
        items.add(new Item(TYPE_VALUE, SYNC_TOKEN, R.string.TjSyncToken));
        items.add(new Item(TYPE_VALUE, SYNC_STATUS, R.string.TjSyncStatus));
        items.add(new Item(TYPE_VALUE, SYNC_DEVICE_ID, R.string.TjSyncDeviceId));
        items.add(new Item(TYPE_VALUE, SYNC_LAST_SENT, R.string.TjSyncLastSent));
        items.add(new Item(TYPE_VALUE, SYNC_LAST_RECEIVED, R.string.TjSyncLastReceived));
        items.add(new Item(TYPE_VALUE, SYNC_REGISTER_STATUS, R.string.TjSyncRegisterStatus));
        items.add(new Item(TYPE_ACTION, SYNC_FORCE, R.string.TjSyncForce));
        items.add(new Item(TYPE_INFO, 0, R.string.TjSyncInfo));
    }

    private void onItemClick(Item item, View view) {
        if (item.type == TYPE_CHECK) {
            boolean value = !isChecked(item.id);
            // The master switch only enables or disables Ghost Mode. Keep the five essential
            // choices untouched so enabling it again restores the user's exact previous preset.
            setChecked(item.id, value);
            ((TextCheckCell) view).setChecked(value);
            if (value && (item.id == GHOST || item.id == FORCE_OFFLINE)) {
                TjGhostController.sendOfflineStatusForActiveAccounts();
            }
            if (item.id == GHOST) {
                buildItems();
                adapter.notifyDataSetChanged();
            } else if (isGhostEssential(item.id)) {
                // The master row at position 1 shows the live enabled/total value.
                adapter.notifyItemChanged(1);
                if (item.id == HIDE_READS) {
                    TjGhostController.clearReadExceptions();
                }
            } else if (value && item.id == READ_AFTER_REPLY) {
                setChecked(SCHEDULE_MESSAGES, false);
                adapter.notifyDataSetChanged();
            } else if (value && item.id == SCHEDULE_MESSAGES) {
                setChecked(READ_AFTER_REPLY, false);
                adapter.notifyDataSetChanged();
            }
            if (item.id == LOCAL_PREMIUM) {
                getMessagesController().updatePremium(value);
                NotificationCenter.getInstance(currentAccount).postNotificationName(
                        NotificationCenter.currentUserPremiumStatusChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(
                        NotificationCenter.premiumStatusChangedGlobal);
                getMediaDataController().loadPremiumPromo(false);
                getMediaDataController().loadReactions(false, null);
            }
            if (item.id == HIDE_SPONSORED) {
                org.telegram.messenger.video.VideoAds.dropCache();
                for (int account = 0; account < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    if (org.telegram.messenger.UserConfig.getInstance(account).isClientActivated()) {
                        org.telegram.messenger.MessagesController.getInstance(account).clearTjSponsoredMessages();
                    }
                }
            }
            if (item.id == CRASH_REPORTS) {
                ApplicationLoader.updateTjCrashReports(value);
            }
            if (item.id == KEEP_ALIVE) {
                ApplicationLoader.startPushService();
            }
            if (item.id == SYNC_ENABLED) {
                TjSyncController.restart();
                adapter.notifyDataSetChanged();
            }
        } else if (item.id == DELETED_MARKER) {
            showDeletedMarkerDialog();
        } else if (item.id == EDITED_MARKER) {
            String marker = TjConfig.editedMark();
            showTextEditor(item, marker.isEmpty() ? LocaleController.getString(R.string.EditedMessage) : marker, true);
        } else if (item.id == FILTER_EXPRESSIONS) {
            showTextEditor(item, TjConfig.filterExpressions(), false);
        } else if (item.id == SYNC_SERVER) {
            showTextEditor(item, TjConfig.syncServer(), true);
        } else if (item.id == SYNC_TOKEN) {
            showTextEditor(item, TjConfig.syncToken(), true);
        } else if (item.id == SYNC_FORCE) {
            TjSyncController.forceSync();
        } else if (item.id == CLEAR_ARCHIVE) {
            showClearArchiveDialog();
        } else if (item.id == ARCHIVE_LIMIT) {
            showArchiveLimitDialog();
        } else if (item.id == ADVANCED_SETTINGS) {
            presentFragment(new TjSettingsActivity());
        }
    }

    private boolean onItemLongClick(Item item) {
        if (page != PAGE_GHOST || !isGhostEssential(item.id)) return false;
        boolean locked = !isGhostEssentialLocked(item.id);
        prefs().edit().putBoolean("ghost_lock_" + item.id, locked).apply();
        if (adapter != null) adapter.notifyDataSetChanged();
        if (getParentActivity() != null) {
            Toast.makeText(getParentActivity(), text(locked
                    ? R.string.TjGhostOptionLocked : R.string.TjGhostOptionUnlocked), Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private static boolean isGhostEssential(int id) {
        return id == HIDE_READS || id == HIDE_STORY_READS || id == HIDE_ONLINE
                || id == HIDE_TYPING || id == FORCE_OFFLINE;
    }

    private static boolean isGhostEssentialLocked(int id) {
        return isGhostEssential(id) && prefs().getBoolean("ghost_lock_" + id, false);
    }

    private boolean isChecked(int id) {
        switch (id) {
            case GHOST: return prefs().getBoolean("ghost_mode", false);
            case HIDE_TYPING: return prefs().getBoolean("ghost_hide_typing", true);
            case HIDE_ONLINE: return prefs().getBoolean("ghost_hide_online", true);
            case HIDE_READS: return prefs().getBoolean("ghost_hide_read", true);
            case HIDE_STORY_READS: return prefs().getBoolean("ghost_hide_story_reads", true);
            case FORCE_OFFLINE: return prefs().getBoolean("ghost_force_offline", true);
            case READ_AFTER_REPLY: return prefs().getBoolean("ghost_read_after_reply", false);
            case SCHEDULE_MESSAGES: return prefs().getBoolean("ghost_schedule_messages", false);
            case SEND_WITHOUT_SOUND: return prefs().getBoolean("ghost_send_without_sound", false);
            case SAVE_DELETED: return TjConfig.saveDeletedMessages();
            case SAVE_EDITED: return TjConfig.saveEditedMessages();
            case SAVE_FORMATTING: return TjConfig.saveFormatting();
            case SAVE_REACTIONS: return TjConfig.saveReactions();
            case SAVE_BOTS: return TjConfig.saveBotMessages();
            case DIM_DELETED: return TjConfig.dimDeletedMessages();
            case SAVE_MEDIA: return TjConfig.saveMedia();
            case MEDIA_PRIVATE: return TjConfig.savePrivateMedia();
            case MEDIA_PUBLIC_GROUP: return TjConfig.savePublicGroupMedia();
            case MEDIA_PRIVATE_GROUP: return TjConfig.savePrivateGroupMedia();
            case MEDIA_PUBLIC_CHANNEL: return TjConfig.savePublicChannelMedia();
            case MEDIA_PRIVATE_CHANNEL: return TjConfig.savePrivateChannelMedia();
            case PROTECTED_FORWARDING: return TjConfig.protectedForwarding();
            case FILTERS: return TjConfig.messageFilters();
            case FILTERS_IN_CHATS: return TjConfig.filtersInChats();
            case FILTERS_CASE_INSENSITIVE: return TjConfig.filtersCaseInsensitive();
            case LOCAL_PREMIUM: return TjConfig.localPremium();
            case HIDE_SPONSORED: return TjConfig.hideSponsoredMessages();
            case CRASH_REPORTS: return TjConfig.crashReportsEnabled();
            case SHOW_GHOST_IN_DRAWER: return TjConfig.showGhostInDrawer();
            case SHOW_KILL_IN_DRAWER: return TjConfig.showKillInDrawer();
            case KEEP_ALIVE: return MessagesController.getGlobalNotificationsSettings()
                    .getBoolean("pushService", true);
            case SYNC_ENABLED: return TjConfig.syncEnabled();
            case SYNC_SECURE: return TjConfig.syncSecure();
        }
        return false;
    }

    private void setChecked(int id, boolean value) {
        String key = null;
        switch (id) {
            case GHOST: key = "ghost_mode"; break;
            case HIDE_TYPING: key = "ghost_hide_typing"; break;
            case HIDE_ONLINE: key = "ghost_hide_online"; break;
            case HIDE_READS: key = "ghost_hide_read"; break;
            case HIDE_STORY_READS: key = "ghost_hide_story_reads"; break;
            case FORCE_OFFLINE: key = "ghost_force_offline"; break;
            case READ_AFTER_REPLY: key = "ghost_read_after_reply"; break;
            case SCHEDULE_MESSAGES: key = "ghost_schedule_messages"; break;
            case SEND_WITHOUT_SOUND: key = "ghost_send_without_sound"; break;
            case SAVE_DELETED: key = "archive_deleted_messages"; break;
            case SAVE_EDITED: key = "archive_edited_messages"; break;
            case SAVE_FORMATTING: key = "archive_formatting"; break;
            case SAVE_REACTIONS: key = "archive_reactions"; break;
            case SAVE_BOTS: key = "archive_bots"; break;
            case DIM_DELETED: key = "dim_deleted_messages"; break;
            case SAVE_MEDIA: key = "archive_media"; break;
            case MEDIA_PRIVATE: key = "archive_media_private"; break;
            case MEDIA_PUBLIC_GROUP: key = "archive_media_public_groups"; break;
            case MEDIA_PRIVATE_GROUP: key = "archive_media_private_groups"; break;
            case MEDIA_PUBLIC_CHANNEL: key = "archive_media_public_channels"; break;
            case MEDIA_PRIVATE_CHANNEL: key = "archive_media_private_channels"; break;
            case PROTECTED_FORWARDING: key = "protected_forwarding"; break;
            case FILTERS: key = "message_filters"; break;
            case FILTERS_IN_CHATS: key = "message_filters_in_chats"; break;
            case FILTERS_CASE_INSENSITIVE: key = "message_filters_case_insensitive"; break;
            case LOCAL_PREMIUM: key = "local_premium"; break;
            case HIDE_SPONSORED: key = "hide_sponsored_messages"; break;
            case CRASH_REPORTS: key = "crash_reports_enabled"; break;
            case SHOW_GHOST_IN_DRAWER: key = "show_ghost_in_drawer"; break;
            case SHOW_KILL_IN_DRAWER: key = "show_kill_in_drawer"; break;
            case KEEP_ALIVE:
                MessagesController.getGlobalNotificationsSettings().edit()
                        .putBoolean("pushService", value).apply();
                return;
            case SYNC_ENABLED: key = "tj_sync_enabled"; break;
            case SYNC_SECURE: key = "tj_sync_secure"; break;
        }
        if (key != null) {
            TjConfig.put(key, value);
        }
    }

    private void showTextEditor(Item item, String current, boolean singleLine) {
        if (getParentActivity() == null) {
            return;
        }
        EditText input = new EditText(getParentActivity());
        input.setText(current);
        input.setSelection(input.length());
        input.setSingleLine(singleLine);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (singleLine
                ? 0 : InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(text(item.text));
        builder.setView(input);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
            String value = input.getText().toString().trim();
            if (item.id == DELETED_MARKER) {
                TjConfig.put("deleted_mark", value.isEmpty() ? "🗑️" : value);
            } else if (item.id == EDITED_MARKER) {
                TjConfig.put("edited_mark", value);
            } else if (item.id == SYNC_SERVER) {
                TjConfig.put("tj_sync_server", value);
                TjSyncController.restart();
            } else if (item.id == SYNC_TOKEN) {
                TjConfig.setSyncToken(value);
                TjSyncController.restart();
            } else {
                TjConfig.put("message_filter_expressions", value);
            }
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showDeletedMarkerDialog() {
        if (getParentActivity() == null) {
            return;
        }
        final String[] markers = {"🗑️", "❌", "✖", "🧹"};
        final int[] labels = {
                R.string.TjDeletedMarkerTrash,
                R.string.TjDeletedMarkerRedX,
                R.string.TjDeletedMarkerDarkX,
                R.string.TjDeletedMarkerBroom
        };
        CharSequence[] choices = new CharSequence[markers.length];
        String current = TjConfig.deletedMark();
        for (int i = 0; i < markers.length; i++) {
            choices[i] = (markers[i].equals(current) ? "✓  " : "") + markers[i] + "  " + text(labels[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(text(R.string.TjDeletedMarker));
        builder.setItems(choices, (dialog, which) -> {
            TjConfig.put("deleted_mark", markers[which]);
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showClearArchiveDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(text(R.string.TjClearArchiveTitle));
        builder.setMessage(text(R.string.TjClearArchiveText));
        builder.setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, which) ->
                TjMessageArchive.getInstance().clear(currentAccount, success -> {
                    // Snapshots alone are not the whole archive: the retained copies also live in
                    // Telegram's message table, and they are what keeps showing up in chats.
                    getMessagesStorage().clearTjRetainedMessages(() -> {
                        if (success && getParentActivity() != null) {
                            Toast.makeText(getParentActivity(), text(R.string.TjArchiveCleared), Toast.LENGTH_SHORT).show();
                        }
                    });
                }));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showArchiveLimitDialog() {
        CharSequence[] values = new CharSequence[10];
        int current = TjConfig.archiveLimitGb();
        for (int i = 0; i < values.length; i++) {
            int size = i + 1;
            values[i] = (size == current ? "✓  " : "") + size + " GB";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(text(R.string.TjArchiveLimit));
        builder.setItems(values, (dialog, which) -> {
            TjConfig.put("archive_limit_gb", which + 1);
            TjMessageArchive.getInstance().enforceQuota(currentAccount);
            adapter.notifyDataSetChanged();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private String valueFor(int id) {
        if (id == DELETED_MARKER) return TjConfig.deletedMark();
        if (id == EDITED_MARKER) {
            String marker = TjConfig.editedMark();
            return marker.isEmpty() ? LocaleController.getString(R.string.EditedMessage) : marker;
        }
        if (id == ARCHIVE_LIMIT) return TjConfig.archiveLimitGb() + " GB";
        if (id == FILTER_EXPRESSIONS) {
            String expressions = TjConfig.filterExpressions();
            return expressions.isEmpty() ? LocaleController.getString(R.string.NotificationsOff) :
                    String.valueOf(expressions.split("\\r?\\n").length);
        }
        if (id == SYNC_SERVER) {
            return TjConfig.syncServer().isEmpty() ? LocaleController.getString(R.string.NotificationsOff) : TjConfig.syncServer();
        }
        if (id == SYNC_TOKEN) {
            return TjConfig.syncToken().isEmpty() ? LocaleController.getString(R.string.NotificationsOff) : "••••••••";
        }
        if (id == SYNC_STATUS) {
            switch (TjSyncController.getState()) {
                case CONNECTED: return text(R.string.TjSyncConnected);
                case CONNECTING: return text(R.string.TjSyncConnecting);
                case ERROR: return text(R.string.TjSyncError);
                case MISSING_CONFIGURATION: return text(R.string.TjSyncMissingConfig);
                default: return text(R.string.TjSyncDisabled);
            }
        }
        if (id == SYNC_DEVICE_ID) return TjSyncController.getDeviceId();
        if (id == SYNC_LAST_SENT) return formatSyncTime(TjSyncController.getLastSent());
        if (id == SYNC_LAST_RECEIVED) return formatSyncTime(TjSyncController.getLastReceived());
        if (id == SYNC_REGISTER_STATUS) {
            int code = TjSyncController.getRegistrationStatusCode();
            return code == 0 ? text(R.string.TjSyncNever) : String.valueOf(code);
        }
        return "";
    }

    private String formatSyncTime(long timestamp) {
        return timestamp == 0 ? text(R.string.TjSyncNever) :
                LocaleController.getInstance().getFormatterStats().format(timestamp);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(parent.getContext());
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(parent.getContext());
                return new RecyclerListView.Holder(view);
            } else {
                view = new TextSettingsCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            applyCardStyle(holder.itemView, position, item.type);
            if (item.type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(text(item.text));
            } else if (item.type == TYPE_INFO) {
                ((TextInfoPrivacyCell) holder.itemView).setText(text(item.text));
            } else if (item.type == TYPE_CHECK) {
                boolean divider = position + 1 < items.size() && items.get(position + 1).type == TYPE_CHECK;
                String label = text(item.text);
                if (item.id == GHOST) {
                    int enabled = 0;
                    int[] essentials = {HIDE_READS, HIDE_STORY_READS, HIDE_ONLINE, HIDE_TYPING, FORCE_OFFLINE};
                    for (int id : essentials) {
                        if (isChecked(id)) enabled++;
                    }
                    label += "  " + enabled + "/" + essentials.length;
                } else if (isGhostEssentialLocked(item.id)) {
                    label += "  🔒";
                }
                ((TextCheckCell) holder.itemView).setTextAndCheck(label, isChecked(item.id), divider);
            } else {
                ((TextSettingsCell) holder.itemView).setTextAndValue(
                        text(item.text), item.type == TYPE_VALUE ? valueFor(item.id) : null, false);
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
            if (type == TYPE_HEADER || type == TYPE_INFO) {
                params.leftMargin = params.rightMargin = 0;
                view.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                return;
            }
            params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
            boolean top = position == 0 || items.get(position - 1).type == TYPE_HEADER
                    || items.get(position - 1).type == TYPE_INFO;
            boolean bottom = position + 1 == items.size() || items.get(position + 1).type == TYPE_HEADER
                    || items.get(position + 1).type == TYPE_INFO;
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
            return items.get(position).type;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) {
                return false;
            }
            Item item = items.get(position);
            return holder.getItemViewType() != TYPE_HEADER && holder.getItemViewType() != TYPE_INFO
                    && item.id != SYNC_STATUS && item.id != SYNC_DEVICE_ID
                    && item.id != SYNC_LAST_SENT && item.id != SYNC_LAST_RECEIVED
                    && item.id != SYNC_REGISTER_STATUS;
        }
    }
}
