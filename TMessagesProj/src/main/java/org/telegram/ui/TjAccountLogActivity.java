package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjAccountLog;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Everything that happened to this account, read like a chat.
 *
 * It is not a chat: there is nobody on the other side and nothing is sent anywhere. Each entry is
 * built here, from an update the server had already delivered to this device, and lives only on it.
 */
public class TjAccountLogActivity extends BaseFragment {

    private static final int MENU_SETTINGS = 1, MENU_CLEAR = 2;

    private final ArrayList<TjAccountLog.Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private TextView emptyView;
    private final Runnable onChanged = this::load;

    @Override public boolean onFragmentCreate() {
        TjAccountLog.addListener(onChanged);
        return super.onFragmentCreate();
    }

    @Override public void onFragmentDestroy() {
        TjAccountLog.removeListener(onChanged);
        super.onFragmentDestroy();
    }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(TjLocale.getString(R.string.TjAccountLog));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_SETTINGS) presentFragment(new TjAccountLogSettingsActivity());
                else if (id == MENU_CLEAR) askToClear();
            }
        });
        ActionBarMenuItem other = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        other.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        other.addSubItem(MENU_SETTINGS, R.drawable.msg_settings, TjLocale.getString(R.string.TjAccountLogTypes));
        other.addSubItem(MENU_CLEAR, R.drawable.msg_delete, TjLocale.getString(R.string.TjAccountLogClear));

        SizeNotifierFrameLayout root = new SizeNotifierFrameLayout(context) {
            // The list starts at the top of the screen, so the picture has to as well.
            @Override protected boolean isActionBarVisible() { return false; }
        };
        root.setOccupyStatusBar(false);
        root.setBackgroundImage(Theme.getCachedWallpaperNonBlocking(), false);
        root.setBackgroundColor(Theme.getColor(Theme.key_chat_wallpaper));
        fragmentView = root;

        listView = new RecyclerListView(context);
        LinearLayoutManager manager = new LinearLayoutManager(context);
        manager.setStackFromEnd(true);
        listView.setLayoutManager(manager);
        listView.setItemAnimator(null);
        listView.setPadding(0, dp(8), 0, dp(8));
        listView.setClipToPadding(false);
        listView.setAdapter(new Adapter());
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= entries.size()) return false;
            AndroidUtilities.addToClipboard(describe(entries.get(position)));
            BulletinFactory.of(this).createCopyBulletin(
                    LocaleController.getString(R.string.TextCopied)).show();
            return true;
        });
        root.addView(listView, LayoutHelper.createFrame(-1, -1));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(15);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setText(TjLocale.getString(R.string.TjAccountLogEmpty));
        emptyView.setPadding(dp(16), dp(12), dp(16), dp(12));
        emptyView.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_chat_serviceBackground)));
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(-2, -2, Gravity.CENTER, 24, 0, 24, 0));

        load();
        return root;
    }

    private void load() {
        TjAccountLog.getInstance().load(currentAccount, loaded -> {
            if (listView == null) return;
            // Newest last, the way a chat reads.
            Collections.reverse(loaded);
            entries.clear();
            entries.addAll(loaded);
            listView.getAdapter().notifyDataSetChanged();
            emptyView.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void askToClear() {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(TjLocale.getString(R.string.TjAccountLogClear))
                .setMessage(TjLocale.getString(R.string.TjAccountLogClearInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget),
                        (dialog, which) -> TjAccountLog.getInstance().clear(currentAccount))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    /** The headline for an entry: what happened, in the words of the thing that happened. */
    private static String title(TjAccountLog.Entry entry) {
        String state = entry.text("state");
        switch (entry.type) {
            case TjAccountLog.TYPE_ADMIN_RIGHTS:
                return TjLocale.getString("granted".equals(state) ? R.string.TjAccountLogAdminGranted
                        : "removed".equals(state) ? R.string.TjAccountLogAdminRemoved
                        : R.string.TjAccountLogAdminChanged);
            case TjAccountLog.TYPE_RESTRICTED:
                return TjLocale.getString("banned".equals(state) ? R.string.TjAccountLogBanned
                        : "lifted".equals(state) ? R.string.TjAccountLogLifted
                        : R.string.TjAccountLogRestricted);
            case TjAccountLog.TYPE_MEMBERSHIP:
                return TjLocale.getString("joined".equals(state) ? R.string.TjAccountLogJoined
                        : R.string.TjAccountLogLeft);
            default:
                return TjLocale.getString(R.string.TjAccountLogNewDevice);
        }
    }

    /** The whole entry as plain text, which is what a long press puts on the clipboard. */
    private static String describe(TjAccountLog.Entry entry) {
        StringBuilder text = new StringBuilder(title(entry));
        String chat = entry.text("chatName");
        if (!chat.isEmpty()) text.append('\n').append(chat);
        String actor = entry.text("actorName");
        if (!actor.isEmpty()) text.append('\n').append(TjLocale.formatString(R.string.TjAccountLogBy, actor));
        String device = entry.text("device");
        if (!device.isEmpty()) text.append('\n').append(device);
        String location = entry.text("location");
        if (!location.isEmpty()) text.append('\n').append(location);
        ArrayList<String> changed = entry.list("changed");
        if (!changed.isEmpty()) {
            text.append("\n\n").append(TjLocale.getString(R.string.TjAccountLogChanged));
            for (String name : changed) text.append('\n').append("• ").append(name);
        }
        appendRights(text, entry);
        text.append('\n').append(LocaleController.formatDateAudio(entry.date, true));
        return text.toString();
    }

    private static void appendRights(StringBuilder text, TjAccountLog.Entry entry) {
        ArrayList<String> on = entry.list("on"), off = entry.list("off");
        if (on.isEmpty() && off.isEmpty()) return;
        text.append('\n');
        for (String name : on) text.append('\n').append("✅ ").append(name);
        for (String name : off) text.append('\n').append("❌ ").append(name);
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return entries.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            EventCell cell = new EventCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((EventCell) holder.itemView).bind(entries.get(position));
        }
    }

    /** One entry, drawn as an incoming bubble so the screen reads like the chat it stands in for. */
    private class EventCell extends LinearLayout {
        private final LinearLayout bubble;
        private final TextView title, where, who, heading, time;
        private final LinearLayout rights;

        EventCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(10), dp(3), dp(10), dp(3));

            bubble = new LinearLayout(context);
            bubble.setOrientation(VERTICAL);
            bubble.setPadding(dp(12), dp(9), dp(12), dp(7));
            bubble.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_chat_inBubble)));
            addView(bubble, LayoutHelper.createLinear(-1, -2, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            title = line(context, 15, Theme.key_chat_messageTextIn);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bubble.addView(title, LayoutHelper.createLinear(-1, -2));

            where = line(context, 14, Theme.key_chat_messageLinkIn);
            bubble.addView(where, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));

            who = line(context, 14, Theme.key_chat_messageTextIn);
            bubble.addView(who, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));

            heading = line(context, 12, Theme.key_chat_inTimeText);
            heading.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            bubble.addView(heading, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));

            rights = new LinearLayout(context);
            rights.setOrientation(VERTICAL);
            bubble.addView(rights, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));

            time = line(context, 11, Theme.key_chat_inTimeText);
            time.setGravity(LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT);
            bubble.addView(time, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 0));
        }

        private TextView line(Context context, int size, int colorKey) {
            TextView view = new TextView(context);
            view.setTextSize(size);
            view.setTextColor(Theme.getColor(colorKey));
            view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            return view;
        }

        void bind(TjAccountLog.Entry entry) {
            title.setText(title(entry));

            String chat = entry.text("chatName");
            String device = entry.text("device");
            where.setText(!chat.isEmpty() ? chat : device);
            where.setVisibility(chat.isEmpty() && device.isEmpty() ? GONE : VISIBLE);

            String actor = entry.text("actorName");
            String location = entry.text("location");
            who.setText(!actor.isEmpty() ? TjLocale.formatString(R.string.TjAccountLogBy, actor) : location);
            who.setVisibility(actor.isEmpty() && location.isEmpty() ? GONE : VISIBLE);
            final long actorId = entry.number("actor");
            who.setOnClickListener(actorId == 0 ? null : v -> {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("user_id", actorId);
                presentFragment(new ProfileActivity(args));
            });

            rights.removeAllViews();
            ArrayList<String> changed = entry.list("changed");
            ArrayList<String> on = entry.list("on"), off = entry.list("off");
            heading.setText(TjLocale.getString(R.string.TjAccountLogChanged));
            heading.setVisibility(changed.isEmpty() ? GONE : VISIBLE);
            for (String name : on) rights.addView(right(name, true, changed.contains(name)));
            for (String name : off) rights.addView(right(name, false, changed.contains(name)));
            rights.setVisibility(on.isEmpty() && off.isEmpty() ? GONE : VISIBLE);

            time.setText(LocaleController.formatDateAudio(entry.date, true));
        }

        /**
         * One right: on or off, and marked when it is one of the ones that moved - the point of
         * showing the whole list is being able to see the change against the rest of it.
         */
        private View right(String name, boolean allowed, boolean moved) {
            TextView view = line(getContext(), 13, allowed
                    ? Theme.key_chat_messageTextIn : Theme.key_chat_inTimeText);
            view.setText((allowed ? "✅  " : "❌  ") + name);
            if (moved) {
                view.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                view.setTextColor(Theme.getColor(allowed
                        ? Theme.key_windowBackgroundWhiteGreenText : Theme.key_text_RedRegular));
            }
            view.setPadding(0, dp(2), 0, dp(2));
            return view;
        }
    }
}
