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
public class TjChatInfoActivity extends BaseFragment {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;
    private static final int TYPE_INFO = 2;
    private static final int TYPE_PERMISSION = 3;

    private static final int ID_PERMISSIONS = 1;
    private static final int ID_ADMINS = 2;
    private static final int ID_MEMBERS = 3;
    private static final int ID_REMOVED = 4;
    private static final int ID_RECENT_ACTIONS = 5;
    private static final int ID_STATISTICS = 6;

    public static final int PAGE_INFO = 0;
    public static final int PAGE_PERMISSIONS = 1;

    private final long chatId;
    private final int page;
    private final ArrayList<Item> items = new ArrayList<>();

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull chatInfo;
    private RecyclerListView listView;

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
        return super.onFragmentCreate();
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
        return root;
    }

    private void onItemClick(int position) {
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
        items.add(new Item(TYPE_ROW, ID_ADMINS, LocaleController.getString(R.string.ChannelAdministrators),
                count(chatInfo == null ? 0 : chatInfo.admins_count), R.drawable.msg_admins, true));
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

    /** Read-only view of what an ordinary member of this group is allowed to do. */
    private void buildPermissionItems() {
        items.add(new Item(TYPE_HEADER, 0, LocaleController.getString(R.string.ChannelPermissionsHeader), null, 0, true));
        TLRPC.TL_chatBannedRights rights = currentChat.default_banned_rights;
        addPermission(R.string.UserRestrictionsSend, rights == null || !rights.send_plain);
        addPermission(R.string.UserRestrictionsSendMedia, rights == null || !rights.send_media);
        addPermission(R.string.UserRestrictionsSendStickers, rights == null || !rights.send_stickers);
        addPermission(R.string.UserRestrictionsEmbedLinks, rights == null || !rights.embed_links);
        addPermission(R.string.UserRestrictionsSendPolls, rights == null || !rights.send_polls);
        addPermission(R.string.UserRestrictionsInviteUsers, rights == null || !rights.invite_users);
        addPermission(R.string.UserRestrictionsPinMessages, rights == null || !rights.pin_messages);
        addPermission(R.string.UserRestrictionsChangeInfo, rights == null || !rights.change_info);
        if (ChatObject.isForum(currentChat)) {
            addPermission(R.string.CreateTopicsPermission, rights == null || !rights.manage_topics);
        }
        items.add(new Item(TYPE_INFO, 0, TjLocale.getString(R.string.TjChatPermissionsReadOnly), null, 0, true));
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
            return holder.getItemViewType() == TYPE_ROW;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
            } else if (viewType == TYPE_INFO) {
                view = new TextInfoPrivacyCell(parent.getContext());
            } else if (viewType == TYPE_PERMISSION) {
                view = new TextCheckCell(parent.getContext());
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
            } else if (item.type == TYPE_PERMISSION) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                boolean divider = position + 1 < items.size() && items.get(position + 1).type == TYPE_PERMISSION;
                cell.setTextAndCheck(item.text, item.allowed, divider);
                // Dimmed and inert: this is what the group allows, not something to change here.
                cell.setEnabled(false, null);
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

    private void applyCard(View view, int position) {
        ViewGroup.LayoutParams current = view.getLayoutParams();
        RecyclerView.LayoutParams params = current instanceof RecyclerView.LayoutParams
                ? (RecyclerView.LayoutParams) current
                : new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        params.leftMargin = params.rightMargin = AndroidUtilities.dp(16);
        int type = items.get(position).type;
        boolean top = position == 0 || items.get(position - 1).type != type;
        boolean bottom = position + 1 == items.size() || items.get(position + 1).type != type;
        view.setBackground(Theme.createRoundRectDrawable(
                top ? AndroidUtilities.dp(14) : 0,
                bottom ? AndroidUtilities.dp(14) : 0,
                Theme.getColor(Theme.key_windowBackgroundWhite)));
    }
}
