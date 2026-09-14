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
        public final double rating;

        Item(JSONObject object, boolean series) {
            this.series = series;
            id = object.optLong("id");
            name = object.optString(series ? "name" : "title", "");
            poster = object.optString("poster_path", "");
            date = object.optString(series ? "first_air_date" : "release_date", "");
            rating = object.optDouble("vote_average", 0);
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

    private final ArrayList<Item> items = new ArrayList<>();
    private final ArrayList<Genre> genres = new ArrayList<>();
    private final TjTmdb series = new TjTmdb();
    private final TjTmdb movies = new TjTmdb();
    private final TjTmdb seriesGenres = new TjTmdb();
    private final TjTmdb movieGenres = new TjTmdb();

    private FrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private EditTextBoldCursor search;
    private TextView status;
    private View gate;
    private LinearLayout continueSection;
    private LinearLayout continueRow;
    private LinearLayout genreRow;
    private Genre selectedGenre;
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
        ActionBarMenuItem other = actionBar.createMenu().addItem(0, R.drawable.ic_ab_other);
        other.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        other.addSubItem(MENU_HISTORY, R.drawable.msg_recent, TjLocale.getString(R.string.TjWatchHistory));
        other.addSubItem(MENU_SETTINGS, R.drawable.msg_settings, TjLocale.getString(R.string.TjWatchSettings));

        root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, LayoutHelper.createFrame(-1, -1));

        content.addView(searchField(context), LayoutHelper.createLinear(-1, 44, 12, 10, 12, 0));

        content.addView(genreSection(context), LayoutHelper.createLinear(-1, -2));

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
        genreRow.addView(chip(context, TjLocale.getString(R.string.TjWatchAll), null),
                LayoutHelper.createLinear(-2, 32, 0, 0, 6, 0));
        for (Genre genre : genres) {
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
            if (search != null && search.getText().length() > 0) search.setText("");
            // Clearing the box queues its own reload; this one is immediate and says the same thing.
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
     * What was left part-way through, in front of everything else. It is the one row on this screen
     * that is about this device rather than the catalogue, so it only stands while nothing is being
     * searched for - a search is a question about the catalogue.
     */
    private View continueSection(Context context) {
        continueSection = new LinearLayout(context);
        continueSection.setOrientation(LinearLayout.VERTICAL);
        continueSection.setVisibility(View.GONE);

        TextView header = new TextView(context);
        header.setTextSize(14);
        header.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setText(TjLocale.getString(R.string.TjWatchContinue));
        header.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        continueSection.addView(header, LayoutHelper.createLinear(-1, -2, 18, 16, 18, 0));

        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(dp(12), 0, dp(12), 0);
        continueRow = new LinearLayout(context);
        continueRow.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(continueRow, new FrameLayout.LayoutParams(-2, -2));
        continueSection.addView(scroll, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 4));
        return continueSection;
    }

    private void refreshContinue() {
        if (continueRow == null) return;
        ArrayList<TjWatchHistory.Entry> entries = query.trim().isEmpty() && gate == null && selectedGenre == null
                ? TjWatchHistory.unfinished() : new ArrayList<>();
        continueRow.removeAllViews();
        continueSection.setVisibility(entries.isEmpty() ? View.GONE : View.VISIBLE);
        Context context = continueRow.getContext();
        for (TjWatchHistory.Entry entry : entries) {
            ResumeCell cell = new ResumeCell(context);
            cell.bind(entry);
            cell.setOnClickListener(v -> resume(entry));
            cell.setOnLongClickListener(v -> { askToForget(entry); return true; });
            continueRow.addView(cell, LayoutHelper.createLinear(104, -2, 0, 0, 10, 0));
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
        if (search != null) search.setEnabled(false);
    }

    private void askForKey() {
        TjTmdbKeyDialog.show(this, () -> {
            if (getParentActivity() == null) return;
            if (gate != null) { root.removeView(gate); gate = null; }
            listView.setVisibility(View.VISIBLE);
            status.setVisibility(View.VISIBLE);
            if (search != null) search.setEnabled(true);
            trending();
            loadGenres();
            refreshContinue();
        });
    }

    private View searchField(Context context) {
        FrameLayout field = new FrameLayout(context);
        field.setBackground(Theme.createRoundRectDrawable(dp(22), Theme.getColor(Theme.key_windowBackgroundWhite)));
        search = new EditTextBoldCursor(context);
        search.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 16);
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setBackground(null);
        search.setSingleLine(true);
        search.setHint(TjLocale.getString(R.string.TjWatchSearchHint));
        search.setCursorSize(dp(20));
        search.setCursorWidth(1.5f);
        search.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                query = s.toString();
                refreshContinue();
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                AndroidUtilities.runOnUIThread(searchRunnable, 400);
            }
        });
        field.addView(search, LayoutHelper.createFrame(-1, -1, Gravity.CENTER, 44, 0, 16, 0));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_search);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteHintText), PorterDuff.Mode.SRC_IN));
        field.addView(icon, LayoutHelper.createFrame(44, 44,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL));
        return field;
    }

    private void trending() {
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        pending = 2;
        series.trending(currentAccount, true, (body, error) -> collect(body, true, error));
        movies.trending(currentAccount, false, (body, error) -> collect(body, false, error));
    }

    private void discover(Genre genre) {
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        pending = (genre.seriesId > 0 ? 1 : 0) + (genre.movieId > 0 ? 1 : 0);
        if (genre.seriesId > 0) series.discover(currentAccount, true, genre.seriesId, (body, error) -> collect(body, true, error));
        if (genre.movieId > 0) movies.discover(currentAccount, false, genre.movieId, (body, error) -> collect(body, false, error));
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
        items.clear();
        adapter.notifyDataSetChanged();
        status.setText(TjLocale.getString(R.string.TjMediaLoading));
        status.setVisibility(View.VISIBLE);
        pending = 2;
        series.search(currentAccount, name, true, (body, error) -> collect(body, true, error));
        movies.search(currentAccount, name, false, (body, error) -> collect(body, false, error));
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
        if (--pending > 0) return;
        // Best known first, so the thing being looked for is rarely below the fold.
        items.sort((a, b) -> Double.compare(b.rating, a.rating));
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
     * One thing left part-way through: its artwork, a bar showing how far in, and - for a series -
     * which episode it was. Narrower than a search result, because a shelf is read across.
     */
    static class ResumeCell extends LinearLayout {
        private final BackupImageView image;
        private final TextView name, where;
        private final View track, bar;

        ResumeCell(Context context) {
            super(context);
            setOrientation(VERTICAL);
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

            ImageView play = new ImageView(context);
            play.setImageResource(R.drawable.msg_played);
            play.setScaleType(ImageView.ScaleType.CENTER);
            play.setColorFilter(new PorterDuffColorFilter(0xffffffff, PorterDuff.Mode.SRC_IN));
            play.setBackground(Theme.createRoundRectDrawable(dp(16), 0x66000000));
            art.addView(play, LayoutHelper.createFrame(32, 32, Gravity.CENTER));

            track = new View(context);
            track.setBackgroundColor(0x55000000);
            art.addView(track, LayoutHelper.createFrame(-1, 3, Gravity.BOTTOM));
            bar = new View(context);
            bar.setBackgroundColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            art.addView(bar, LayoutHelper.createFrame(0, 3, Gravity.BOTTOM | Gravity.LEFT));

            name = new TextView(context);
            name.setTextSize(12);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            name.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(name, LayoutHelper.createLinear(-1, -2, 0, 6, 0, 0));

            where = new TextView(context);
            where.setTextSize(11);
            where.setSingleLine(true);
            where.setEllipsize(android.text.TextUtils.TruncateAt.END);
            where.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            where.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            addView(where, LayoutHelper.createLinear(-1, -2, 0, 1, 0, 6));

            ScaleStateListAnimator.apply(this, 0.03f, 1.2f);
        }

        void bind(TjWatchHistory.Entry entry) {
            String poster = TjTmdb.posterUrl(entry.poster);
            image.setImage(poster.isEmpty() ? null : poster, "320_480", (android.graphics.drawable.Drawable) null);
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
            } else {
                where.setVisibility(GONE);
            }
            final float progress = entry.progress();
            track.setVisibility(progress > 0 ? VISIBLE : GONE);
            bar.setVisibility(progress > 0 ? VISIBLE : GONE);
            // The artwork has no width until it is measured, so the bar is sized against it then.
            image.post(() -> {
                ViewGroup.LayoutParams params = bar.getLayoutParams();
                params.width = (int) (image.getMeasuredWidth() * progress);
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
