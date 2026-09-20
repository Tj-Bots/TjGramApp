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

    /** At most this many channels are asked about, a few at a time, per lookup. */
    private static final int CHANNEL_SCAN_LIMIT = 150;
    private static final int CHANNEL_SCAN_PARALLEL = 3;

    private final ArrayList<Probe> channelQueue = new ArrayList<>();

    /** One question to the server: is this person in this chat? */
    private static final class Probe {
        final TLRPC.Chat chat;
        /** True when the chat is already on the list and the answer may drop it. */
        final boolean listed;

        Probe(TLRPC.Chat chat, boolean listed) {
            this.chat = chat;
            this.listed = listed;
        }
    }
    private int scanGeneration;
    private int scanChecked;
    private int scanTotal;
    private int scanInFlight;
    private boolean scanning;
    private boolean scanStopped;
    private int commonGeneration;

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
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
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
        final String typed = field.getText().toString().trim();
        if (TextUtils.isEmpty(typed)) {
            return;
        }
        long id;
        try {
            id = Long.parseLong(typed);
        } catch (NumberFormatException e) {
            resolveUsername(typed);
            return;
        }
        if (id <= 0) {
            return;
        }
        lookupId(id);
    }

    /** A @name, a t.me link or a bare name - whatever shape it arrives in. */
    private void resolveUsername(String typed) {
        String username = typed;
        int slash = username.lastIndexOf('/');
        if (slash >= 0) {
            username = username.substring(slash + 1);
        }
        while (username.startsWith("@")) {
            username = username.substring(1);
        }
        if (username.isEmpty()) {
            return;
        }
        searched = true;
        user = null;
        userId = 0;
        common.clear();
        commonGeneration++;
        stopChannelScan();
        loading = true;
        buildRows();
        getMessagesController().getUserNameResolver().resolve(username, resolved -> {
            loading = false;
            if (resolved == null || resolved <= 0 || getMessagesController().getUser(resolved) == null) {
                buildRows();
                return;
            }
            if (field != null) {
                field.setText(String.valueOf(resolved));
            }
            lookupId(resolved);
        });
    }

    private void lookupId(long id) {
        userId = id;
        searched = true;
        common.clear();
        commonGeneration++;
        stopChannelScan();
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

    private static final int COMMON_PAGE = 100;

    /**
     * The server hands the common chats over a page at a time, so asking once and stopping gave a
     * hundred of them and called that the answer. It is asked again from the last chat it named,
     * until it runs out.
     */
    private void loadCommonChats() {
        if (user == null || UserObject.isUserSelf(user)) {
            return;
        }
        loading = true;
        buildRows();
        loadCommonPage(0, ++commonGeneration);
    }

    private void loadCommonPage(long maxId, int generation) {
        TLRPC.TL_messages_getCommonChats req = new TLRPC.TL_messages_getCommonChats();
        req.user_id = getMessagesController().getInputUser(user);
        if (req.user_id instanceof TLRPC.TL_inputUserEmpty) {
            loading = false;
            buildRows();
            return;
        }
        req.limit = COMMON_PAGE;
        req.max_id = maxId;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (generation != commonGeneration) {
                return;
            }
            boolean more = false;
            if (response instanceof TLRPC.messages_Chats) {
                TLRPC.messages_Chats res = (TLRPC.messages_Chats) response;
                getMessagesController().putChats(res.chats, false);
                for (int a = 0; a < res.chats.size(); a++) {
                    if (!alreadyListed(res.chats.get(a).id)) {
                        common.add(res.chats.get(a));
                    }
                }
                more = res.chats.size() == COMMON_PAGE;
                if (more) {
                    loadCommonPage(res.chats.get(res.chats.size() - 1).id, generation);
                }
            }
            if (!more) {
                loading = false;
                sortCommon();
                loadAdminChannels();
            }
            buildRows();
        }));
    }

    /**
     * Two questions the "chats in common" answer cannot settle on its own.
     *
     * One: a broadcast channel's members are its admins' business, so the server does not
     * volunteer them - but for a channel this account already runs it will answer if asked, and
     * those are the only channels where anything could be done about the answer anyway.
     *
     * Two: the common-chats answer lags. Someone removed a minute ago can still be in it, which
     * is worse than useless - it says a removal did not take. So every chat on the list that can
     * be asked about directly is asked about, and dropped when the answer says they are gone.
     *
     * The asking is paced: a handful of questions in the air at a time, and the whole thing stops
     * the moment the server says it has had enough.
     */
    private void loadAdminChannels() {
        stopChannelScan();
        if (user == null || UserObject.isUserSelf(user)) {
            return;
        }
        // Verify what is already listed, where verifying is possible at all.
        for (int a = 0; a < common.size() && channelQueue.size() < CHANNEL_SCAN_LIMIT; a++) {
            TLRPC.Chat chat = common.get(a);
            if (ChatObject.isChannel(chat) && ChatObject.canBlockUsers(chat)) {
                channelQueue.add(new Probe(chat, true));
            }
        }
        // And ask about the channels that answer could never have mentioned.
        final ArrayList<TLRPC.Dialog> dialogs = getMessagesController().getAllDialogs();
        for (int a = 0; a < dialogs.size() && channelQueue.size() < CHANNEL_SCAN_LIMIT; a++) {
            final long dialogId = dialogs.get(a).id;
            if (dialogId >= 0) {
                continue;
            }
            TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
            if (chat == null || !ChatObject.isChannelAndNotMegaGroup(chat)
                    || !ChatObject.canBlockUsers(chat) || alreadyListed(chat.id)) {
                continue;
            }
            channelQueue.add(new Probe(chat, false));
        }
        if (channelQueue.isEmpty()) {
            buildRows();
            return;
        }
        scanGeneration++;
        scanTotal = channelQueue.size();
        scanChecked = 0;
        scanInFlight = 0;
        scanning = true;
        scanStopped = false;
        buildRows();
        pumpChannelScan();
    }

    private void stopChannelScan() {
        scanGeneration++;
        channelQueue.clear();
        scanning = false;
        scanStopped = false;
        scanInFlight = 0;
        scanChecked = 0;
        scanTotal = 0;
    }

    private void pumpChannelScan() {
        final int generation = scanGeneration;
        while (scanning && scanInFlight < CHANNEL_SCAN_PARALLEL && !channelQueue.isEmpty()) {
            final Probe probe = channelQueue.remove(0);
            final long askedFor = userId;
            scanInFlight++;
            TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
            req.channel = MessagesController.getInputChannel(probe.chat);
            req.participant = getMessagesController().getInputPeer(askedFor);
            getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (generation != scanGeneration) {
                    return;
                }
                scanInFlight--;
                scanChecked++;
                if (error != null && error.text != null && error.text.startsWith("FLOOD_WAIT")) {
                    // The server has had enough questions for now. Show what was found.
                    channelQueue.clear();
                    scanStopped = true;
                } else if (isMember(response)) {
                    if (!alreadyListed(probe.chat.id)) {
                        common.add(probe.chat);
                        sortCommon();
                    }
                } else if (probe.listed && error == null) {
                    // A clear "not there" - but only a clear one. A failed question leaves the
                    // chat alone rather than quietly hiding it.
                    removeListed(probe.chat.id);
                }
                if (channelQueue.isEmpty() && scanInFlight <= 0) {
                    scanning = false;
                }
                buildRows();
                pumpChannelScan();
            }));
        }
    }

    /**
     * A removed member still has a record in the channel - it just says they left, or that they
     * were banned and left. Anything that comes back is not the same as anyone being there.
     */
    private static boolean isMember(org.telegram.tgnet.TLObject response) {
        if (!(response instanceof TLRPC.TL_channels_channelParticipant)) {
            return false;
        }
        TLRPC.ChannelParticipant participant = ((TLRPC.TL_channels_channelParticipant) response).participant;
        if (participant == null || participant instanceof TLRPC.TL_channelParticipantLeft) {
            return false;
        }
        if (participant instanceof TLRPC.TL_channelParticipantBanned) {
            // Banned without leaving is a member who cannot speak; banned and gone is gone.
            return !participant.left;
        }
        return true;
    }

    private void removeListed(long chatId) {
        for (int a = 0; a < common.size(); a++) {
            if (common.get(a).id == chatId) {
                common.remove(a);
                return;
            }
        }
    }

    /** Groups as the server ordered them, then the channels, each by name. */
    private void sortCommon() {
        java.util.Collections.sort(common, (a, b) -> {
            boolean channelA = isChannel(a), channelB = isChannel(b);
            if (channelA != channelB) {
                return channelA ? 1 : -1;
            }
            if (!channelA) {
                return 0;
            }
            return String.valueOf(a.title).compareToIgnoreCase(String.valueOf(b.title));
        });
    }

    private boolean alreadyListed(long chatId) {
        for (int a = 0; a < common.size(); a++) {
            if (common.get(a).id == chatId) {
                return true;
            }
        }
        return false;
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
            rows.add(new Row(TYPE_INFO, TjLocale.getString(R.string.TjIdLookupPrompt), null, 0, false));
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
                rows.add(new Row(TYPE_INFO, summary(), null, 0, false));
                if (common.isEmpty()) {
                    rows.add(new Row(TYPE_INFO, loading || scanning
                            ? TjLocale.getString(R.string.TjIdLookupSearching)
                            : TjLocale.getString(R.string.TjIdLookupNoCommon), null, 0, false));
                } else {
                    for (int a = 0; a < common.size(); a++) {
                        rows.add(new Row(TYPE_CHAT, null, common.get(a), 0, false));
                    }
                    if (removableCount() > 0) {
                        rows.add(new Row(TYPE_ACTION, TjLocale.getString(R.string.TjIdLookupRemoveAll)
                                + " (" + removableCount() + ")", null, ACTION_REMOVE_ALL, true));
                    }
                }
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    /** What was found, and - while channels are still being asked about - how far along it is. */
    private CharSequence summary() {
        int groups = 0, channels = 0;
        for (int a = 0; a < common.size(); a++) {
            if (isChannel(common.get(a))) {
                channels++;
            } else {
                groups++;
            }
        }
        StringBuilder text = new StringBuilder(TjLocale.formatString(
                R.string.TjIdLookupSummary, groups, channels, removableCount()));
        if (scanning) {
            text.append("\n").append(TjLocale.formatString(
                    R.string.TjIdLookupScanning, scanChecked, scanTotal));
        } else if (scanStopped) {
            text.append("\n").append(TjLocale.getString(R.string.TjIdLookupScanStopped));
        }
        return text;
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

    private static boolean isChannel(TLRPC.Chat chat) {
        return ChatObject.isChannelAndNotMegaGroup(chat);
    }

    private void showChatMenu(View anchor, TLRPC.Chat chat) {
        org.telegram.ui.Components.ItemOptions options = org.telegram.ui.Components.ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.msg_openin, LocaleController.getString(R.string.Open), () -> {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            presentFragment(new ChatActivity(args));
        });
        options.add(R.drawable.msg_openprofile, LocaleController.getString(R.string.OpenProfile), () -> {
            Bundle args = new Bundle();
            args.putLong("chat_id", chat.id);
            presentFragment(new ProfileActivity(args));
        });
        final String username = ChatObject.getPublicUsername(chat);
        if (!TextUtils.isEmpty(username)) {
            options.add(R.drawable.msg_link2, TjLocale.getString(R.string.TjIdLookupCopyChatLink), () -> {
                AndroidUtilities.addToClipboard("https://t.me/" + username);
                BulletinFactory.of(this).createCopyLinkBulletin().show();
            });
        }
        if (ChatObject.canBlockUsers(chat)) {
            options.add(R.drawable.msg_remove, TjLocale.getString(isChannel(chat)
                    ? R.string.TjIdLookupRemoveChannel : R.string.TjIdLookupRemove), true, () -> confirmRemove(chat));
        }
        options.setGravity(Gravity.LEFT).show();
    }

    /** Two taps for one chat: the first says which, the second says it cannot be taken back. */
    private void confirmRemove(TLRPC.Chat chat) {
        if (getParentActivity() == null || user == null) {
            return;
        }
        if (!ChatObject.canBlockUsers(chat)) {
            BulletinFactory.of(this).createErrorBulletin(TjLocale.getString(R.string.TjIdLookupNoRights)).show();
            return;
        }
        final String title = TjLocale.getString(isChannel(chat)
                ? R.string.TjIdLookupRemoveChannel : R.string.TjIdLookupRemove);
        AlertDialog.Builder first = new AlertDialog.Builder(getParentActivity());
        first.setTitle(title);
        first.setMessage(TjLocale.formatString(R.string.TjIdLookupRemoveConfirm, UserObject.getUserName(user), chat.title));
        first.setPositiveButton(LocaleController.getString(R.string.Remove), (d, w) -> {
            AlertDialog.Builder second = new AlertDialog.Builder(getParentActivity());
            second.setTitle(title);
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
        first.setMessage(TjLocale.formatString(R.string.TjIdLookupRemoveAllConfirm, UserObject.getUserName(user), count));
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
                            showChatMenu(c, (TLRPC.Chat) c.getCurrentObject());
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
                    String status = TjLocale.getString(isChannel(row.chat)
                            ? R.string.TjIdLookupIsChannel : R.string.TjIdLookupIsGroup);
                    if (ChatObject.canBlockUsers(row.chat)) {
                        status += " · " + TjLocale.getString(R.string.TjIdLookupYouAdmin);
                    }
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
