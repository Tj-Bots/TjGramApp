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
import androidx.recyclerview.widget.LinearLayoutManager;
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
import java.util.HashSet;

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

    /** One row of the home screen: a name, and what is filed under it. */
    private static final class Shelf {
        final String title;
        final Genre genre;
        final ArrayList<Item> items = new ArrayList<>();
        final TjTmdb seriesClient = new TjTmdb(), movieClient = new TjTmdb();
        boolean asked;
        int pending;
        /** The "continue watching" row: what was left part-way, instead of catalogue items. */
        ArrayList<TjWatchHistory.Entry> resume;
        /** The "for you" row: the recently watched titles its suggestions come from. */
        ArrayList<TjWatchHistory.Entry> forYou;

        Shelf(String title, Genre genre) { this.title = title; this.genre = genre; }
    }

    /** How many of the most recently watched titles feed the "for you" row. */
    private static final int FOR_YOU_SOURCES = 5;
    private Shelf continueShelf;
    private Shelf forYouShelf;
    private String forYouKey = "";
    /** Genre rows keep what they fetched; coming back to the screen must not ask for them again. */
    private final java.util.HashMap<String, Shelf> genreShelves = new java.util.HashMap<>();

    /** The last few watched titles, most recent first, of the kind being shown. */
    private ArrayList<TjWatchHistory.Entry> forYouSources() {
        ArrayList<TjWatchHistory.Entry> sources = new ArrayList<>();
        for (TjWatchHistory.Entry entry : TjWatchHistory.all()) {
            if (sources.size() >= FOR_YOU_SOURCES) break;
            if (entry.series ? !wantsSeries() : !wantsMovies()) continue;
            if (entry.name.isEmpty() || titleId(entry) <= 0) continue;
            sources.add(entry);
        }
        return sources;
    }

    private static long titleId(TjWatchHistory.Entry entry) {
        try {
            return Long.parseLong(entry.key.substring(entry.key.indexOf(':') + 1));
        } catch (Exception e) {
            return 0;
        }
    }

    private static final int MENU_HISTORY = 1, MENU_SETTINGS = 2, MENU_COPY_LINK = 3;
    /** How many genre rows the home screen offers before it becomes a list of lists. */
    private static final int MAX_SHELVES = 12;

    /** Everything, or only one half of it. Netflix's first question, and a reasonable one. */
    private static final int KIND_ALL = 0, KIND_MOVIES = 1, KIND_SERIES = 2;

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Item> incoming = new ArrayList<>();
    private final HashSet<String> seen = new HashSet<>();
    private final ArrayList<Shelf> shelves = new ArrayList<>();
    private final ArrayList<Genre> genres = new ArrayList<>();
    private final TjTmdb series = new TjTmdb();
    private final TjTmdb movies = new TjTmdb();
    private final TjTmdb seriesGenres = new TjTmdb();
    private final TjTmdb movieGenres = new TjTmdb();

    private FrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private ShelfAdapter shelfAdapter;
    private Shelf trendingShelf;
    private boolean gridMode;
    private int page = 1;
    private boolean loadingMore, moreSeries, moreMovies;
    private ActionBarMenuItem searchItem;
    private TextView status;
    private View gate;
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
                else if (id == MENU_COPY_LINK) {
                    org.telegram.messenger.AndroidUtilities.addToClipboard(
                            org.telegram.messenger.tj.TjSettingsLinks.build(
                                    org.telegram.messenger.tj.TjSettingsLinks.Section.WATCH, null));
                    org.telegram.ui.Components.BulletinFactory.of(TjWatchActivity.this).createCopyLinkBulletin().show();
                }
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
        other.addSubItem(MENU_COPY_LINK, R.drawable.msg_link2, LocaleController.getString(R.string.CopyLink));

        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(-1, -1));

        content.addView(kindSection(context), LayoutHelper.createLinear(-1, 34, 12, 10, 12, 0));

        content.addView(genreSection(context), LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));


        status = new TextView(context);
        status.setTextSize(14);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(28), dp(28), dp(28), dp(12));
        content.addView(status, LayoutHelper.createLinear(-1, -2));

        listView = new RecyclerListView(context);
        listView.setClipToPadding(false);
        listView.setVerticalScrollBarEnabled(false);
        adapter = new Adapter();
        shelfAdapter = new ShelfAdapter();
        listView.setOnItemClickListener((view, position) -> {
            if (!gridMode || position < 0 || position >= items.size()) return;
            open(items.get(position));
        });
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView view, int dx, int dy) {
                if (dy <= 0) return;
                checkLoadMore();
            }
        });
        content.addView(listView, LayoutHelper.createLinear(-1, -1));
        applyMode();

        if (!TjTmdb.available(currentAccount)) {
            showGate(context);
        } else {
            buildShelves();
            loadGenres();
        }
        refreshContinue();
        return fragmentView;
    }

    private void open(Item item) {
        presentFragment(new TjTitleActivity(item.id, item.series, item.name));
    }

    /**
     * The home screen is rows you read across; a search or a chosen genre is a grid you read down.
     * Both live in the same list, so switching is a layout and an adapter rather than a screen.
     */
    private void applyMode() {
        if (listView == null) return;
        if (gridMode) {
            listView.setLayoutManager(new GridLayoutManager(getParentActivity(), 3) {
                // The app declares no RTL support and mirrors by hand, so the manager has to be
                // told; left to itself it fills a Hebrew grid from the left like an English one.
                @Override protected boolean isLayoutRTL() { return LocaleController.isRTL; }
            });
            listView.setPadding(dp(6), dp(6), dp(6), dp(24));
            if (listView.getAdapter() != adapter) listView.setAdapter(adapter);
        } else {
            listView.setLayoutManager(new LinearLayoutManager(getParentActivity()));
            listView.setPadding(0, dp(2), 0, dp(24));
            if (listView.getAdapter() != shelfAdapter) listView.setAdapter(shelfAdapter);
        }
    }

    /**
     * Back goes back inside the screen before it leaves it. Opening a category replaces what the
     * list shows rather than pushing a screen, so without this a single step out of a category
     * threw the whole of Watch away and landed on the chat list.
     */
    @Override
    public boolean onBackPressed(boolean invoked) {
        if (actionBar != null && actionBar.isSearchFieldVisible()) {
            if (invoked) actionBar.closeSearchField(true);
            return false;
        }
        if (gridMode) {
            if (invoked) selectGenre(null);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    /**
     * No sliding this screen away. Browsing is done by dragging sideways, and every row here moves
     * that way, so the gesture that closes the screen was firing on rows people were only reading.
     * The screens opened from here keep it.
     */
    @Override
    public boolean isSwipeBackEnabled(android.view.MotionEvent event) {
        return false;
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
        // Added back to front in a language read that way, since nothing here is mirrored for us.
        int[] order = LocaleController.isRTL ? new int[]{KIND_SERIES, KIND_MOVIES, KIND_ALL}
                : new int[]{KIND_ALL, KIND_MOVIES, KIND_SERIES};
        for (int value : order) {
            kindRow.addView(segment(context, TjLocale.getString(value == KIND_ALL ? R.string.TjWatchAll
                            : value == KIND_MOVIES ? R.string.TjMediaMovies : R.string.TjMediaSeries), value),
                    LayoutHelper.createLinear(0, -1, 1f));
        }
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
            // Every row is about one half of the catalogue, so they are all asked again.
            if (trendingShelf != null) { trendingShelf.asked = false; trendingShelf.items.clear(); }
            genreShelves.clear();
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
        if (!gridMode) buildShelves();
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
        ArrayList<View> chips = new ArrayList<>();
        chips.add(chip(context, TjLocale.getString(R.string.TjWatchAllGenres), null));
        for (Genre genre : genres) {
            if (fits(genre)) chips.add(chip(context, genre.name, genre));
        }
        // Nothing here is mirrored for us, so the first chip is added last when the row is read
        // from the right, and the row is scrolled to that end once it has a width.
        if (LocaleController.isRTL) java.util.Collections.reverse(chips);
        for (View view : chips) genreRow.addView(view, LayoutHelper.createLinear(-2, 32, 0, 0, 6, 0));
        styleChips();
        if (LocaleController.isRTL) {
            View parent = (View) genreRow.getParent();
            // scrollTo rather than fullScroll: the latter also hands focus to a chip.
            parent.post(() -> parent.scrollTo(genreRow.getWidth(), 0));
        }
    }

    private TextView chip(Context context, String label, Genre genre) {
        TextView view = new TextView(context);
        view.setTextSize(13);
        view.setText(label);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(14), 0, dp(14), 0);
        view.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        view.setTag(genre);
        view.setOnClickListener(v -> selectGenre(genre));
        ScaleStateListAnimator.apply(view, 0.05f, 1.2f);
        return view;
    }

    /**
     * Opening one genre in full, from its chip or from the name above its row. Whatever was typed
     * goes with it: a search and a genre are two different questions, and only one is being asked.
     */
    private void selectGenre(Genre genre) {
        selectedGenre = genre;
        if (searchItem != null && searchItem.isSearchFieldVisible()) actionBar.closeSearchField(true);
        query = "";
        // Closing the box queues its own reload; this one is immediate and says the same thing.
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        styleChips();
        refreshContinue();
        reload();
        if (listView != null) listView.scrollToPosition(0);
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
     * What was left part-way through becomes the first row of the list, drawn like the others -
     * posters read across, with a red line for how far in - so it scrolls away with the rest
     * instead of sitting over it.
     */
    private void refreshContinue() {
        if (continueShelf == null) continueShelf = new Shelf(TjLocale.getString(R.string.TjWatchContinue), null);
        ArrayList<TjWatchHistory.Entry> entries = new ArrayList<>();
        if (query.trim().isEmpty() && gate == null && selectedGenre == null) {
            for (TjWatchHistory.Entry entry : TjWatchHistory.unfinished()) {
                if (entry.series ? wantsSeries() : wantsMovies()) entries.add(entry);
            }
        }
        continueShelf.resume = entries;
        if (!gridMode && gate == null && shelfAdapter != null && TjTmdb.available(currentAccount)) buildShelves();
    }

    private void resume(TjWatchHistory.Entry entry) {
        org.telegram.messenger.MessageObject message = entry.message();
        if (message == null) {
            TjWatchHistory.forget(entry.key, entry.owner);
            refreshContinue();
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
            buildShelves();
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
     * The home screen: what is being watched this week, then a row for each name the catalogue
     * files things under. Rows are not fetched until they are about to be looked at - twelve of
     * them at two requests each would be a minute of waiting for rows nobody scrolled to.
     */
    private void buildShelves() {
        shelves.clear();
        // What was left part-way through, as a row like any other, scrolling away with the rest.
        if (continueShelf != null && continueShelf.resume != null && !continueShelf.resume.isEmpty()) {
            shelves.add(continueShelf);
        }
        // Then one row for you: what TMDB suggests to people who liked the last few titles watched
        // here, taken in turn from each - the part of "for you" a device can know on its own.
        ArrayList<TjWatchHistory.Entry> sources = forYouSources();
        StringBuilder key = new StringBuilder();
        for (TjWatchHistory.Entry entry : sources) key.append(entry.key).append(',');
        if (sources.isEmpty()) {
            forYouShelf = null;
            forYouKey = "";
        } else if (forYouShelf == null || !forYouKey.equals(key.toString())) {
            forYouShelf = new Shelf(TjLocale.getString(R.string.TjWatchForYou), null);
            forYouShelf.forYou = sources;
            forYouKey = key.toString();
        }
        if (forYouShelf != null) shelves.add(forYouShelf);
        if (trendingShelf == null) trendingShelf = new Shelf(TjLocale.getString(R.string.TjWatchTrending), null);
        shelves.add(trendingShelf);
        int genreRows = 0;
        for (Genre genre : genres) {
            if (!fits(genre)) continue;
            Shelf shelf = genreShelves.get(genre.name);
            if (shelf == null) {
                shelf = new Shelf(genre.name, genre);
                genreShelves.put(genre.name, shelf);
            }
            shelves.add(shelf);
            if (++genreRows >= MAX_SHELVES) break;
        }
        if (shelfAdapter != null) shelfAdapter.notifyDataSetChanged();
        status.setVisibility(View.GONE);
    }

    /** Fills one row. Which halves it asks depends on what the row is and what is being shown. */
    private void loadShelf(Shelf shelf) {
        if (shelf.asked) return;
        shelf.asked = true;
        if (shelf.resume != null) return;
        if (shelf.forYou != null) {
            loadForYou(shelf);
            return;
        }
        boolean askSeries = wantsSeries() && (shelf.genre == null || shelf.genre.seriesId > 0);
        boolean askMovies = wantsMovies() && (shelf.genre == null || shelf.genre.movieId > 0);
        shelf.pending = (askSeries ? 1 : 0) + (askMovies ? 1 : 0);
        if (shelf.pending == 0) return;
        if (askSeries) {
            if (shelf.genre == null) shelf.seriesClient.trending(currentAccount, true, (body, error) -> shelfArrived(shelf, body, true));
            else shelf.seriesClient.discover(currentAccount, true, shelf.genre.seriesId, (body, error) -> shelfArrived(shelf, body, true));
        }
        if (askMovies) {
            if (shelf.genre == null) shelf.movieClient.trending(currentAccount, false, (body, error) -> shelfArrived(shelf, body, false));
            else shelf.movieClient.discover(currentAccount, false, shelf.genre.movieId, (body, error) -> shelfArrived(shelf, body, false));
        }
    }

    private void shelfArrived(Shelf shelf, JSONObject body, boolean isSeries) {
        read(body, isSeries, shelf.items, null);
        if (--shelf.pending > 0) return;
        shelf.items.sort((a, b) -> Double.compare(b.popularity, a.popularity));
        if (shelfAdapter != null) shelfAdapter.notifyDataSetChanged();
    }

    /**
     * Asks for suggestions for each source title at once, then deals them out in turn - one from
     * the most recent title, one from the next, and round again - so the row is about all of them,
     * leaving out what was already watched and anything suggested twice.
     */
    private void loadForYou(Shelf shelf) {
        final ArrayList<TjWatchHistory.Entry> sources = shelf.forYou;
        final ArrayList<ArrayList<Item>> answers = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) answers.add(new ArrayList<>());
        final int[] pending = {sources.size()};
        for (int i = 0; i < sources.size(); i++) {
            final int index = i;
            final TjWatchHistory.Entry source = sources.get(i);
            new TjTmdb().recommendations(currentAccount, titleId(source), source.series, (body, error) -> {
                read(body, source.series, answers.get(index), null);
                if (--pending[0] > 0) return;
                java.util.HashSet<String> skip = new java.util.HashSet<>();
                for (TjWatchHistory.Entry entry : TjWatchHistory.all()) skip.add(entry.key);
                shelf.items.clear();
                boolean added = true;
                for (int round = 0; added; round++) {
                    added = false;
                    for (ArrayList<Item> answer : answers) {
                        if (round >= answer.size()) continue;
                        added = true;
                        Item item = answer.get(round);
                        if (skip.add(TjWatchHistory.key(item.id, item.series))) shelf.items.add(item);
                    }
                }
                if (shelfAdapter != null) shelfAdapter.notifyDataSetChanged();
            });
        }
    }

    /** Pulls the titles out of one answer, skipping anything already in the list it fills. */
    private void read(JSONObject body, boolean isSeries, ArrayList<Item> into, HashSet<String> known) {
        JSONArray results = body == null ? null : body.optJSONArray("results");
        for (int i = 0; results != null && i < results.length(); i++) {
            JSONObject object = results.optJSONObject(i);
            if (object == null) continue;
            Item item = new Item(object, isSeries);
            if (item.id <= 0 || item.name.isEmpty()) continue;
            if (known != null && !known.add((isSeries ? "tv" : "movie") + item.id)) continue;
            into.add(item);
        }
    }

    /**
     * Clears the grid and says how many answers are still owed. A half that is not being asked is
     * told to forget whatever it was fetching, so a late reply cannot land in the new list.
     */
    private void begin(boolean askSeries, boolean askMovies) {
        items.clear();
        incoming.clear();
        seen.clear();
        page = 1;
        loadingMore = false;
        moreSeries = moreMovies = false;
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        if (!askSeries) series.cancel();
        if (!askMovies) movies.cancel();
        pending = (askSeries ? 1 : 0) + (askMovies ? 1 : 0);
        if (pending == 0) collect(null, false, TjTmdb.OK);
    }

    private void discover(Genre genre) {
        boolean askSeries = wantsSeries() && genre.seriesId > 0;
        boolean askMovies = wantsMovies() && genre.movieId > 0;
        begin(askSeries, askMovies);
        if (askSeries) series.discover(currentAccount, true, genre.seriesId, page, (body, error) -> collect(body, true, error));
        if (askMovies) movies.discover(currentAccount, false, genre.movieId, page, (body, error) -> collect(body, false, error));
    }

    private void reload() {
        if (!TjTmdb.available(currentAccount)) return;
        String typed = query.trim();
        // Typing is a question about a title, which no longer belongs to whichever genre was open.
        if (!typed.isEmpty() && selectedGenre != null) { selectedGenre = null; styleChips(); }
        gridMode = !typed.isEmpty() || selectedGenre != null;
        applyMode();
        if (!gridMode) {
            series.cancel();
            movies.cancel();
            buildShelves();
            return;
        }
        if (typed.isEmpty()) { discover(selectedGenre); return; }
        // A person types the episode into the search box as readily as the name. The name is what
        // the catalogue is asked about; the numbers are carried into the title screen.
        String name = TjMediaTitle.parse("", typed).title;
        if (name == null || name.trim().isEmpty()) name = typed;
        boolean askSeries = wantsSeries(), askMovies = wantsMovies();
        begin(askSeries, askMovies);
        final String asked = name;
        if (askSeries) series.search(currentAccount, asked, true, page, (body, error) -> collect(body, true, error));
        if (askMovies) movies.search(currentAccount, asked, false, page, (body, error) -> collect(body, false, error));
    }

    /** Another page of the same question, once the end of what is shown comes into view. */
    private void checkLoadMore() {
        if (!gridMode || loadingMore || pending > 0 || items.isEmpty()) return;
        boolean askSeries = wantsSeries() && moreSeries;
        boolean askMovies = wantsMovies() && moreMovies;
        if (!askSeries && !askMovies) return;
        RecyclerView.LayoutManager manager = listView.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager)) return;
        if (((LinearLayoutManager) manager).findLastVisibleItemPosition() < items.size() - 6) return;

        loadingMore = true;
        page++;
        incoming.clear();
        pending = (askSeries ? 1 : 0) + (askMovies ? 1 : 0);
        String typed = query.trim();
        if (selectedGenre != null) {
            if (askSeries) series.discover(currentAccount, true, selectedGenre.seriesId, page, (body, error) -> collect(body, true, error));
            if (askMovies) movies.discover(currentAccount, false, selectedGenre.movieId, page, (body, error) -> collect(body, false, error));
        } else {
            String name = TjMediaTitle.parse("", typed).title;
            if (name == null || name.trim().isEmpty()) name = typed;
            final String asked = name;
            if (askSeries) series.search(currentAccount, asked, true, page, (body, error) -> collect(body, true, error));
            if (askMovies) movies.search(currentAccount, asked, false, page, (body, error) -> collect(body, false, error));
        }
    }

    private void collect(JSONObject body, boolean isSeries, int error) {
        if (body != null) {
            boolean more = body.optInt("page", 1) < body.optInt("total_pages", 1);
            if (isSeries) moreSeries = more; else moreMovies = more;
            read(body, isSeries, incoming, seen);
        }
        if (pending > 0 && --pending > 0) return;
        // What people are actually watching first, within the page that just arrived. Sorting the
        // whole list again would shuffle what is already on screen under the reader's thumb.
        incoming.sort((a, b) -> Double.compare(b.popularity, a.popularity));
        int from = items.size();
        items.addAll(incoming);
        incoming.clear();
        loadingMore = false;
        if (from == 0) adapter.notifyDataSetChanged(); else adapter.notifyItemRangeInserted(from, items.size() - from);
        if (!items.isEmpty()) {
            status.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.VISIBLE);
            status.setText(TjLocale.getString(error == TjTmdb.CREDENTIAL ? R.string.TjWatchNeedsKey
                    : error == TjTmdb.NETWORK ? R.string.TjWatchOffline : R.string.TjWatchNothing));
        }
    }

    /** The home screen's rows. */
    private class ShelfAdapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return shelves.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return false; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ShelfCell cell = new ShelfCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            Shelf shelf = shelves.get(position);
            // Asked for the first time as it comes into view, not when the screen was built.
            loadShelf(shelf);
            ((ShelfCell) holder.itemView).bind(shelf);
        }
    }

    /**
     * One row: the name of what is in it, and the artwork read across. The name is the way into the
     * whole of it - a row shows what happens to fit, and there is always more than that.
     */
    private class ShelfCell extends LinearLayout {
        private final TextView title;
        private final ImageView chevron;
        private final RecyclerListView row;
        private final ArrayList<Item> shown = new ArrayList<>();
        private final ArrayList<TjWatchHistory.Entry> resumeShown = new ArrayList<>();
        private Shelf bound;

        ShelfCell(Context context) {
            super(context);
            setOrientation(VERTICAL);

            LinearLayout head = new LinearLayout(context);
            head.setOrientation(HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(dp(14), dp(12), dp(14), dp(8));
            head.setBackground(Theme.getSelectorDrawable(false));
            head.setOnClickListener(v -> {
                if (bound == null) return;
                if (bound.genre != null) selectGenre(bound.genre);
                else if (bound.resume != null) presentFragment(new TjWatchHistoryActivity());
            });

            title = new TextView(context);
            title.setTextSize(15);
            title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);

            chevron = new ImageView(context);
            chevron.setImageResource(R.drawable.msg_arrowright);
            chevron.setScaleType(ImageView.ScaleType.CENTER);
            chevron.setColorFilter(new PorterDuffColorFilter(
                    Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
            // It points the way the reader is going, and sits at the end of the line they finish on.
            if (LocaleController.isRTL) chevron.setScaleX(-1);
            if (LocaleController.isRTL) head.addView(chevron, LayoutHelper.createLinear(20, 20));
            head.addView(title, LayoutHelper.createLinear(0, -2, 1f));
            if (!LocaleController.isRTL) head.addView(chevron, LayoutHelper.createLinear(20, 20));
            addView(head, LayoutHelper.createLinear(-1, -2));

            row = new RecyclerListView(context);
            row.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL,
                    LocaleController.isRTL));
            row.setClipToPadding(false);
            row.setPadding(dp(10), 0, dp(10), 0);
            row.setHorizontalScrollBarEnabled(false);
            row.setNestedScrollingEnabled(false);
            row.setAdapter(new RecyclerListView.SelectionAdapter() {
                @Override public int getItemCount() { return resumeShown.isEmpty() ? shown.size() : resumeShown.size(); }
                @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    ArtCell cell = new ArtCell(parent.getContext());
                    cell.setLayoutParams(new RecyclerView.LayoutParams(dp(104), -2));
                    return new RecyclerListView.Holder(cell);
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    if (!resumeShown.isEmpty()) ((ArtCell) holder.itemView).bind(resumeShown.get(position));
                    else ((ArtCell) holder.itemView).bind(shown.get(position));
                }
            });
            row.setOnItemClickListener((view, position) -> {
                if (!resumeShown.isEmpty()) {
                    if (position >= 0 && position < resumeShown.size()) resume(resumeShown.get(position));
                } else if (position >= 0 && position < shown.size()) {
                    open(shown.get(position));
                }
            });
            row.setOnItemLongClickListener((view, position) -> {
                if (resumeShown.isEmpty() || position < 0 || position >= resumeShown.size()) return false;
                askToForget(resumeShown.get(position));
                return true;
            });
            addView(row, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));
        }

        void bind(Shelf shelf) {
            bound = shelf;
            title.setText(shelf.title);
            chevron.setVisibility(shelf.genre == null && shelf.resume == null ? GONE : VISIBLE);
            shown.clear();
            shown.addAll(shelf.items);
            resumeShown.clear();
            if (shelf.resume != null) resumeShown.addAll(shelf.resume);
            row.getAdapter().notifyDataSetChanged();
            row.scrollToPosition(0);
            // An empty row is still on its way; leaving the header alone keeps the screen still.
            row.setVisibility(shown.isEmpty() && resumeShown.isEmpty() ? GONE : VISIBLE);
        }
    }

    /** Artwork alone, the way a row of these is read. */
    static class ArtCell extends FrameLayout {
        private final BackupImageView image;
        private final TextView rating;
        private final WatchedLine watched;

        ArtCell(Context context) {
            super(context);
            setPadding(dp(4), dp(2), dp(4), dp(2));
            FrameLayout art = new FrameLayout(context);
            art.setClipToOutline(true);
            art.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
            image = new BackupImageView(context);
            art.addView(image, LayoutHelper.createFrame(-1, -1));
            rating = new TextView(context);
            rating.setTextSize(10);
            rating.setTextColor(0xffffffff);
            rating.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            rating.setPadding(dp(5), dp(1), dp(5), dp(2));
            rating.setBackground(Theme.createRoundRectDrawable(dp(5), 0xcc000000));
            art.addView(rating, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 5, 5, 5, 5));
            watched = new WatchedLine(context);
            watched.setVisibility(GONE);
            art.addView(watched, LayoutHelper.createFrame(-1, 4, Gravity.BOTTOM));
            addView(art, LayoutHelper.createFrame(96, 144));
            ScaleStateListAnimator.apply(this, 0.04f, 1.2f);
        }

        void bind(Item item) {
            String poster = TjTmdb.posterUrl(item.poster);
            image.setImage(poster.isEmpty() ? null : poster, "320_480", (android.graphics.drawable.Drawable) null);
            if (item.rating > 0) {
                rating.setVisibility(VISIBLE);
                rating.setText(String.format(java.util.Locale.US, "\u2605 %.1f", item.rating));
            } else {
                rating.setVisibility(GONE);
            }
            watched.setVisibility(GONE);
            setContentDescription(item.name);
        }

        /** Something left part-way: the poster, which episode, and the red line for how far in. */
        void bind(TjWatchHistory.Entry entry) {
            String poster = TjTmdb.posterUrl(entry.poster);
            image.setImage(poster.isEmpty() ? null : poster, "320_480", (android.graphics.drawable.Drawable) null);
            if (entry.season >= 0 || entry.episode >= 0) {
                StringBuilder text = new StringBuilder();
                if (entry.season > 0) text.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(entry.season);
                if (entry.episode >= 0) {
                    if (text.length() > 0) text.append(" · ");
                    text.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(entry.episode);
                }
                rating.setVisibility(VISIBLE);
                rating.setText(text);
            } else {
                rating.setVisibility(GONE);
            }
            watched.setProgress(entry.progress());
            watched.setVisibility(VISIBLE);
            setContentDescription(entry.name);
        }
    }

    /** A grey track along the bottom of a poster with the watched part in red. */
    static class WatchedLine extends View {
        private float progress;
        private final android.graphics.Paint track = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Paint red = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        WatchedLine(Context context) {
            super(context);
            track.setColor(0x99808080);
            red.setColor(0xFFE50914);
        }

        void setProgress(float value) {
            progress = Math.max(0f, Math.min(1f, value));
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            float w = getWidth(), h = getHeight();
            canvas.drawRect(0, 0, w, h, track);
            if (LocaleController.isRTL) canvas.drawRect(w - w * progress, 0, w, h, red);
            else canvas.drawRect(0, 0, w * progress, h, red);
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
