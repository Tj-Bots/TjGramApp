package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Shows how many chats of every kind the current account has. The numbers are counted off the
 * main thread, so the screen opens with a spinner and swaps to the list once the count is done.
 */
public class ChatCountersActivity extends BaseFragment {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_VALUE = 1;
    private static final int VIEW_TYPE_SHADOW = 2;

    private FrameLayout contentView;
    private RecyclerListView listView;
    private RadialProgressView progressView;

    private final ArrayList<Item> items = new ArrayList<>();
    private boolean loaded;
    private long countedAt;

    private static class Item {
        final int viewType;
        final CharSequence text;
        final CharSequence value;
        ArrayList<Long> dialogIds;

        Item(int viewType, CharSequence text, ArrayList<Long> ids) {
            this(viewType, text, (CharSequence) format(ids.size()));
            dialogIds = new ArrayList<>(ids);
        }

        Item(int viewType, CharSequence text, CharSequence value) {
            this.viewType = viewType;
            this.text = text;
            this.value = value;
        }
    }

    private static class Counters {
        final ArrayList<Long> total = new ArrayList<>();
        final ArrayList<Long> privateChats = new ArrayList<>();
        final ArrayList<Long> groups = new ArrayList<>();
        final ArrayList<Long> supergroups = new ArrayList<>();
        final ArrayList<Long> channels = new ArrayList<>();
        final ArrayList<Long> bots = new ArrayList<>();
        final ArrayList<Long> secretChats = new ArrayList<>();
        final ArrayList<Long> forums = new ArrayList<>();
        final ArrayList<Long> unread = new ArrayList<>();
        final ArrayList<Long> muted = new ArrayList<>();
        final ArrayList<Long> archived = new ArrayList<>();
        int folders;
        int contacts;
        final ArrayList<Long> creatorGroups = new ArrayList<>();
        final ArrayList<Long> creatorSupergroups = new ArrayList<>();
        final ArrayList<Long> creatorChannels = new ArrayList<>();
        final ArrayList<Long> adminGroups = new ArrayList<>();
        final ArrayList<Long> adminSupergroups = new ArrayList<>();
        final ArrayList<Long> adminChannels = new ArrayList<>();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjChatCounters));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        contentView = new FrameLayout(context);
        fragmentView = contentView;
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(new ListAdapter());
        listView.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < items.size()) showChats(items.get(position));
        });
        listView.setVisibility(View.GONE);
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        progressView = new RadialProgressView(context);
        progressView.setProgressColor(Theme.getColor(Theme.key_progressCircle));
        contentView.addView(progressView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        loadCounters();

        return fragmentView;
    }

    private void loadCounters() {
        final int account = currentAccount;
        Utilities.globalQueue.postRunnable(() -> {
            final Counters counters = count(account);
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinishing() || listView == null) {
                    return;
                }
                countedAt = System.currentTimeMillis();
                buildItems(counters);
                loaded = true;
                progressView.setVisibility(View.GONE);
                listView.setVisibility(View.VISIBLE);
                listView.getAdapter().notifyDataSetChanged();
            });
        }, 250);
    }

    private Counters count(int account) {
        final Counters counters = new Counters();
        final MessagesController controller = MessagesController.getInstance(account);
        for (int folderId = 0; folderId <= 1; folderId++) {
            ArrayList<TLRPC.Dialog> dialogs;
            try {
                dialogs = new ArrayList<>(controller.getDialogs(folderId));
            } catch (Exception e) {
                continue;
            }
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.Dialog dialog = dialogs.get(a);
                if (dialog == null || dialog instanceof TLRPC.TL_dialogFolder) {
                    continue;
                }
                final long dialogId = dialog.id;
                counters.total.add(dialogId);
                if (folderId == 1) {
                    counters.archived.add(dialogId);
                }
                if (dialog.unread_count > 0 || dialog.unread_mark) {
                    counters.unread.add(dialogId);
                }
                try {
                    if (controller.isDialogMuted(dialogId, 0)) {
                        counters.muted.add(dialogId);
                    }
                } catch (Exception ignore) {
                }
                if (DialogObject.isEncryptedDialog(dialogId)) {
                    counters.secretChats.add(dialogId);
                } else if (DialogObject.isUserDialog(dialogId)) {
                    TLRPC.User user = controller.getUser(dialogId);
                    if (user != null && user.bot) {
                        counters.bots.add(dialogId);
                    } else {
                        counters.privateChats.add(dialogId);
                    }
                } else {
                    TLRPC.Chat chat = controller.getChat(-dialogId);
                    if (chat != null && chat.forum) {
                        counters.forums.add(dialogId);
                    }
                    final int kind;
                    if (chat != null && ChatObject.isChannel(chat) && !chat.megagroup) {
                        counters.channels.add(dialogId);
                        kind = 2;
                    } else if (chat != null && ChatObject.isChannel(chat)) {
                        counters.supergroups.add(dialogId);
                        kind = 1;
                    } else {
                        counters.groups.add(dialogId);
                        kind = 0;
                    }
                    if (chat != null && chat.creator) {
                        if (kind == 2) counters.creatorChannels.add(dialogId);
                        else if (kind == 1) counters.creatorSupergroups.add(dialogId);
                        else counters.creatorGroups.add(dialogId);
                    } else if (ChatObject.hasAdminRights(chat)) {
                        if (kind == 2) counters.adminChannels.add(dialogId);
                        else if (kind == 1) counters.adminSupergroups.add(dialogId);
                        else counters.adminGroups.add(dialogId);
                    }
                }
            }
        }
        ArrayList<MessagesController.DialogFilter> filters = controller.getDialogFilters();
        if (filters != null) {
            for (int a = 0; a < filters.size(); a++) {
                MessagesController.DialogFilter filter = filters.get(a);
                if (filter != null && !filter.isDefault()) {
                    counters.folders++;
                }
            }
        }
        try {
            counters.contacts = org.telegram.messenger.ContactsController.getInstance(account).contacts.size();
        } catch (Exception ignore) {
        }
        return counters;
    }

    private void buildItems(Counters counters) {
        items.clear();
        items.add(new Item(VIEW_TYPE_HEADER, TjLocale.getString(R.string.TjChatCounters), (CharSequence) null));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjTotalChats), counters.total));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjPrivateChats), counters.privateChats));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjGroups), counters.groups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjSupergroups), counters.supergroups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjChannels), counters.channels));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjBots), counters.bots));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjSecretChats), counters.secretChats));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjForums), counters.forums));
        items.add(new Item(VIEW_TYPE_SHADOW, null, (CharSequence) null));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjUnreadChats), counters.unread));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjMutedChats), counters.muted));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjArchivedChats), counters.archived));
        items.add(new Item(VIEW_TYPE_SHADOW, null, (CharSequence) null));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjFoldersCount), format(counters.folders)));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjContactsCount), format(counters.contacts)));
        items.add(new Item(VIEW_TYPE_SHADOW, null, (CharSequence) null));
        items.add(new Item(VIEW_TYPE_HEADER, TjLocale.getString(R.string.TjCreatorHeader), (CharSequence) null));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjGroups), counters.creatorGroups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjSupergroups), counters.creatorSupergroups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjChannels), counters.creatorChannels));
        items.add(new Item(VIEW_TYPE_SHADOW, null, (CharSequence) null));
        items.add(new Item(VIEW_TYPE_HEADER, TjLocale.getString(R.string.TjAdministratorHeader), (CharSequence) null));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjGroups), counters.adminGroups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjSupergroups), counters.adminSupergroups));
        items.add(new Item(VIEW_TYPE_VALUE, TjLocale.getString(R.string.TjChannels), counters.adminChannels));
        String when = LocaleController.getInstance().getFormatterStats().format(countedAt);
        items.add(new Item(VIEW_TYPE_SHADOW,
                TjLocale.formatString(R.string.TjChatCountersUpdated, when) + "\n" + TjLocale.getString(R.string.TjChatCountersInfo), (CharSequence) null));
    }


    private void showChats(Item item) {
        if (getParentActivity() == null || item.dialogIds == null || item.dialogIds.isEmpty()) return;
        final ArrayList<Long> ids = new ArrayList<>(item.dialogIds);
        RecyclerListView chats = new RecyclerListView(getParentActivity());
        chats.setLayoutManager(new LinearLayoutManager(getParentActivity()));
        chats.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                return new RecyclerListView.Holder(new TextSettingsCell(parent.getContext()));
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setText(chatName(ids.get(position)), position + 1 < ids.size());
            }

            @Override
            public int getItemCount() { return ids.size(); }
        });
        AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(item.text)
                .setView(chats, Math.min(Math.min(400, (int) (AndroidUtilities.displaySize.y / AndroidUtilities.density * 0.55f)), ids.size() * 50))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .create();
        chats.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= ids.size()) return;
            long id = ids.get(position);
            Bundle args = new Bundle();
            if (DialogObject.isEncryptedDialog(id)) {
                args.putInt("enc_id", DialogObject.getEncryptedChatId(id));
            } else if (DialogObject.isUserDialog(id)) {
                args.putLong("user_id", id);
            } else {
                args.putLong("chat_id", -id);
            }
            if (!getMessagesController().checkCanOpenChat(args, ChatCountersActivity.this)) return;
            ChatActivity chat = new ChatActivity(args);
            chat.setCurrentAccount(currentAccount);
            dialog.dismiss();
            presentFragment(chat);
        });
        showDialog(dialog);
    }

    private String chatName(long id) {
        if (DialogObject.isEncryptedDialog(id)) {
            TLRPC.EncryptedChat chat = getMessagesController().getEncryptedChat(DialogObject.getEncryptedChatId(id));
            if (chat != null) {
                TLRPC.User user = getMessagesController().getUser(chat.user_id);
                if (user != null) return UserObject.getUserName(user);
            }
        } else if (DialogObject.isUserDialog(id)) {
            TLRPC.User user = getMessagesController().getUser(id);
            if (user != null) return UserObject.getUserName(user);
        } else {
            TLRPC.Chat chat = getMessagesController().getChat(-id);
            if (chat != null) return chat.title;
        }
        return Long.toString(id);
    }

    private static String format(int value) {
        return LocaleController.formatNumber(value, ',');
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return position >= 0 && position < items.size() && items.get(position).dialogIds != null
                    && !items.get(position).dialogIds.isEmpty();
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            if (viewType == VIEW_TYPE_HEADER) {
                view = new HeaderCell(parent.getContext());
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else if (viewType == VIEW_TYPE_VALUE) {
                view = new TextSettingsCell(parent.getContext());
                view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            } else {
                view = new TextInfoPrivacyCell(parent.getContext());
                view.setBackground(Theme.getThemedDrawableByKey(parent.getContext(), R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (position < 0 || position >= items.size()) {
                return;
            }
            Item item = items.get(position);
            if (item.viewType == VIEW_TYPE_HEADER) {
                ((HeaderCell) holder.itemView).setText(item.text);
            } else if (item.viewType == VIEW_TYPE_SHADOW) {
                ((TextInfoPrivacyCell) holder.itemView).setText(item.text);
            } else {
                boolean divider = position + 1 < items.size() && items.get(position + 1).viewType == VIEW_TYPE_VALUE;
                ((TextSettingsCell) holder.itemView).setTextAndValue(item.text, item.value, divider);
            }
        }

        @Override
        public int getItemCount() {
            return loaded ? items.size() : 0;
        }

        @Override
        public int getItemViewType(int position) {
            if (position < 0 || position >= items.size()) {
                return VIEW_TYPE_SHADOW;
            }
            return items.get(position).viewType;
        }
    }
}
