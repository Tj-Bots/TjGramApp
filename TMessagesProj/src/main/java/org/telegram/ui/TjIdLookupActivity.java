package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ManageChatUserCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

/**
 * Type a user id, see who it is, and see which groups you are both in.
 *
 * Nothing here can see more than the account already can: the id is resolved out of what this
 * install has already met, the groups come from the server's own "chats in common" answer, and a
 * member can only be removed from a group where this account is already allowed to remove them.
 */
public class TjIdLookupActivity extends BaseFragment {

    private static final int TYPE_INFO = 0;
    private static final int TYPE_ACTION = 1;
    private static final int TYPE_HEADER = 2;
    private static final int TYPE_CHAT = 3;

    private EditTextBoldCursor field;
    private RecyclerListView listView;
    private ListAdapter adapter;

    private long userId;
    private TLRPC.User user;
    private boolean loading;
    private boolean searched;
    private final ArrayList<TLRPC.Chat> common = new ArrayList<>();
    private final ArrayList<Row> rows = new ArrayList<>();

    private static class Row {
        final int type;
        final CharSequence text;
        final TLRPC.Chat chat;
        final int action;
        final boolean red;

        Row(int type, CharSequence text, TLRPC.Chat chat, int action, boolean red) {
            this.type = type;
            this.text = text;
            this.chat = chat;
            this.action = action;
            this.red = red;
        }
    }

    private static final int ACTION_OPEN = 1;
    private static final int ACTION_COPY = 2;
    private static final int ACTION_REMOVE_ALL = 3;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjIdLookup));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        frame.addView(content, LayoutHelper.createFrame(-1, -1));

        FrameLayout fieldHolder = new FrameLayout(context);
        fieldHolder.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        content.addView(fieldHolder, LayoutHelper.createLinear(-1, 56));

        field = new EditTextBoldCursor(context);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setHintText(TjLocale.getString(R.string.TjIdLookupHint));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setCursorSize(AndroidUtilities.dp(20));
        field.setCursorWidth(1.5f);
        field.setBackground(null);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                lookup();
                return true;
            }
            return false;
        });
        fieldHolder.addView(field, LayoutHelper.createFrame(-1, -1f, Gravity.LEFT | Gravity.TOP, 20, 0, 20, 0));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setClipToPadding(false);
        listView.setPadding(0, 0, 0, AndroidUtilities.dp(24));
        adapter = new ListAdapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) {
                return;
            }
            Row row = rows.get(position);
            if (row.type == TYPE_CHAT) {
                Bundle args = new Bundle();
                args.putLong("chat_id", row.chat.id);
                presentFragment(new ChatActivity(args));
            } else if (row.action == ACTION_OPEN) {
                openChat();
            } else if (row.action == ACTION_COPY) {
                AndroidUtilities.addToClipboard("tg://openmessage?user_id=" + userId);
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            } else if (row.action == ACTION_REMOVE_ALL) {
                confirmRemoveFromAll();
            }
        });
        content.addView(listView, LayoutHelper.createLinear(-1, 0, 1f));

        buildRows();
        AndroidUtilities.runOnUIThread(() -> {
            if (field != null) {
                field.requestFocus();
                AndroidUtilities.showKeyboard(field);
            }
        }, 100);
        return fragmentView;
    }

    private void lookup() {
        AndroidUtilities.hideKeyboard(field);
        long id;
        try {
            id = Long.parseLong(field.getText().toString().trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (id <= 0) {
            return;
        }
        userId = id;
        searched = true;
        common.clear();
        user = getMessagesController().getUser(id);
        if (user != null) {
            buildRows();
            loadCommonChats();
            return;
        }
        // Never met locally: ask the server, which answers only for people this account has
        // already come across.
        loading = true;
        buildRows();
        TLRPC.TL_users_getUsers req = new TLRPC.TL_users_getUsers();
        TLRPC.TL_inputUser input = new TLRPC.TL_inputUser();
        input.user_id = id;
        input.access_hash = 0;
        req.id.add(input);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            if (response instanceof org.telegram.tgnet.Vector) {
                ArrayList<Object> objects = ((org.telegram.tgnet.Vector) response).objects;
                if (!objects.isEmpty() && objects.get(0) instanceof TLRPC.User) {
                    user = (TLRPC.User) objects.get(0);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user);
                    getMessagesController().putUsers(users, false);
                    buildRows();
                    loadCommonChats();
                    return;
                }
            }
            buildRows();
        }));
    }

    private void loadCommonChats() {
        if (user == null || UserObject.isUserSelf(user)) {
            return;
        }
        TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
        req.user_id = getMessagesController().getInputUser(user);
        req.limit = 100;
        req.max_id = 0;
        loading = true;
        buildRows();
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            if (response instanceof TLRPC.messages_Chats) {
                TLRPC.messages_Chats res = (TLRPC.messages_Chats) response;
                getMessagesController().putChats(res.chats, false);
                common.clear();
                common.addAll(res.chats);
            }
            buildRows();
        }));
    }

    private void openChat() {
        if (userId == 0) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", userId);
        if (getMessagesController().checkCanOpenChat(args, this)) {
            presentFragment(new ChatActivity(args));
        }
    }

    private void buildRows() {
        rows.clear();
        if (!searched) {
            rows.add(new Row(TYPE_INFO, TjLocale.getString(R.string.TjIdLookupUnknown), null, 0, false));
        } else if (user == null) {
            rows.add(new Row(TYPE_INFO, loading
                    ? TjLocale.getString(R.string.TjIdLookupSearching)
                    : TjLocale.getString(R.string.TjIdLookupUnknown), null, 0, false));
        } else {
            String username = UserObject.getPublicUsername(user);
            StringBuilder subtitle = new StringBuilder();
            if (!TextUtils.isEmpty(username)) {
                subtitle.append("@").append(username).append("  ");
            }
            subtitle.append(userId);
            rows.add(new Row(TYPE_HEADER, UserObject.getUserName(user), null, 0, false));
            rows.add(new Row(TYPE_INFO, subtitle, null, 0, false));
            rows.add(new Row(TYPE_ACTION, TjLocale.getString(R.string.TjIdLookupOpen), null, ACTION_OPEN, false));
            rows.add(new Row(TYPE_ACTION, TjLocale.getString(R.string.TjIdLookupCopyLink), null, ACTION_COPY, false));
            if (!UserObject.isUserSelf(user)) {
                rows.add(new Row(TYPE_HEADER, TjLocale.getString(R.string.TjIdLookupCommon), null, 0, false));
                if (common.isEmpty()) {
                    rows.add(new Row(TYPE_INFO, loading
                            ? TjLocale.getString(R.string.TjIdLookupSearching)
                            : TjLocale.getString(R.string.TjIdLookupNoCommon), null, 0, false));
                } else {
                    for (int a = 0; a < common.size(); a++) {
                        rows.add(new Row(TYPE_CHAT, null, common.get(a), 0, false));
                    }
                    if (removableCount() > 0) {
                        rows.add(new Row(TYPE_ACTION, TjLocale.getString(R.string.TjIdLookupRemoveAll), null, ACTION_REMOVE_ALL, true));
                    }
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private int removableCount() {
        int count = 0;
        for (int a = 0; a < common.size(); a++) {
            if (ChatObject.canBlockUsers(common.get(a))) {
                count++;
            }
        }
        return count;
    }

    /** Two taps for one group: the first says which, the second says it cannot be taken back. */
    private void confirmRemove(TLRPC.Chat chat) {
        if (getParentActivity() == null || user == null) {
            return;
        }
        if (!ChatObject.canBlockUsers(chat)) {
            BulletinFactory.of(this).createErrorBulletin(TjLocale.getString(R.string.TjIdLookupNoRights)).show();
            return;
        }
        AlertDialog.Builder first = new AlertDialog.Builder(getParentActivity());
        first.setTitle(TjLocale.getString(R.string.TjIdLookupRemove));
        first.setMessage(LocaleController.formatString(R.string.TjIdLookupRemoveConfirm, UserObject.getUserName(user), chat.title));
        first.setPositiveButton(LocaleController.getString(R.string.Remove), (d, w) -> {
            AlertDialog.Builder second = new AlertDialog.Builder(getParentActivity());
            second.setTitle(TjLocale.getString(R.string.TjIdLookupRemove));
            second.setMessage(TjLocale.getString(R.string.TjIdLookupRemoveConfirmAgain));
            second.setPositiveButton(LocaleController.getString(R.string.Remove), (d2, w2) -> remove(chat, null));
            second.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = second.create();
            showDialog(dialog);
            makeRed(dialog);
        });
        first.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = first.create();
        showDialog(dialog);
        makeRed(dialog);
    }

    /** Three taps for every group at once. */
    private void confirmRemoveFromAll() {
        if (getParentActivity() == null || user == null) {
            return;
        }
        final int count = removableCount();
        if (count == 0) {
            return;
        }
        AlertDialog.Builder first = new AlertDialog.Builder(getParentActivity());
        first.setTitle(TjLocale.getString(R.string.TjIdLookupRemoveAll));
        first.setMessage(LocaleController.formatString(R.string.TjIdLookupRemoveAllConfirm, UserObject.getUserName(user), count));
        first.setPositiveButton(LocaleController.getString(R.string.Remove), (d, w) -> {
            AlertDialog.Builder second = new AlertDialog.Builder(getParentActivity());
            second.setTitle(TjLocale.getString(R.string.TjIdLookupRemoveAll));
            second.setMessage(TjLocale.getString(R.string.TjIdLookupRemoveAllConfirmAgain));
            second.setPositiveButton(LocaleController.getString(R.string.Remove), (d2, w2) -> {
                AlertDialog.Builder third = new AlertDialog.Builder(getParentActivity());
                third.setTitle(TjLocale.getString(R.string.TjIdLookupRemoveAll));
                third.setMessage(TjLocale.getString(R.string.TjIdLookupRemoveAllConfirmFinal));
                third.setPositiveButton(LocaleController.getString(R.string.Remove), (d3, w3) -> removeFromAll());
                third.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
                AlertDialog dialog = third.create();
                showDialog(dialog);
                makeRed(dialog);
            });
            second.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            AlertDialog dialog = second.create();
            showDialog(dialog);
            makeRed(dialog);
        });
        first.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        AlertDialog dialog = first.create();
        showDialog(dialog);
        makeRed(dialog);
    }

    private void removeFromAll() {
        ArrayList<TLRPC.Chat> chats = new ArrayList<>(common);
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            if (ChatObject.canBlockUsers(chat)) {
                remove(chat, a == chats.size() - 1 ? () -> {} : null);
            }
        }
    }

    private void remove(TLRPC.Chat chat, Runnable whenDone) {
        if (user == null) {
            return;
        }
        getMessagesController().deleteParticipantFromChat(chat.id, getMessagesController().getInputPeer(user.id), false, false, () -> {
            common.remove(chat);
            buildRows();
            if (whenDone != null) {
                whenDone.run();
            }
        });
        BulletinFactory.of(this).createSimpleBulletin(R.raw.ic_delete,
                TjLocale.getString(R.string.TjIdLookupRemoved) + " · " + chat.title).show();
    }

    private void makeRed(AlertDialog dialog) {
        TextView button = (TextView) dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_ACTION || type == TYPE_CHAT;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            Context context = parent.getContext();
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_ACTION:
                    view = new TextSettingsCell(context);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHAT:
                    ManageChatUserCell cell = new ManageChatUserCell(context, 6, 2, true);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    cell.setDelegate((c, click) -> {
                        if (click && c.getCurrentObject() instanceof TLRPC.Chat) {
                            confirmRemove((TLRPC.Chat) c.getCurrentObject());
                        }
                        return true;
                    });
                    view = cell;
                    break;
                default:
                    view = new TextInfoPrivacyCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            switch (holder.getItemViewType()) {
                case TYPE_HEADER:
                    ((HeaderCell) holder.itemView).setText(row.text);
                    break;
                case TYPE_ACTION:
                    TextSettingsCell action = (TextSettingsCell) holder.itemView;
                    action.setText(row.text.toString(), position + 1 < rows.size() && rows.get(position + 1).type == TYPE_ACTION);
                    action.setTextColor(Theme.getColor(row.red ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteBlueText));
                    break;
                case TYPE_CHAT:
                    ManageChatUserCell cell = (ManageChatUserCell) holder.itemView;
                    String status = ChatObject.canBlockUsers(row.chat)
                            ? TjLocale.getString(R.string.TjIdLookupRemove)
                            : TjLocale.getString(R.string.TjIdLookupNoRights);
                    cell.setData(row.chat, null, status, position + 1 < rows.size() && rows.get(position + 1).type == TYPE_CHAT);
                    break;
                default:
                    ((TextInfoPrivacyCell) holder.itemView).setText(row.text);
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).type;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }
}
