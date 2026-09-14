package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjMediaTitle;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.messenger.tj.TjWatchHistory;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.TjTmdbKeyDialog;

import java.util.ArrayList;

/**
 * Browsing what there is to watch, which starts with the catalogue of everything rather than with
 * the files in a chat. A name is searched against TMDB, the answers come back as the titles a
 * person would recognise, and only after one is chosen does anything go looking for a file.
 */
public class TjWatchActivity extends BaseFragment {

    /** One row of results. */
    public static final class Item {
        public final long id;
        public final boolean series;
        public final String name, poster, date;
        public final double rating, popularity;

        Item(JSONObject object, boolean series) {
            this.series = series;
            id = object.optLong("id");
            name = object.optString(series ? "name" : "title", "");
            poster = object.optString("poster_path", "");
            date = object.optString(series ? "first_air_date" : "release_date", "");
            rating = object.optDouble("vote_average", 0);
            popularity = object.optDouble("popularity", 0);
        }

        String year() { return date != null && date.length() >= 4 ? date.substring(0, 4) : ""; }
    }

    /** A name the catalogue files things under. Films and series number them differently. */
    private static final class Genre {
        final String name;
        int movieId, seriesId;
        Genre(String name) { this.name = name; }
    }

    private static final int MENU_HISTORY = 1, MENU_SETTINGS = 2;

    /** Everything, or only one half of it. Netflix's first question, and a reasonable one. */
    private static final int KIND_ALL = 0, KIND_MOVIES = 1, KIND_SERIES = 2;

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Genre> genres = new ArrayList<>();
    private final TjTmdb series = new TjTmdb();
    private final TjTmdb movies = new TjTmdb();
    private final TjTmdb seriesGenres = new TjTmdb();
    private final TjTmdb movieGenres = new TjTmdb();

    private FrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private ActionBarMenuItem searchItem;
    private TextView status;
    private View gate;
    private LinearLayout continueSection;
    private LinearLayout continueRow;
    private LinearLayout genreRow;
    private LinearLayout kindRow;
    private Genre selectedGenre;
    private int kind = KIND_ALL;
    private String query = "";
    private int pending;
    private int genresPending;
    private final Runnable searchRunnable = this::reload;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(TjLocale.getString(R.string.TjWatchTitle));
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_HISTORY) presentFragment(new TjWatchHistoryActivity());
                else if (id == MENU_SETTINGS) presentFragment(new TjWatchSettingsActivity());
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        // The search belongs where every other search in the app is. Below the bar it sat on top
        // of the genre row, and the two read as one crowded block.
        searchItem = menu.addItem(0, R.drawable.outline_header_search).setIsSearchField(true)
                .setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
                    @Override public void onTextChanged(android.widget.EditText field) {
                        typed(field.getText().toString());
                    }
                    @Override public void onSearchCollapse() {
                        typed("");
                    }
                });
        searchItem.setSearchFieldHint(TjLocale.getString(R.string.TjWatchSearchHint));
        searchItem.setContentDescription(LocaleController.getString(R.string.Search));

        ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        other.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        other.addSubItem(MENU_HISTORY, R.drawable.msg_recent, TjLocale.getString(R.string.TjWatchHistory));
        other.addSubItem(MENU_SETTINGS, R.drawable.msg_settings, TjLocale.getString(R.string.TjWatchSettings));

        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(-1, -1));

        content.addView(kindSection(context), LayoutHelper.createLinear(-1, 34, 12, 10, 12, 0));

        content.addView(genreSection(context), LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));

        content.addView(continueSection(context), LayoutHelper.createLinear(-1, -2));

        status = new TextView(context);
        status.setTextSize(14);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(28), dp(28), dp(28), dp(12));
        content.addView(status, LayoutHelper.createLinear(-1, -2));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new GridLayoutManager(context, 3));
        listView.setClipToPadding(false);
        listView.setPadding(dp(6), dp(6), dp(6), dp(24));
        listView.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= items.size()) return;
            Item item = items.get(position);
            presentFragment(new TjTitleActivity(item.id, item.series, item.name));
        });
        content.addView(listView, LayoutHelper.createLinear(-1, -1));

        if (!TjTmdb.available(currentAccount)) {
            showGate(context);
        } else {
            trending();
            loadGenres();
        }
        refreshContinue();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Coming back from a film is exactly when the shelf is wrong: it now has a new position,
        // or the film finished and should drop off it.
        refreshContinue();
    }

    /**
     * Films, series, or both. The first thing a person narrows down, and the thing that decides
     * which half of the catalogue every other question on this screen is put to.
     */
    private View kindSection(Context context) {
        kindRow = new LinearLayout(context);
        kindRow.setOrientation(LinearLayout.HORIZONTAL);
        kindRow.setBackground(Theme.createRoundRectDrawable(dp(17),
                Theme.getColor(Theme.key_windowBackgroundWhite)));
        kindRow.setPadding(dp(3), dp(3), dp(3), dp(3));
        kindRow.addView(segment(context, TjLocale.getString(R.string.TjWatchAll), KIND_ALL),
                LayoutHelper.createLinear(0, -1, 1f));
        kindRow.addView(segment(context, TjLocale.getString(R.string.TjMediaMovies), KIND_MOVIES),
                LayoutHelper.createLinear(0, -1, 1f));
        kindRow.addView(segment(context, TjLocale.getString(R.string.TjMediaSeries), KIND_SERIES),
                LayoutHelper.createLinear(0, -1, 1f));
        styleKinds();
        return kindRow;
    }

    private TextView segment(Context context, String label, int value) {
        TextView view = new TextView(context);
        view.setTextSize(13);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        view.setTag(value);
        view.setOnClickListener(v -> {
            if (kind == value) return;
            kind = value;
            styleKinds();
            // A genre only one half of the catalogue knows about goes with that half.
            if (selectedGenre != null && !fits(selectedGenre)) selectedGenre = null;
            buildGenreChips();
            reload();
        });
        return view;
    }

    private void styleKinds() {
        if (kindRow == null) return;
        for (int a = 0; a < kindRow.getChildCount(); a++) {
            TextView view = (TextView) kindRow.getChildAt(a);
            boolean chosen = (Integer) view.getTag() == kind;
            view.setBackground(chosen ? Theme.createRoundRectDrawable(dp(14),
                    Theme.getColor(Theme.key_featuredStickers_addButton)) : null);
            view.setTextColor(chosen ? Theme.getColor(Theme.key_featuredStickers_buttonText)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        }
    }

    /** True when this genre exists on the half of the catalogue currently being shown. */
    private boolean fits(Genre genre) {
        return (wantsSeries() && genre.seriesId > 0) || (wantsMovies() && genre.movieId > 0);
    }

    /**
     * The row of names the catalogue files things under, which is the other way people look for
     * something to watch: not a title they already have in mind, but a kind of evening.
     */
    private View genreSection(Context context) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        scroll.setVisibility(View.GONE);
        genreRow = new LinearLayout(context);
        genreRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(genreRow, new FrameLayout.LayoutParams(-2, -2));
        return scroll;
    }

    /**
     * Both lists at once, merged by the name rather than the number: a film's "Action" and a
     * series' "Action & Adventure" are one chip to a person, and two different numbers to TMDB.
     */
    private void loadGenres() {
        genresPending = 2;
        seriesGenres.genres(currentAccount, true, (body, error) -> collectGenres(body, true));
        movieGenres.genres(currentAccount, false, (body, error) -> collectGenres(body, false));
    }

    private void collectGenres(JSONObject body, boolean isSeries) {
        JSONArray list = body == null ? null : body.optJSONArray("genres");
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject object = list.optJSONObject(i);
            if (object == null) continue;
            int id = object.optInt("id");
            String name = object.optString("name", "").trim();
            if (id <= 0 || name.isEmpty()) continue;
            Genre genre = null;
            for (Genre known : genres) if (known.name.equalsIgnoreCase(name)) { genre = known; break; }
            if (genre == null) { genre = new Genre(name); genres.add(genre); }
            if (isSeries) genre.seriesId = id; else genre.movieId = id;
        }
        if (--genresPending > 0) return;
        java.util.Collections.sort(genres, (a, b) -> a.name.compareToIgnoreCase(b.name));
        buildGenreChips();
    }

    private void buildGenreChips() {
        if (genreRow == null) return;
        Context context = genreRow.getContext();
        genreRow.removeAllViews();
        if (genres.isEmpty()) {
            ((View) genreRow.getParent()).setVisibility(View.GONE);
            return;
        }
        ((View) genreRow.getParent()).setVisibility(View.VISIBLE);
        genreRow.addView(chip(context, TjLocale.getString(R.string.TjWatchAllGenres), null),
                LayoutHelper.createLinear(-2, 32, 0, 0, 6, 0));
        for (Genre genre : genres) {
            if (!fits(genre)) continue;
            genreRow.addView(chip(context, genre.name, genre), LayoutHelper.createLinear(-2, 32, 0, 0, 6, 0));
        }
        styleChips();
    }

    private TextView chip(Context context, String label, Genre genre) {
        TextView view = new TextView(context);
        view.setTextSize(13);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        view.setTag(genre);
        view.setOnClickListener(v -> {
            selectedGenre = genre;
            if (searchItem != null && searchItem.isSearchFieldVisible()) actionBar.closeSearchField(true);
            query = "";
            // Closing the box queues its own reload; this one is immediate and says the same thing.
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            styleChips();
            refreshContinue();
            reload();
        });
        ScaleStateListAnimator.apply(view, 0.05f, 1.2f);
        return view;
    }

    private void styleChips() {
        if (genreRow == null) return;
        for (int a = 0; a < genreRow.getChildCount(); a++) {
            View view = genreRow.getChildAt(a);
            boolean chosen = view.getTag() == selectedGenre;
            view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(16),
                    chosen ? Theme.getColor(Theme.key_featuredStickers_addButton)
                            : Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_listSelector)));
            ((TextView) view).setTextColor(chosen ? Theme.getColor(Theme.key_featuredStickers_buttonText)
                    : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    /**
     * What was left part-way through, kept deliberately small: two to a row, lying down, artwork at
     * the side. It is a reminder, not the point of the screen - as tall cards it pushed everything
     * worth browsing off the bottom. The rest of the list is one tap away, under the header.
     */
    private View continueSection(Context context) {
        continueSection = new LinearLayout(context);
        continueSection.setOrientation(LinearLayout.VERTICAL);
        continueSection.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView header = new TextView(context);
        header.setTextSize(13);
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        header.setText(TjLocale.getString(R.string.TjWatchContinue));
        header.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        head.addView(header, LayoutHelper.createLinear(0, -2, 1f));

        TextView all = new TextView(context);
        all.setTextSize(13);
        all.setText(TjLocale.getString(R.string.TjWatchAll));
        all.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        all.setPadding(dp(8), dp(4), dp(8), dp(4));
        all.setOnClickListener(v -> presentFragment(new TjWatchHistoryActivity()));
        head.addView(all, LayoutHelper.createLinear(-2, -2));

        continueSection.addView(head, LayoutHelper.createLinear(-1, -2, 14, 14, 14, 0));

        continueRow = new LinearLayout(context);
        continueRow.setOrientation(LinearLayout.VERTICAL);
        continueSection.addView(continueRow, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 2));
        return continueSection;
    }

    private void refreshContinue() {
        if (continueRow == null) return;
        ArrayList<TjWatchHistory.Entry> entries = query.trim().isEmpty() && gate == null && selectedGenre == null
                ? TjWatchHistory.unfinished() : new ArrayList<>();
        continueRow.removeAllViews();
        continueSection.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        Context context = continueRow.getContext();
        // Two rows of two. More than that stops being a reminder and starts being the screen.
        int shown = Math.min(entries.size(), 4);
        for (int a = 0; a < shown; a += 2) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int b = a; b < Math.min(a + 2, shown); b++) {
                TjWatchHistory.Entry entry = entries.get(b);
                ResumeCell cell = new ResumeCell(context);
                cell.bind(entry);
                cell.setOnClickListener(v -> resume(entry));
                cell.setOnLongClickListener(v -> { askToForget(entry); return true; });
                row.addView(cell, LayoutHelper.createLinear(0, 56, 1f, 4, 0, 4, 0));
            }
            // An odd last card keeps its half of the row rather than stretching across it.
            if (Math.min(a + 2, shown) - a == 1) {
                row.addView(new View(context), LayoutHelper.createLinear(0, 56, 1f));
            }
            continueRow.addView(row, LayoutHelper.createLinear(-1, -2, 8, 0, 8, 8));
        }
    }

    private void resume(TjWatchHistory.Entry entry) {
        org.telegram.messenger.MessageObject message = entry.message();
        if (message == null) {
            TjWatchHistory.forget(entry.key, entry.owner);
            refreshContinue();
            return;
        }
        org.telegram.ui.Components.TjMediaPlayback.open(this,
                new TjMediaLibrary.Entry(entry.account, entry.owner, message), entry.position);
    }

    private void askToForget(TjWatchHistory.Entry entry) {
        if (getParentActivity() == null) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setTitle(entry.name)
                .setMessage(TjLocale.getString(R.string.TjWatchForgetInfo))
                .setPositiveButton(TjLocale.getString(R.string.TjWatchForget), (dialog, which) -> {
                    TjWatchHistory.forget(entry.key, entry.owner);
                    refreshContinue();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    /**
     * Without a key of their own there is no catalogue to browse, so the screen says so and offers
     * the one thing that would change it rather than opening on an empty grid.
     */
    private void showGate(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(32), dp(24), dp(32), dp(24));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_played);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.SRC_IN));
        panel.addView(icon, LayoutHelper.createLinear(56, 56, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        TextView text = new TextView(context);
        text.setTextSize(15);
        text.setGravity(Gravity.CENTER);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setText(TjLocale.getString(R.string.TjWatchNeedsKey));
        panel.addView(text, LayoutHelper.createLinear(-1, -2));

        TextView button = new TextView(context);
        button.setTextSize(15);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        button.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(20),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        button.setText(TjLocale.getString(R.string.TjWatchAddKey));
        button.setOnClickListener(v -> askForKey());
        ScaleStateListAnimator.apply(button, 0.04f, 1.2f);
        panel.addView(button, LayoutHelper.createLinear(-2, 40, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        gate = panel;
        root.addView(panel, LayoutHelper.createFrame(-1, -2, Gravity.CENTER));
        listView.setVisibility(View.GONE);
        status.setVisibility(View.GONE);
        if (searchItem != null) searchItem.setVisibility(View.GONE);
    }

    private void askForKey() {
        TjTmdbKeyDialog.show(this, () -> {
            if (getParentActivity() == null) return;
            if (gate != null) { root.removeView(gate); gate = null; }
            listView.setVisibility(View.VISIBLE);
            status.setVisibility(View.VISIBLE);
            if (searchItem != null) searchItem.setVisibility(View.VISIBLE);
            trending();
            loadGenres();
            refreshContinue();
        });
    }

    /** One place the typed text arrives, whichever way it changed. */
    private void typed(String text) {
        if (query.equals(text)) return;
        query = text;
        refreshContinue();
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        AndroidUtilities.runOnUIThread(searchRunnable, 400);
    }

    private boolean wantsSeries() { return kind != KIND_MOVIES; }
    private boolean wantsMovies() { return kind != KIND_SERIES; }

    /**
     * Clears the grid and says how many answers are still owed. A half that is not being asked is
     * told to forget whatever it was fetching, so a late reply cannot land in the new list.
     */
    private void begin(boolean askSeries, boolean askMovies) {
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        if (!askSeries) series.cancel();
        if (!askMovies) movies.cancel();
        pending = (askSeries ? 1 : 0) + (askMovies ? 1 : 0);
        if (pending == 0) collect(null, false, TjTmdb.OK);
    }

    private void trending() {
        boolean askSeries = wantsSeries(), askMovies = wantsMovies();
        begin(askSeries, askMovies);
        if (askSeries) series.trending(currentAccount, true, (body, error) -> collect(body, true, error));
        if (askMovies) movies.trending(currentAccount, false, (body, error) -> collect(body, false, error));
    }

    private void discover(Genre genre) {
        boolean askSeries = wantsSeries() && genre.seriesId > 0;
        boolean askMovies = wantsMovies() && genre.movieId > 0;
        begin(askSeries, askMovies);
        if (askSeries) series.discover(currentAccount, true, genre.seriesId, (body, error) -> collect(body, true, error));
        if (askMovies) movies.discover(currentAccount, false, genre.movieId, (body, error) -> collect(body, false, error));
    }

    private void reload() {
        if (!TjTmdb.available(currentAccount)) return;
        String typed = query.trim();
        if (typed.isEmpty()) {
            if (selectedGenre != null) discover(selectedGenre); else trending();
            return;
        }
        // Typing is a question about a title, which no longer belongs to whichever genre was open.
        if (selectedGenre != null) { selectedGenre = null; styleChips(); }
        // A person types the episode into the search box as readily as the name. The name is what
        // the catalogue is asked about; the numbers are carried into the title screen.
        String name = TjMediaTitle.parse("", typed).title;
        if (name == null || name.trim().isEmpty()) name = typed;
        boolean askSeries = wantsSeries(), askMovies = wantsMovies();
        begin(askSeries, askMovies);
        final String asked = name;
        if (askSeries) series.search(currentAccount, asked, true, (body, error) -> collect(body, true, error));
        if (askMovies) movies.search(currentAccount, asked, false, (body, error) -> collect(body, false, error));
    }

    private void collect(JSONObject body, boolean isSeries, int error) {
        if (body != null) {
            JSONArray results = body.optJSONArray("results");
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject object = results.optJSONObject(i);
                if (object == null) continue;
                Item item = new Item(object, isSeries);
                if (item.id > 0 && !item.name.isEmpty()) items.add(item);
            }
        }
        if (pending > 0 && --pending > 0) return;
        // What people are actually watching first. Sorting by score put an obscure title with four
        // votes above everything anyone came here for.
        items.sort((a, b) -> Double.compare(b.popularity, a.popularity));
        adapter.notifyDataSetChanged();
        if (!items.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(TjLocale.getString(error == TjTmdb.CREDENTIAL ? R.string.TjWatchNeedsKey
                    : error == TjTmdb.NETWORK ? R.string.TjWatchOffline : R.string.TjWatchNothing));
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return items.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            PosterCell cell = new PosterCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((PosterCell) holder.itemView).bind(items.get(position));
        }
    }

    /**
     * One thing left part-way through, on its side: a small piece of the artwork, the name, which
     * episode, and a hairline across the bottom for how far in it got.
     */
    static class ResumeCell extends FrameLayout {
        private final BackupImageView image;
        private final TextView name, where;
        private final View track, bar;

        ResumeCell(Context context) {
            super(context);
            setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10),
                    Theme.getColor(Theme.key_windowBackgroundWhite),
                    Theme.getColor(Theme.key_listSelector)));
            setClipToOutline(true);

            image = new BackupImageView(context);
            addView(image, LayoutHelper.createFrame(38, 56,
                    LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT));

            // No play glyph here: over a thumbnail this small it covers the picture it is on.
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setGravity(Gravity.CENTER_VERTICAL);
            name = new TextView(context);
            name.setTextSize(12);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(name, LayoutHelper.createLinear(-1, -2));
            where = new TextView(context);
            where.setTextSize(10);
            where.setSingleLine(true);
            where.setEllipsize(android.text.TextUtils.TruncateAt.END);
            where.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            where.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            texts.addView(where, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
            addView(texts, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL,
                    LocaleController.isRTL ? 8 : 48, 0, LocaleController.isRTL ? 48 : 8, 0));

            track = new View(context);
            track.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
            addView(track, LayoutHelper.createFrame(-1, 2, Gravity.BOTTOM,
                    LocaleController.isRTL ? 0 : 38, 0, LocaleController.isRTL ? 38 : 0, 0));
            bar = new View(context);
            bar.setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            addView(bar, LayoutHelper.createFrame(0, 2, Gravity.BOTTOM
                    | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT),
                    LocaleController.isRTL ? 0 : 38, 0, LocaleController.isRTL ? 38 : 0, 0));

            ScaleStateListAnimator.apply(this, 0.02f, 1.2f);
        }

        void bind(TjWatchHistory.Entry entry) {
            String poster = TjTmdb.posterUrl(entry.poster);
            image.setImage(poster.isEmpty() ? null : poster, "90_135", (android.graphics.drawable.Drawable) null);
            name.setText(entry.name);
            if (entry.season >= 0 || entry.episode >= 0) {
                StringBuilder text = new StringBuilder();
                if (entry.season >= 0) text.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(entry.season);
                if (entry.episode >= 0) {
                    if (text.length() > 0) text.append(" · ");
                    text.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(entry.episode);
                }
                where.setVisibility(VISIBLE);
                where.setText(text);
            } else if (entry.year > 0) {
                where.setVisibility(VISIBLE);
                where.setText(String.valueOf(entry.year));
            } else {
                where.setVisibility(GONE);
            }
            final float progress = entry.progress();
            track.setVisibility(progress > 0 ? VISIBLE : GONE);
            bar.setVisibility(progress > 0 ? VISIBLE : GONE);
            // The card has no width until it is measured, so the bar is sized against it then.
            post(() -> {
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                params.width = (int) Math.max(0, (getMeasuredWidth() - dp(38)) * progress);
                bar.setLayoutParams(params);
            });
            setContentDescription(entry.name);
        }
    }

    /** A poster with its score in the corner and its name underneath. */
    static class PosterCell extends LinearLayout {
        private final BackupImageView image;
        private final TextView name;
        private final TextView rating;

        PosterCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(dp(6), dp(6), dp(6), dp(10));
            FrameLayout art = new FrameLayout(context);
            art.setClipToOutline(true);
            art.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray)));
            addView(art, LayoutHelper.createLinear(-1, -2));
            image = new BackupImageView(context) {
                @Override protected void onMeasure(int widthSpec, int heightSpec) {
                    int width = MeasureSpec.getSize(widthSpec);
                    super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(width * 3 / 2, MeasureSpec.EXACTLY));
                }
            };
            art.addView(image, LayoutHelper.createFrame(-1, -2));
            rating = new TextView(context);
            rating.setTextSize(11);
            rating.setTextColor(0xffffffff);
            rating.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            rating.setPadding(dp(6), dp(2), dp(6), dp(3));
            rating.setBackground(Theme.createRoundRectDrawable(dp(6), 0xcc000000));
            art.addView(rating, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 6, 6, 6, 6));
            name = new TextView(context);
            name.setTextSize(13);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(name, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));
            ScaleStateListAnimator.apply(this, 0.03f, 1.2f);
        }

        void bind(Item item) {
            String poster = TjTmdb.posterUrl(item.poster);
            image.setImage(poster.isEmpty() ? null : poster, "320_480", (android.graphics.drawable.Drawable) null);
            String year = item.year();
            name.setText(year.isEmpty() ? item.name : item.name + " (" + year + ")");
            if (item.rating > 0) {
                rating.setVisibility(VISIBLE);
                rating.setText(String.format(java.util.Locale.US, "★ %.1f", item.rating));
            } else {
                rating.setVisibility(GONE);
            }
            setContentDescription(item.name);
        }
    }
}
