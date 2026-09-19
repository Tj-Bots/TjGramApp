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

        Shelf(String title, Genre genre) { this.title = title; this.genre = genre; }
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

        content.addView(continueSection(context), LayoutHelper.createLinear(-1, -2));

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
            ArrayList<View> cells = new ArrayList<>();
            for (int b = a; b < Math.min(a + 2, shown); b++) {
                TjWatchHistory.Entry entry = entries.get(b);
                ResumeCell cell = new ResumeCell(context);
                cell.bind(entry);
                cell.setOnClickListener(v -> resume(entry));
                cell.setOnLongClickListener(v -> { askToForget(entry); return true; });
                cells.add(cell);
            }
            if (LocaleController.isRTL) java.util.Collections.reverse(cells);
            for (View cell : cells) row.addView(cell, LayoutHelper.createLinear(0, 56, 1f, 4, 0, 4, 0));
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
        if (trendingShelf == null) trendingShelf = new Shelf(TjLocale.getString(R.string.TjWatchTrending), null);
        shelves.add(trendingShelf);
        for (Genre genre : genres) {
            if (!fits(genre)) continue;
            shelves.add(new Shelf(genre.name, genre));
            if (shelves.size() > MAX_SHELVES) break;
        }
        if (shelfAdapter != null) shelfAdapter.notifyDataSetChanged();
        status.setVisibility(View.GONE);
    }

    /** Fills one row. Which halves it asks depends on what the row is and what is being shown. */
    private void loadShelf(Shelf shelf) {
        if (shelf.asked) return;
        shelf.asked = true;
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
                if (bound != null && bound.genre != null) selectGenre(bound.genre);
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
                @Override public int getItemCount() { return shown.size(); }
                @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
                @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                    ArtCell cell = new ArtCell(parent.getContext());
                    cell.setLayoutParams(new RecyclerView.LayoutParams(dp(104), -2));
                    return new RecyclerListView.Holder(cell);
                }
                @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                    ((ArtCell) holder.itemView).bind(shown.get(position));
                }
            });
            row.setOnItemClickListener((view, position) -> {
                if (position >= 0 && position < shown.size()) open(shown.get(position));
            });
            addView(row, LayoutHelper.createLinear(-1, -2, 0, 0, 0, 6));
        }

        void bind(Shelf shelf) {
            bound = shelf;
            title.setText(shelf.title);
            chevron.setVisibility(shelf.genre == null ? GONE : VISIBLE);
            shown.clear();
            shown.addAll(shelf.items);
            row.getAdapter().notifyDataSetChanged();
            row.scrollToPosition(0);
            // An empty row is still on its way; leaving the header alone keeps the screen still.
            row.setVisibility(shown.isEmpty() ? GONE : VISIBLE);
        }
    }

    /** Artwork alone, the way a row of these is read. */
    static class ArtCell extends FrameLayout {
        private final BackupImageView image;
        private final TextView rating;

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
            setContentDescription(item.name);
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
