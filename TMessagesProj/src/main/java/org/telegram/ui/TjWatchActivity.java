package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.messenger.tj.TjMediaTitle;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TjMediaCardCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * Watching, rather than file browsing.
 *
 * The library lists what is stored; this lists what there is to watch. A title is a title here -
 * one poster for a series, however many episodes of it are lying around in however many chats -
 * and the search field reads a name the way a person types it, "Dexter S05E06" or
 * "דקסטר עונה 5 פרק 6", keeping the numbers as a filter and searching on the name.
 */
public class TjWatchActivity extends BaseFragment {

    private static final int TYPE_CONTINUE = 0;
    private static final int TYPE_HEADER = 1;
    private static final int TYPE_POSTER = 2;
    private static final int TYPE_EMPTY = 3;

    /** What the chips filter by. */
    private static final int ALL = 0, SERIES = 1, MOVIES = 2;

    private final ArrayList<TjMediaStore.Record> titles = new ArrayList<>();
    private final ArrayList<TjMediaStore.Record> continueWatching = new ArrayList<>();
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ArrayList<Chip> chips = new ArrayList<>();

    private RecyclerListView listView;
    private Adapter adapter;
    private EditTextBoldCursor search;
    private TextView numberHint;
    private String query = "";
    private int filter = ALL;
    private int generation;
    private boolean loading;
    private final Runnable searchRunnable = this::reload;

    @Override
    public boolean onFragmentCreate() {
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (UserConfig.getInstance(account).isClientActivated()) accounts.add(account);
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjMediaWatchTab));
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        root.addView(createSearchField(context), LayoutHelper.createLinear(-1, 44, 12, 8, 12, 0));

        numberHint = new TextView(context);
        numberHint.setTextSize(13);
        numberHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        numberHint.setPadding(dp(18), dp(6), dp(18), 0);
        numberHint.setVisibility(View.GONE);
        root.addView(numberHint, LayoutHelper.createLinear(-1, -2));

        root.addView(createChips(context), LayoutHelper.createLinear(-1, -2, 12, 8, 12, 4));

        listView = new RecyclerListView(context);
        GridLayoutManager layout = new GridLayoutManager(context, 3);
        layout.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return adapter.typeOf(position) == TYPE_POSTER ? 1 : 3;
            }
        });
        listView.setLayoutManager(layout);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(dp(6), 0, dp(6), dp(24));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            TjMediaStore.Record record = adapter.titleAt(position);
            if (record != null) open(record);
        });
        root.addView(listView, LayoutHelper.createLinear(-1, -1));

        reload();
        return fragmentView;
    }

    private View createSearchField(Context context) {
        FrameLayout field = new FrameLayout(context);
        field.setBackground(Theme.createRoundRectDrawable(dp(22),
                Theme.getColor(Theme.key_windowBackgroundWhite)));
        search = new EditTextBoldCursor(context);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setBackground(null);
        search.setSingleLine(true);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.setHint(TjLocale.getString(R.string.TjWatchSearchHint));
        search.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        search.setCursorSize(dp(20));
        search.setCursorWidth(1.5f);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString();
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                AndroidUtilities.runOnUIThread(searchRunnable, 350);
            }
        });
        field.addView(search, LayoutHelper.createFrame(-1, -1, Gravity.CENTER, 44, 0, 16, 0));
        android.widget.ImageView icon = new android.widget.ImageView(context);
        icon.setImageResource(R.drawable.msg_search);
        icon.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteHintText), android.graphics.PorterDuff.Mode.SRC_IN));
        field.addView(icon, LayoutHelper.createFrame(44, 44,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        return field;
    }

    private View createChips(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[] labels = {R.string.TjWatchAll, R.string.TjMediaSeries, R.string.TjMediaMovies};
        for (int i = 0; i < labels.length; i++) {
            Chip chip = new Chip(context, TjLocale.getString(labels[i]));
            final int value = i;
            chip.setOnClickListener(v -> {
                if (filter == value) return;
                filter = value;
                for (Chip other : chips) other.setSelected(other == chip);
                reload();
            });
            chip.setSelected(i == ALL);
            chips.add(chip);
            row.addView(chip, LayoutHelper.createLinear(-2, 34, 0, 0, 8, 0));
        }
        return row;
    }

    /** The pill above the grid. Selected state is a fill, not a border, so it reads at a glance. */
    private static class Chip extends TextView {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Chip(Context context, String label) {
            super(context);
            setText(label);
            setTextSize(14);
            setGravity(Gravity.CENTER);
            setPadding(dp(16), 0, dp(16), 0);
            setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            ScaleStateListAnimator.apply(this, 0.04f, 1.2f);
            setWillNotDraw(false);
        }

        @Override public void setSelected(boolean selected) {
            super.setSelected(selected);
            setTextColor(Theme.getColor(selected ? Theme.key_featuredStickers_buttonText
                    : Theme.key_windowBackgroundWhiteBlackText));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setColor(isSelected() ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhite));
            canvas.drawRoundRect(0, 0, getWidth(), getHeight(), getHeight() / 2f, getHeight() / 2f, paint);
            super.onDraw(canvas);
        }
    }

    private void open(TjMediaStore.Record record) {
        int account = record.message.currentAccount;
        TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account,
                UserConfig.getInstance(account).getClientUserId(), record.message);
        presentFragment(new TjMediaDetailsActivity(entry, record, () -> { },
                accounts, new HashMap<>(), 0));
    }

    private void reload() {
        final int epoch = ++generation;
        loading = true;
        titles.clear();
        continueWatching.clear();
        adapter.notifyDataSetChanged();

        TjMediaTitle parsed = TjMediaTitle.parse("", query);
        String name = query.trim().isEmpty() ? "" : parsed.title;
        boolean numbered = !query.trim().isEmpty() && (parsed.season >= 0 || parsed.episode >= 0);
        if (numbered) {
            StringBuilder hint = new StringBuilder();
            if (parsed.season >= 0) hint.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(parsed.season);
            if (parsed.episode >= 0) {
                if (hint.length() > 0) hint.append("  ·  ");
                hint.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(parsed.episode);
            }
            numberHint.setText(hint);
            numberHint.setVisibility(View.VISIBLE);
        } else {
            numberHint.setVisibility(View.GONE);
        }

        if (query.trim().isEmpty()) loadContinue(epoch);
        loadTitles(epoch, name);
    }

    /** What is part-way watched, newest first, across every account signed in here. */
    private void loadContinue(int epoch) {
        final ArrayList<TjMediaStore.Record> collected = new ArrayList<>();
        final int[] pending = {accounts.size()};
        if (pending[0] == 0) return;
        for (int account : accounts) {
            TjMediaStore.getInstance().load(account, 1, "", "", null, new HashSet<>(), 0, 0, page -> {
                if (epoch != generation) return;
                if (page != null) collected.addAll(page);
                if (--pending[0] > 0) return;
                collected.sort((a, b) -> Long.compare(b.playedAt, a.playedAt));
                continueWatching.clear();
                for (TjMediaStore.Record record : collected) {
                    if (record.duration > 0 && record.position > 0 && !record.watched) continueWatching.add(record);
                    if (continueWatching.size() >= 20) break;
                }
                adapter.notifyDataSetChanged();
            });
        }
    }

    private void loadTitles(int epoch, String name) {
        final ArrayList<TjMediaStore.Record> collected = new ArrayList<>();
        final int[] pending = {filter == ALL ? 2 : 1};
        TjMediaStore.Callback<TjMediaStore.CatalogPage> callback = page -> {
            if (epoch != generation) return;
            if (page != null) collected.addAll(page);
            if (--pending[0] > 0) return;
            collected.sort((a, b) -> a.title().compareToIgnoreCase(b.title()));
            titles.clear();
            titles.addAll(collected);
            loading = false;
            adapter.notifyDataSetChanged();
        };
        if (filter != MOVIES) {
            TjMediaStore.getInstance().loadCatalog(accounts, true, name, null, new HashMap<>(), 0, 0, callback);
        }
        if (filter != SERIES) {
            TjMediaStore.getInstance().loadCatalog(accounts, false, name, null, new HashMap<>(), 0, 0, callback);
        }
    }

    private String subtitleOf(TjMediaStore.Record record) {
        String year = record.metadata != null && record.metadata.date.length() >= 4
                ? record.metadata.date.substring(0, 4)
                : record.localYear > 0 ? String.valueOf(record.localYear) : "";
        String kind = TjLocale.getString(record.isSeries() ? R.string.TjMediaSeries : R.string.TjMediaMovies);
        return year.isEmpty() ? kind : kind + "  ·  " + year;
    }

    private void bindPoster(TjMediaCardCell cell, TjMediaStore.Record record, boolean poster) {
        cell.setPoster(poster);
        String name = record.title();
        if (TextUtils.isEmpty(name)) name = TjLocale.getString(R.string.TjMediaCenter);
        cell.bind(record.message, name, poster ? subtitleOf(record) : record.title(),
                record.position, record.duration,
                record.metadata == null ? null
                        : poster ? record.metadata.posterUrl() : record.metadata.backdropUrl());
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {

        /** Layout: [continue row][titles header][poster…] - the row only when nothing is searched. */
        private boolean hasContinue() {
            return query.trim().isEmpty() && !continueWatching.isEmpty();
        }

        private int headerCount() {
            return hasContinue() ? 2 : titles.isEmpty() ? 0 : 1;
        }

        int typeOf(int position) {
            if (hasContinue() && position == 0) return TYPE_CONTINUE;
            if (position < headerCount()) return TYPE_HEADER;
            return titles.isEmpty() ? TYPE_EMPTY : TYPE_POSTER;
        }

        TjMediaStore.Record titleAt(int position) {
            int index = position - headerCount();
            return index >= 0 && index < titles.size() ? titles.get(index) : null;
        }

        @Override public int getItemCount() {
            return headerCount() + (titles.isEmpty() ? 1 : titles.size());
        }

        @Override public int getItemViewType(int position) { return typeOf(position); }

        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_POSTER;
        }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            Context context = parent.getContext();
            if (viewType == TYPE_CONTINUE) {
                RecyclerListView row = new RecyclerListView(context);
                row.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, LocaleController.isRTL));
                row.setClipToPadding(false);
                row.setPadding(dp(4), 0, dp(4), 0);
                row.setLayoutParams(new RecyclerView.LayoutParams(-1, dp(210)));
                row.setAdapter(new ContinueAdapter());
                row.setOnItemClickListener((view, position) -> {
                    if (position >= 0 && position < continueWatching.size()) {
                        TjMediaStore.Record record = continueWatching.get(position);
                        int account = record.message.currentAccount;
                        org.telegram.ui.Components.TjMediaPlayback.open(TjWatchActivity.this,
                                new TjMediaLibrary.Entry(account,
                                        UserConfig.getInstance(account).getClientUserId(), record.message),
                                record.position);
                    }
                });
                return new RecyclerListView.Holder(row);
            }
            if (viewType == TYPE_HEADER) {
                TextView header = new TextView(context);
                header.setTextSize(18);
                header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                header.setPadding(dp(12), dp(14), dp(12), dp(6));
                header.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
                header.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerListView.Holder(header);
            }
            if (viewType == TYPE_EMPTY) {
                TextView empty = new TextView(context);
                empty.setTextSize(15);
                empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(24), dp(48), dp(24), dp(24));
                empty.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerListView.Holder(empty);
            }
            TjMediaCardCell cell = new TjMediaCardCell(context);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            int type = typeOf(position);
            if (type == TYPE_CONTINUE) {
                RecyclerView.Adapter<?> rowAdapter = ((RecyclerListView) holder.itemView).getAdapter();
                if (rowAdapter != null) rowAdapter.notifyDataSetChanged();
                return;
            }
            if (type == TYPE_HEADER) {
                boolean continueHeader = hasContinue() && position == 1;
                ((TextView) holder.itemView).setText(TjLocale.getString(continueHeader
                        ? R.string.TjWatchTitles : R.string.TjWatchTitles));
                return;
            }
            if (type == TYPE_EMPTY) {
                ((TextView) holder.itemView).setText(TjLocale.getString(loading
                        ? R.string.TjMediaLoading : R.string.TjWatchNothing));
                return;
            }
            TjMediaStore.Record record = titleAt(position);
            if (record != null) bindPoster((TjMediaCardCell) holder.itemView, record, true);
        }
    }

    private class ContinueAdapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return continueWatching.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            TjMediaCardCell cell = new TjMediaCardCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(dp(220), -1));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            bindPoster((TjMediaCardCell) holder.itemView, continueWatching.get(position), false);
        }
    }
}
