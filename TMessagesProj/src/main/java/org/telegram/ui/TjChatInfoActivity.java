package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * One place to reach everything a group or channel exposes about itself.
 *
 * Telegram scatters these behind the edit screen, which members without rights never see, so a
 * plain member cannot find out who the admins are or what the group allows. This screen shows
 * whatever the person is actually allowed to see and nothing more: the moderation entries only
 * appear for people who can moderate, and the permissions list is read-only unless they can edit.
 */
public class TjChatInfoActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_PERMISSION = 3;
    private static final int TYPE_SUB_PERMISSION = 4;
    private static final int TYPE_VALUE = 5;
    /** "Send media": its own type, so a recycled plain row never inherits its arrow. */
    private static final int TYPE_MEDIA_PERMISSION = 6;

    private static final int ID_PERMISSIONS = 1;
    private static final int ID_ADMINS = 2;
    private static final int ID_MEMBERS = 3;
    private static final int ID_REMOVED = 4;
    private static final int ID_RECENT_ACTIONS = 5;
    private static final int ID_STATISTICS = 6;
    private static final int ID_SEND_MEDIA = 7;

    public static final int PAGE_INFO = 0;
    public static final int PAGE_PERMISSIONS = 1;

    private final long chatId;
    private final int page;
    private final ArrayList<Item> items = new ArrayList<>();

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull chatInfo;
    private RecyclerListView listView;
    private Integer fetchedAdminCount;
    private boolean requestedAdminCount;
    private boolean destroyed;
    private boolean mediaExpanded;

    private static class Item {
        final int type;
        final int id;
        final CharSequence text;
        final CharSequence value;
        final int icon;
        final boolean allowed;

        Item(int type, int id, CharSequence text, CharSequence value, int icon, boolean allowed) {
            this.type = type;
            this.id = id;
            this.text = text;
            this.value = value;
            this.icon = icon;
            this.allowed = allowed;
        }
    }

    public TjChatInfoActivity(long chatId) {
        this(chatId, PAGE_INFO);
    }

    public TjChatInfoActivity(long chatId, int page) {
        this.chatId = chatId;
        this.page = page;
    }

    public void setInfo(TLRPC.ChatFull info) {
        this.chatInfo = info;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            return false;
        }
        if (chatInfo == null) {
            chatInfo = getMessagesController().getChatFull(chatId);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getMessagesController().loadFullChat(chatId, classGuid, true);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        destroyed = true;
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad && args[0] instanceof TLRPC.ChatFull) {
            TLRPC.ChatFull info = (TLRPC.ChatFull) args[0];
            if (info.id == chatId) {
                chatInfo = info;
                refreshRows();
                loadAdminCount();
            }
        }
    }

    private void refreshRows() {
        if (destroyed || listView == null) return;
        buildItems();
        listView.getAdapter().notifyDataSetChanged();
    }

    /** A missing optional server field is unknown, not zero administrators. */
    static Integer knownAdminCount(TLRPC.Chat chat, TLRPC.ChatFull info) {
        if (info == null) return null;
        if (ChatObject.isChannel(chat)) {
            return (info.flags & 2) != 0 || info.admins_count > 0 ? info.admins_count : null;
        }
        if (!(info.participants instanceof TLRPC.TL_chatParticipants)) return null;
        int count = 0;
        for (TLRPC.ChatParticipant participant : info.participants.participants) {
            if (participant instanceof TLRPC.TL_chatParticipantCreator
                    || participant instanceof TLRPC.TL_chatParticipantAdmin) count++;
        }
        return count;
    }

    private void loadAdminCount() {
        if (page != PAGE_INFO || requestedAdminCount || destroyed
                || !ChatObject.isChannel(currentChat) || knownAdminCount(currentChat, chatInfo) != null) return;
        requestedAdminCount = true;
        long ownerId = getUserConfig().getClientUserId();
        TLRPC.TL_channels_getParticipants request = new TLRPC.TL_channels_getParticipants();
        request.channel = getMessagesController().getInputChannel(chatId);
        request.filter = new TLRPC.TL_channelParticipantsAdmins();
        request.limit = 1; // Only the total is needed, not the entire admin roster.
        int requestId = getConnectionsManager().sendRequest(request, (response, error) ->
                AndroidUtilities.runOnUIThread(() -> {
                    if (destroyed || ownerId != getUserConfig().getClientUserId()) return;
                    if (response instanceof TLRPC.TL_channels_channelParticipants) {
                        fetchedAdminCount = ((TLRPC.TL_channels_channelParticipants) response).count;
                        refreshRows();
                    }
                }));
        getConnectionsManager().bindRequestToGuid(requestId, classGuid);
    }

    private boolean isChannel() {
        return ChatObject.isChannelAndNotMegaGroup(currentChat);
    }

    private boolean canEditPermissions() {
        return !isChannel() && ChatObject.canBlockUsers(currentChat);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(
                page == PAGE_PERMISSIONS ? R.string.TjChatPermissions : R.string.TjChatInfo));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        buildItems();

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setClipToPadding(false);
        listView.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(32));
        listView.setAdapter(new Adapter());
        listView.setOnItemClickListener((view, position) -> onItemClick(position));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        loadAdminCount();
        return root;
    }

    private void onItemClick(int position) {
        if (position >= 0 && position < items.size() && items.get(position).id == ID_SEND_MEDIA) {
            mediaExpanded = !mediaExpanded;
            refreshRows();
            return;
        }
        if (position < 0 || position >= items.size() || items.get(position).type != TYPE_ROW) {
            return;
        }
        Bundle args;
        switch (items.get(position).id) {
            case ID_PERMISSIONS:
                if (canEditPermissions()) {
                    args = new Bundle();
                    args.putLong("chat_id", chatId);
                    args.putInt("type", ChatUsersActivity.TYPE_KICKED);
                    ChatUsersActivity permissions = new ChatUsersActivity(args);
                    permissions.setInfo(chatInfo);
                    presentFragment(permissions);
                } else {
                    presentFragment(new TjChatInfoActivity(chatId, PAGE_PERMISSIONS));
                }
                break;
            case ID_ADMINS:
                args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", ChatUsersActivity.TYPE_ADMIN);
                ChatUsersActivity admins = new ChatUsersActivity(args);
                admins.setInfo(chatInfo);
                presentFragment(admins);
                break;
            case ID_MEMBERS:
                args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", ChatUsersActivity.TYPE_USERS);
                ChatUsersActivity members = new ChatUsersActivity(args);
                members.setInfo(chatInfo);
                presentFragment(members);
                break;
            case ID_REMOVED:
                args = new Bundle();
                args.putLong("chat_id", chatId);
                args.putInt("type", ChatUsersActivity.TYPE_BANNED);
                ChatUsersActivity removed = new ChatUsersActivity(args);
                removed.setInfo(chatInfo);
                presentFragment(removed);
                break;
            case ID_RECENT_ACTIONS:
                presentFragment(new ChannelAdminLogActivity(currentChat));
                break;
            case ID_STATISTICS:
                args = new Bundle();
                args.putLong("chat_id", chatId);
                presentFragment(new StatisticActivity(args));
                break;
        }
    }

    private void buildItems() {
        items.clear();
        if (page == PAGE_PERMISSIONS) {
            buildPermissionItems();
            return;
        }

        boolean channel = isChannel();
        boolean canModerate = ChatObject.canBlockUsers(currentChat);
        // Same rule the profile already uses for its "Recent actions" entry.
        boolean isAdmin = currentChat.creator || ChatObject.hasAdminRights(currentChat);
        boolean canViewAdminLog = isAdmin
                && (ChatObject.isChannel(currentChat) || currentChat.gigagroup);

        items.add(new Item(TYPE_HEADER, 0, TjLocale.getString(R.string.TjChatInfo), null, 0, true));
        if (!channel) {
            items.add(new Item(TYPE_ROW, ID_PERMISSIONS, TjLocale.getString(R.string.TjChatPermissions),
                    permissionsSummary(), R.drawable.msg_permissions, true));
        }
        Integer adminCount = knownAdminCount(currentChat, chatInfo);
        if (adminCount == null) adminCount = fetchedAdminCount;
        items.add(new Item(TYPE_ROW, ID_ADMINS, LocaleController.getString(R.string.ChannelAdministrators),
                adminCount == null ? "—" : LocaleController.formatNumber(adminCount, ','), R.drawable.msg_admins, true));
        items.add(new Item(TYPE_ROW, ID_MEMBERS,
                LocaleController.getString(channel ? R.string.ChannelSubscribers : R.string.ChannelMembers),
                count(chatInfo == null ? currentChat.participants_count : chatInfo.participants_count),
                R.drawable.msg_groups, true));
        if (canModerate) {
            items.add(new Item(TYPE_ROW, ID_REMOVED, LocaleController.getString(R.string.ChannelBlacklist),
                    count(chatInfo == null ? 0 : chatInfo.kicked_count), R.drawable.msg_user_remove, true));
        }
        if (canViewAdminLog) {
            items.add(new Item(TYPE_ROW, ID_RECENT_ACTIONS, LocaleController.getString(R.string.EventLog),
                    null, R.drawable.msg_log, true));
        }
        if (chatInfo != null && chatInfo.can_view_stats) {
            items.add(new Item(TYPE_ROW, ID_STATISTICS, LocaleController.getString(R.string.Statistics),
                    null, R.drawable.msg_stats, true));
        }
        items.add(new Item(TYPE_INFO, 0, TjLocale.getString(R.string.TjChatInfoDescription), null, 0, true));
    }

    /**
     * What an ordinary member of this group is allowed to do, laid out the way the group's own
     * permissions screen lays it out for admins - media broken down by kind, slow mode included -
     * but only to read: nothing here can be switched.
     */
    private void buildPermissionItems() {
        items.add(new Item(TYPE_HEADER, 0, LocaleController.getString(R.string.ChannelPermissionsHeader), null, 0, true));
        TLRPC.TL_chatBannedRights rights = currentChat.default_banned_rights;
        if (rights == null) rights = new TLRPC.TL_chatBannedRights();
        addPermission(R.string.UserRestrictionsSendText, !rights.send_plain);
        int media = ChatUsersActivity.getSendMediaSelectedCount(rights);
        items.add(new Item(TYPE_MEDIA_PERMISSION, ID_SEND_MEDIA, LocaleController.getString(R.string.UserRestrictionsSendMedia),
                String.format(java.util.Locale.US, "%d/10", media), 0, media > 0));
        if (mediaExpanded) {
            addSub(R.string.SendMediaPermissionPhotos, !rights.send_photos);
            addSub(R.string.SendMediaPermissionVideos, !rights.send_videos);
            addSub(R.string.SendMediaPermissionStickersGifs, !rights.send_stickers);
            addSub(R.string.SendMediaPermissionMusic, !rights.send_audios);
            addSub(R.string.SendMediaPermissionFiles, !rights.send_docs);
            addSub(R.string.SendMediaPermissionVoice, !rights.send_voices);
            addSub(R.string.SendMediaPermissionRound, !rights.send_roundvideos);
            addSub(R.string.SendMediaEmbededLinks, !rights.embed_links && !rights.send_plain);
            addSub(R.string.SendMediaPolls, !rights.send_polls);
            addSub(R.string.UserRestrictionsSendReactions, !rights.send_reactions);
        }
        addPermission(R.string.UserRestrictionsInviteUsers, !rights.invite_users);
        addPermission(R.string.UserRestrictionsPinMessages, !rights.pin_messages && !ChatObject.isPublic(currentChat));
        addPermission(R.string.UserRestrictionsChangeInfo, !rights.change_info && !ChatObject.isPublic(currentChat));
        if (ChatObject.isForum(currentChat)) {
            addPermission(R.string.CreateTopicsPermission, !rights.manage_topics);
        }
        if (ChatObject.isChannel(currentChat) && !currentChat.gigagroup) {
            int slowmode = chatInfo == null ? 0 : chatInfo.slowmode_seconds;
            items.add(new Item(TYPE_HEADER, 0, LocaleController.getString(R.string.Slowmode), null, 0, true));
            items.add(new Item(TYPE_VALUE, 0, LocaleController.getString(R.string.Slowmode),
                    slowmode > 0 ? LocaleController.formatTTLString(slowmode) : LocaleController.getString(R.string.SlowmodeOff), 0, true));
        }
        items.add(new Item(TYPE_INFO, 0, TjLocale.getString(R.string.TjChatPermissionsReadOnly), null, 0, true));
    }

    private void addSub(int titleRes, boolean allowed) {
        items.add(new Item(TYPE_SUB_PERMISSION, 0, LocaleController.getString(titleRes), null, 0, allowed));
    }

    private void addPermission(int titleRes, boolean allowed) {
        items.add(new Item(TYPE_PERMISSION, 0, LocaleController.getString(titleRes), null, 0, allowed));
    }

    private CharSequence permissionsSummary() {
        TLRPC.TL_chatBannedRights rights = currentChat.default_banned_rights;
        boolean[] flags = rights == null ? null : new boolean[]{
                rights.send_plain, rights.send_media, rights.send_stickers, rights.embed_links,
                rights.send_polls, rights.invite_users, rights.pin_messages, rights.change_info
        };
        int total = 8;
        int allowed = total;
        if (flags != null) {
            allowed = 0;
            for (boolean restricted : flags) {
                if (!restricted) {
                    allowed++;
                }
            }
        }
        return allowed + "/" + total;
    }

    private static CharSequence count(int value) {
        return value > 0 ? String.valueOf(value) : null;
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == TYPE_ROW) return true;
            int position = holder.getAdapterPosition();
            return position >= 0 && position < items.size() && items.get(position).id == ID_SEND_MEDIA;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(parent.getContext());
            } else if (viewType == TYPE_PERMISSION || viewType == TYPE_MEDIA_PERMISSION) {
                view = new org.telegram.ui.Cells.TextCheckCell2(parent.getContext());
            } else if (viewType == TYPE_SUB_PERMISSION) {
                org.telegram.ui.Cells.CheckBoxCell box = new org.telegram.ui.Cells.CheckBoxCell(parent.getContext(), 4, 21, null);
                box.getCheckBoxRound().setDrawBackgroundAsArc(14);
                box.getCheckBoxRound().setColor(Theme.key_switch2TrackChecked, Theme.key_radioBackground, Theme.key_checkboxCheck);
                box.setEnabled(true);
                view = box;
            } else {
                view = new TextSettingsCell(parent.getContext());
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (item.type == TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (item.type == TYPE_INFO) {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(item.text);
                cell.setBackground(Theme.getThemedDrawable(cell.getContext(),
                        R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
            } else if (item.type == TYPE_PERMISSION || item.type == TYPE_MEDIA_PERMISSION) {
                org.telegram.ui.Cells.TextCheckCell2 cell = (org.telegram.ui.Cells.TextCheckCell2) holder.itemView;
                int next = position + 1 < items.size() ? items.get(position + 1).type : -1;
                boolean divider = next == TYPE_PERMISSION || next == TYPE_SUB_PERMISSION || next == TYPE_MEDIA_PERMISSION;
                cell.setTextAndCheck(item.text.toString(), item.allowed, divider, false);
                if (item.type == TYPE_MEDIA_PERMISSION) {
                    // The count and the arrow open the kinds of media, as on the admins' screen.
                    cell.setCollapseArrow(item.value == null ? "" : item.value.toString(), !mediaExpanded, () -> {
                        mediaExpanded = !mediaExpanded;
                        refreshRows();
                    });
                }
                cell.setIcon(0);
                applyCard(cell, position);
            } else if (item.type == TYPE_SUB_PERMISSION) {
                org.telegram.ui.Cells.CheckBoxCell cell = (org.telegram.ui.Cells.CheckBoxCell) holder.itemView;
                int next = position + 1 < items.size() ? items.get(position + 1).type : -1;
                boolean divider = next == TYPE_PERMISSION || next == TYPE_SUB_PERMISSION || next == TYPE_MEDIA_PERMISSION;
                cell.setText(item.text, "", item.allowed, divider, false);
                cell.setPad(1);
                applyCard(cell, position);
            } else if (item.type == TYPE_VALUE) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setTextAndValue(item.text, item.value, false);
                cell.setTextValueColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                applyCard(cell, position);
            } else {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                boolean divider = position + 1 < items.size() && items.get(position + 1).type == TYPE_ROW;
                cell.setTextAndValue(item.text, item.value, divider);
                cell.setTextValueColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
                cell.setIcon(item.icon);
                applyCard(cell, position);
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }
    }

    /** Rows that sit together on one card: every kind of permission row is one list. */
    private static int cardGroup(int type) {
        return type == TYPE_MEDIA_PERMISSION || type == TYPE_SUB_PERMISSION ? TYPE_PERMISSION : type;
    }

    private void applyCard(View view, int position) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        RecyclerView.LayoutParams params = current instanceof RecyclerView.LayoutParams
                ? (RecyclerView.LayoutParams) current
                : new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
        int type = cardGroup(items.get(position).type);
        boolean top = position == 0 || cardGroup(items.get(position - 1).type) != type;
        boolean bottom = position + 1 == items.size() || cardGroup(items.get(position + 1).type) != type;
        view.setBackground(Theme.createRoundRectDrawable(
                top ? AndroidUtilities.dp(14) : 0,
                bottom ? AndroidUtilities.dp(14) : 0,
                Theme.getColor(Theme.key_windowBackgroundWhite)));
    }
}
