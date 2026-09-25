package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.messenger.tj.TjWatchHistory;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TjMediaPlayback;

import java.util.ArrayList;

/**
 * Everything that was watched from the catalogue, newest first, with where each one stopped.
 *
 * It is deliberately a list of titles rather than of files: a row opens the same copy that was
 * played, and a series shows the episode it was last left at. Removing a row forgets the position,
 * nothing else - the file stays where it is, in the chat it came from.
 */
public class TjWatchHistoryActivity extends BaseFragment {

    private static final int MENU_CLEAR = 1;

    private final ArrayList<TjWatchHistory.Entry> entries = new ArrayList<>();
    private RecyclerListView listView;
    private TextView empty;
    private Adapter adapter;
    private ActionBarMenuItem clearItem;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjWatchHistory));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_CLEAR) askToClear();
            }
        });
        clearItem = actionBar.createMenu().addItem(MENU_CLEAR, R.drawable.msg_delete);
        clearItem.setContentDescription(TjLocale.getString(R.string.TjWatchClearHistory));

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= entries.size()) return;
            resume(entries.get(position));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= entries.size()) return false;
            askToForget(entries.get(position));
            return true;
        });
        root.addView(listView, LayoutHelper.createFrame(-1, -1));

        empty = new TextView(context);
        empty.setTextSize(15);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(32), dp(16), dp(32), dp(16));
        empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        empty.setText(TjLocale.getString(R.string.TjWatchHistoryEmpty));
        root.addView(empty, LayoutHelper.createFrame(-1, -2, Gravity.CENTER));

        refresh();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        entries.clear();
        entries.addAll(TjWatchHistory.all());
        if (adapter != null) adapter.notifyDataSetChanged();
        if (empty != null) empty.setVisibility(entries.isEmpty() ? View.VISIBLE : View.GONE);
        if (clearItem != null) clearItem.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void resume(TjWatchHistory.Entry entry) {
        MessageObject message = entry.message();
        if (message == null) {
            // The chat it lived in is gone, or the account that saw it signed out.
            TjWatchHistory.forget(entry.key, entry.owner);
            refresh();
            return;
        }
        TjWatchPlayerActivity.resume(this, entry, message);
    }

    private void askToForget(TjWatchHistory.Entry entry) {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(entry.name)
                .setMessage(TjLocale.getString(R.string.TjWatchForgetInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget), (dialog, which) -> {
                    TjWatchHistory.forget(entry.key, entry.owner);
                    refresh();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void askToClear() {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(TjLocale.getString(R.string.TjWatchClearHistory))
                .setMessage(TjLocale.getString(R.string.TjWatchClearHistoryInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget), (dialog, which) -> {
                    TjWatchHistory.clear();
                    refresh();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return entries.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            HistoryCell cell = new HistoryCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((HistoryCell) holder.itemView).bind(entries.get(position));
        }
    }

    /** One title: its artwork, which episode, and how far in it got. */
    static class HistoryCell extends FrameLayout {
        private final BackupImageView image;
        private final TextView name, where;
        private final View track, bar;

        HistoryCell(Context context) {
            super(context);
            setBackground(Theme.getSelectorDrawable(false));
            setPadding(dp(16), dp(10), dp(16), dp(10));

            FrameLayout art = new FrameLayout(context);
            art.setClipToOutline(true);
            art.setBackground(Theme.createRoundRectDrawable(dp(6), Theme.getColor(Theme.key_windowBackgroundGray)));
            image = new BackupImageView(context);
            art.addView(image, LayoutHelper.createFrame(-1, -1));
            track = new View(context);
            track.setBackgroundColor(0x55000000);
            art.addView(track, LayoutHelper.createFrame(-1, 3, Gravity.BOTTOM));
            bar = new View(context);
            bar.setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            art.addView(bar, LayoutHelper.createFrame(0, 3, Gravity.BOTTOM | Gravity.LEFT));
            addView(art, LayoutHelper.createFrame(44, 66, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            name = new TextView(context);
            name.setTextSize(15);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(name, LayoutHelper.createLinear(-1, -2));
            where = new TextView(context);
            where.setTextSize(13);
            where.setSingleLine(true);
            where.setEllipsize(android.text.TextUtils.TruncateAt.END);
            where.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            where.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(where, LayoutHelper.createLinear(-1, -2, 0, 3, 0, 0));
            addView(texts, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 0 : 58, 0, LocaleController.isRTL ? 58 : 0, 0));
        }

        void bind(TjWatchHistory.Entry entry) {
            String poster = TjTmdb.posterUrl(entry.poster);
            image.setImage(poster.isEmpty() ? null : poster, "90_135", (android.graphics.drawable.Drawable) null);
            name.setText(entry.name);

            StringBuilder text = new StringBuilder();
            if (entry.season >= 0) text.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(entry.season);
            if (entry.episode >= 0) {
                if (text.length() > 0) text.append(" · ");
                text.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(entry.episode);
            }
            if (entry.duration > 0) {
                if (text.length() > 0) text.append(" · ");
                text.append(entry.finished() ? TjLocale.getString(R.string.TjWatchFinished)
                        : AndroidUtilities.formatShortDuration((int) (entry.position / 1000))
                                + " / " + AndroidUtilities.formatShortDuration((int) (entry.duration / 1000)));
            }
            where.setVisibility(text.length() > 0 ? VISIBLE : GONE);
            where.setText(text);

            final float progress = entry.progress();
            track.setVisibility(progress > 0 ? VISIBLE : GONE);
            bar.setVisibility(progress > 0 ? VISIBLE : GONE);
            ViewGroup.LayoutParams params = bar.getLayoutParams();
            params.width = (int) (dp(44) * progress);
            bar.setLayoutParams(params);
            setContentDescription(entry.name);
        }
    }
}
