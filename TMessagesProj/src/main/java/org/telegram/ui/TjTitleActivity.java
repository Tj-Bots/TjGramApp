package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.tj.TjMediaKind;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.messenger.tj.TjTmdb;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * One title, as the catalogue describes it: the artwork, what it is about, how it was received,
 * and - for a series - its seasons and the episodes in them. Watching is the last step, and only
 * then does anything look for a file among the chats this device has indexed.
 */
public class TjTitleActivity extends BaseFragment {

    private static final class Episode {
        final int number;
        final String name, overview, still;
        Episode(JSONObject object) {
            number = object.optInt("episode_number", -1);
            name = object.optString("name", "");
            overview = object.optString("overview", "");
            still = object.optString("still_path", "");
        }
    }

    private final long id;
    private final boolean series;
    private final String name;
    /** What the catalogue also calls it, and when it came out - both decide which file is right. */
    private String originalName = "";
    private String posterPath = "";
    private int year;
    /** Which episode the current search is for, so what gets played can be remembered as that. */
    private int pendingSeason = -1, pendingEpisode = -1;
    private final TjTmdb details = new TjTmdb();
    private final TjTmdb seasonClient = new TjTmdb();
    private final ArrayList<Integer> seasons = new ArrayList<>();
    private final ArrayList<Episode> episodes = new ArrayList<>();
    private final ArrayList<Integer> accounts = new ArrayList<>();

    private LinearLayout body;
    private LinearLayout seasonRow;
    private LinearLayout episodeList;
    private TextView overviewView;
    private TextView metaView;
    private TextView genresView;
    private BackupImageView backdrop;
    private BackupImageView poster;
    private int selectedSeason = -1;

    public TjTitleActivity(long id, boolean series, String name) {
        this.id = id;
        this.series = series;
        this.name = name;
    }

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
        actionBar.setTitle(name);
        actionBar.setCastShadows(false);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = scroll;

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));

        body.addView(hero(context), LayoutHelper.createLinear(-1, -2));

        metaView = text(context, 13, Theme.key_windowBackgroundWhiteGrayText2);
        body.addView(metaView, LayoutHelper.createLinear(-1, -2, 16, 12, 16, 0));

        genresView = text(context, 13, Theme.key_windowBackgroundWhiteBlueHeader);
        body.addView(genresView, LayoutHelper.createLinear(-1, -2, 16, 6, 16, 0));

        overviewView = text(context, 15, Theme.key_windowBackgroundWhiteBlackText);
        overviewView.setLineSpacing(dp(2), 1f);
        body.addView(overviewView, LayoutHelper.createLinear(-1, -2, 16, 12, 16, 4));

        if (series) {
            seasonRow = new LinearLayout(context);
            seasonRow.setOrientation(LinearLayout.HORIZONTAL);
            android.widget.HorizontalScrollView seasonScroll = new android.widget.HorizontalScrollView(context);
            seasonScroll.setHorizontalScrollBarEnabled(false);
            seasonScroll.addView(seasonRow, new FrameLayout.LayoutParams(-2, -2));
            body.addView(seasonScroll, LayoutHelper.createLinear(-1, -2, 10, 14, 10, 4));
        }

        episodeList = new LinearLayout(context);
        episodeList.setOrientation(LinearLayout.VERTICAL);
        body.addView(episodeList, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 24));

        loadDetails();
        return fragmentView;
    }

    private View hero(Context context) {
        FrameLayout hero = new FrameLayout(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private int gradientHeight;

            @Override protected void dispatchDraw(Canvas canvas) {
                super.dispatchDraw(canvas);
                if (gradientHeight != getHeight()) {
                    gradientHeight = getHeight();
                    // Artwork has to give way to text at the bottom, or the name lands on whatever
                    // the frame happened to contain.
                    paint.setShader(new LinearGradient(0, getHeight() * 0.45f, 0, getHeight(),
                            0x00000000, Theme.getColor(Theme.key_windowBackgroundGray), Shader.TileMode.CLAMP));
                }
                canvas.drawRect(0, getHeight() * 0.45f, getWidth(), getHeight(), paint);
            }
        };
        hero.setWillNotDraw(false);
        backdrop = new BackupImageView(context);
        hero.addView(backdrop, LayoutHelper.createFrame(-1, 210));

        poster = new BackupImageView(context);
        poster.setRoundRadius(dp(8));
        hero.addView(poster, LayoutHelper.createFrame(92, 138,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 16, 0, 16, 0));

        TextView titleView = new TextView(context);
        titleView.setTextSize(21);
        titleView.setMaxLines(3);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setText(name);
        titleView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        hero.addView(titleView, LayoutHelper.createFrame(-1, -2,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM,
                LocaleController.isRTL ? 16 : 120, 0, LocaleController.isRTL ? 120 : 16, 10));

        hero.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(250)));
        return hero;
    }

    private TextView text(Context context, int size, int colorKey) {
        TextView view = new TextView(context);
        view.setTextSize(size);
        view.setTextColor(Theme.getColor(colorKey));
        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        return view;
    }

    private void loadDetails() {
        details.details(currentAccount, id, series, (body, error) -> {
            if (getParentActivity() == null) return;
            if (body == null) {
                overviewView.setText(TjLocale.getString(error == TjTmdb.CREDENTIAL
                        ? R.string.TjWatchNeedsKey : R.string.TjWatchOffline));
                return;
            }
            String backdropPath = body.optString("backdrop_path", "");
            String posterPath = body.optString("poster_path", "");
            this.posterPath = posterPath;
            String backdropUrl = TjTmdb.backdropUrl(backdropPath);
            if (backdropUrl.isEmpty()) backdropUrl = TjTmdb.backdropUrl(posterPath);
            if (!backdropUrl.isEmpty()) backdrop.setImage(backdropUrl, "800_450", (android.graphics.drawable.Drawable) null);
            String posterUrl = TjTmdb.posterUrl(posterPath);
            if (!posterUrl.isEmpty()) poster.setImage(posterUrl, "320_480", (android.graphics.drawable.Drawable) null);

            ArrayList<String> meta = new ArrayList<>();
            String date = body.optString(series ? "first_air_date" : "release_date", "");
            if (date.length() >= 4) {
                meta.add(date.substring(0, 4));
                try { year = Integer.parseInt(date.substring(0, 4)); } catch (NumberFormatException ignore) { }
            }
            originalName = body.optString(series ? "original_name" : "original_title", "");
            double rating = body.optDouble("vote_average", 0);
            if (rating > 0) meta.add(String.format(Locale.US, "★ %.1f", rating));
            if (series) {
                int count = body.optInt("number_of_seasons", 0);
                if (count > 0) meta.add(LocaleController.formatPluralString("Seasons", count));
            } else {
                int runtime = body.optInt("runtime", 0);
                if (runtime > 0) meta.add(runtime + " " + TjLocale.getString(R.string.TjWatchMinutes));
            }
            metaView.setText(TextUtils.join("   ·   ", meta));

            JSONArray genres = body.optJSONArray("genres");
            ArrayList<String> names = new ArrayList<>();
            for (int i = 0; genres != null && i < genres.length(); i++) {
                JSONObject genre = genres.optJSONObject(i);
                if (genre != null && !genre.optString("name", "").isEmpty()) names.add(genre.optString("name"));
            }
            genresView.setText(TextUtils.join("  ·  ", names));
            genresView.setVisibility(names.isEmpty() ? View.GONE : View.VISIBLE);

            String overview = body.optString("overview", "");
            overviewView.setText(overview);
            overviewView.setVisibility(overview.isEmpty() ? View.GONE : View.VISIBLE);

            if (series) {
                seasons.clear();
                JSONArray list = body.optJSONArray("seasons");
                for (int i = 0; list != null && i < list.length(); i++) {
                    JSONObject season = list.optJSONObject(i);
                    if (season == null) continue;
                    int number = season.optInt("season_number", -1);
                    if (number >= 0 && season.optInt("episode_count", 0) > 0) seasons.add(number);
                }
                buildSeasonChips();
                if (!seasons.isEmpty()) selectSeason(seasons.contains(1) ? 1 : seasons.get(0));
            } else {
                buildMovieAction();
            }
        });
    }

    private void buildSeasonChips() {
        if (seasonRow == null) return;
        seasonRow.removeAllViews();
        ArrayList<View> chips = new ArrayList<>();
        for (int season : seasons) {
            TextView chip = new TextView(seasonRow.getContext());
            chip.setTextSize(14);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), 0, dp(14), 0);
            chip.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            chip.setText(season == 0 ? TjLocale.getString(R.string.TjWatchSpecials)
                    : TjLocale.getString(R.string.TjMediaSeason) + " " + season);
            chip.setTag(season);
            chip.setOnClickListener(v -> selectSeason((Integer) v.getTag()));
            ScaleStateListAnimator.apply(chip, 0.04f, 1.2f);
            chips.add(chip);
        }
        // The app declares no right-to-left support and mirrors by hand, so season one is added
        // last in a language read from the right, and the row starts at that end.
        if (LocaleController.isRTL) java.util.Collections.reverse(chips);
        for (View chip : chips) seasonRow.addView(chip, LayoutHelper.createLinear(-2, 34, 6, 0, 0, 0));
        paintSeasonChips();
        if (LocaleController.isRTL && seasonRow.getParent() instanceof android.widget.HorizontalScrollView) {
            View parent = (View) seasonRow.getParent();
            // scrollTo rather than fullScroll: the latter also hands focus to a chip.
            parent.post(() -> parent.scrollTo(seasonRow.getWidth(), 0));
        }
    }

    private void paintSeasonChips() {
        if (seasonRow == null) return;
        for (int i = 0; i < seasonRow.getChildCount(); i++) {
            View child = seasonRow.getChildAt(i);
            boolean active = child.getTag() != null && (Integer) child.getTag() == selectedSeason;
            child.setBackground(Theme.createRoundRectDrawable(dp(17), active
                    ? Theme.getColor(Theme.key_featuredStickers_addButton)
                    : Theme.getColor(Theme.key_windowBackgroundWhite)));
            ((TextView) child).setTextColor(Theme.getColor(active
                    ? Theme.key_featuredStickers_buttonText : Theme.key_windowBackgroundWhiteBlackText));
        }
    }

    private void selectSeason(int season) {
        selectedSeason = season;
        paintSeasonChips();
        episodes.clear();
        episodeList.removeAllViews();
        TextView loading = text(episodeList.getContext(), 14, Theme.key_windowBackgroundWhiteGrayText);
        loading.setText(TjLocale.getString(R.string.TjMediaLoading));
        loading.setPadding(dp(16), dp(16), dp(16), dp(16));
        episodeList.addView(loading);
        seasonClient.season(currentAccount, id, season, (body, error) -> {
            if (getParentActivity() == null || selectedSeason != season) return;
            episodeList.removeAllViews();
            JSONArray list = body == null ? null : body.optJSONArray("episodes");
            for (int i = 0; list != null && i < list.length(); i++) {
                JSONObject object = list.optJSONObject(i);
                if (object == null) continue;
                Episode episode = new Episode(object);
                if (episode.number >= 0) episodes.add(episode);
            }
            if (episodes.isEmpty()) {
                TextView empty = text(episodeList.getContext(), 14, Theme.key_windowBackgroundWhiteGrayText);
                empty.setText(TjLocale.getString(R.string.TjWatchNoEpisodes));
                empty.setPadding(dp(16), dp(16), dp(16), dp(16));
                episodeList.addView(empty);
                return;
            }
            for (Episode episode : episodes) episodeList.addView(episodeRow(episode));
        });
    }

    /**
     * "Season 1 Episode 3", and the episode's own name after it when it has one. TMDB fills a
     * missing translation with "Episode 3" in the viewer's language, which is no name at all - that
     * is what used to read as "3. Episode 3".
     */
    private static String episodeLabel(int season, Episode episode) {
        StringBuilder label = new StringBuilder();
        if (season > 0) {
            label.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(season).append(' ');
        }
        label.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(episode.number);
        final String name = episode.name.trim();
        final boolean placeholder = name.isEmpty()
                || name.contains(String.valueOf(episode.number)) && name.replaceAll("[\\d\\s.:#\\-]", "").length() <= 10;
        if (!placeholder) {
            label.append(" · ").append(name);
        }
        return label.toString();
    }

    private View episodeRow(Episode episode) {
        Context context = episodeList.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> watch(selectedSeason, episode.number));
        ScaleStateListAnimator.apply(row, 0.02f, 1.2f);

        BackupImageView still = new BackupImageView(context);
        still.setRoundRadius(dp(6));
        String stillUrl = TjTmdb.stillUrl(episode.still);
        if (!stillUrl.isEmpty()) still.setImage(stillUrl, "300_170", (android.graphics.drawable.Drawable) null);
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(context, 15, Theme.key_windowBackgroundWhiteBlackText);
        title.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setText(episodeLabel(selectedSeason, episode));
        texts.addView(title, LayoutHelper.createLinear(-1, -2));
        if (!episode.overview.isEmpty()) {
            TextView overview = text(context, 12, Theme.key_windowBackgroundWhiteGrayText2);
            overview.setMaxLines(2);
            overview.setEllipsize(TextUtils.TruncateAt.END);
            overview.setText(episode.overview);
            texts.addView(overview, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        }
        android.widget.ImageView play = new android.widget.ImageView(context);
        play.setImageResource(R.drawable.msg_played);
        play.setScaleType(android.widget.ImageView.ScaleType.CENTER);
        play.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader), android.graphics.PorterDuff.Mode.SRC_IN));

        // The picture starts the line and the play mark ends it, whichever way the line runs.
        if (LocaleController.isRTL) {
            row.addView(play, LayoutHelper.createLinear(32, 32));
            row.addView(texts, LayoutHelper.createLinear(0, -2, 1f, 8, 0, 12, 0));
            row.addView(still, LayoutHelper.createLinear(104, 59));
        } else {
            row.addView(still, LayoutHelper.createLinear(104, 59));
            row.addView(texts, LayoutHelper.createLinear(0, -2, 1f, 12, 0, 8, 0));
            row.addView(play, LayoutHelper.createLinear(32, 32));
        }
        return row;
    }

    private void buildMovieAction() {
        episodeList.removeAllViews();
        Context context = episodeList.getContext();
        TextView watch = new TextView(context);
        watch.setTextSize(16);
        watch.setGravity(Gravity.CENTER);
        watch.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        watch.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        watch.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(24),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        watch.setText(TjLocale.getString(R.string.TjWatchOpen));
        watch.setOnClickListener(v -> watch(-1, -1));
        ScaleStateListAnimator.apply(watch, 0.03f, 1.2f);
        episodeList.addView(watch, LayoutHelper.createLinear(-1, 48, 16, 8, 16, 8));
    }

    /** The names this title is known by. Files are named in either, so both are searched for. */
    private ArrayList<String> targets() {
        ArrayList<String> targets = new ArrayList<>();
        targets.add(name);
        if (!originalName.isEmpty() && !originalName.equalsIgnoreCase(name)) targets.add(originalName);
        return targets;
    }

    /** One copy of the thing, from wherever it was found. */
    private static final class Candidate {
        final org.telegram.messenger.MessageObject message;
        final long position;
        final int score;
        Candidate(org.telegram.messenger.MessageObject message, long position, int score) {
            this.message = message;
            this.position = position;
            this.score = score;
        }
        String identity() { return org.telegram.messenger.tj.TjWatchSearch.identity(message); }
        long size() { return message.getDocument() == null ? 0 : message.getDocument().size; }
    }

    /**
     * The one step that touches the chats. Two places are asked: what this device has already
     * indexed, which is quick and remembers where playback stopped, and Telegram itself - the same
     * search a person would type the name into - because the index only ever holds the chats that
     * happen to have been scanned, which is never all of them.
     */
    private void watch(int season, int episode) {
        if (getParentActivity() == null) return;
        pendingSeason = season;
        pendingEpisode = episode;
        final ArrayList<Candidate> found = new ArrayList<>();
        final HashSet<String> seen = new HashSet<>();
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.show();
        final int[] pending = {accounts.size() + 1};
        final Runnable done = () -> {
            if (--pending[0] > 0) return;
            try { progress.dismiss(); } catch (Exception ignore) { }
            offer(found);
        };
        final ArrayList<String> targets = targets();
        for (int account : accounts) {
            TjMediaStore.getInstance().load(account, 0, name, "", null, new HashSet<>(), 0, 0, page -> {
                if (page != null) {
                    for (TjMediaStore.Record record : page) {
                        if (TjMediaKind.of(record.message) != TjMediaKind.VIDEO) continue;
                        int score = org.telegram.messenger.tj.TjTitleMatch.score(
                                record.message.getDocumentName(),
                                record.message.messageOwner == null ? "" : record.message.messageOwner.message,
                                targets, year, season, episode);
                        if (score == org.telegram.messenger.tj.TjTitleMatch.REJECT) continue;
                        Candidate candidate = new Candidate(record.message, record.position, score);
                        if (seen.add(candidate.identity())) found.add(candidate);
                    }
                }
                done.run();
            });
        }
        org.telegram.messenger.tj.TjWatchSearch.search(accounts, targets, year, season, episode, results -> {
            for (org.telegram.messenger.tj.TjWatchSearch.Result result : results) {
                Candidate candidate = new Candidate(result.message, 0, result.score);
                if (seen.add(candidate.identity())) found.add(candidate);
            }
            done.run();
        });
    }

    private void offer(ArrayList<Candidate> found) {
        if (getParentActivity() == null) return;
        if (found.isEmpty()) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(name)
                    .setMessage(TjLocale.getString(R.string.TjWatchNoFile))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            return;
        }
        // Closest match first, then the better picture, then the larger file of that picture.
        found.sort((a, b) -> {
            if (a.score != b.score) return Integer.compare(b.score, a.score);
            int quality = Integer.compare(qualityRank(b), qualityRank(a));
            return quality != 0 ? quality : Long.compare(b.size(), a.size());
        });
        if (found.size() == 1) { play(found.get(0)); return; }

        Context context = getParentActivity();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialog = new AlertDialog[1];
        for (int a = 0; a < found.size(); a++) {
            if (a > 0) list.addView(separator(context));
            list.addView(copyRow(context, found.get(a), dialog));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        dialog[0] = new AlertDialog.Builder(context)
                .setTitle(TjLocale.getString(R.string.TjWatchChooseFile))
                .setView(scroll)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create();
        showDialog(dialog[0]);
    }

    /** A hairline between two copies, so three lines of one do not read as part of the next. */
    private View separator(Context context) {
        View line = new View(context);
        line.setBackgroundColor(Theme.getColor(Theme.key_divider));
        LinearLayout.LayoutParams params = LayoutHelper.createLinear(-1, 1);
        params.leftMargin = dp(20);
        params.rightMargin = dp(20);
        line.setLayoutParams(params);
        return line;
    }

    /**
     * One copy, laid out so the same fact is always in the same place: what the picture is and how
     * big it is on the first line, the name the file gives itself on the second, where it came
     * from on the third. Put on one line they run into each other, and in a language read the
     * other way round they run into each other backwards.
     */
    private View copyRow(Context context, Candidate candidate, AlertDialog[] dialog) {
        String caption = candidate.message.messageOwner == null ? "" : candidate.message.messageOwner.message;
        String quality = org.telegram.messenger.tj.TjMediaTitle.parse(
                candidate.message.getDocumentName(), caption).quality;
        String described = org.telegram.messenger.tj.TjTitleMatch.describe(
                candidate.message.getDocumentName(), caption);
        String source = sourceName(candidate.message);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> {
            if (dialog[0] != null) dialog[0].dismiss();
            play(candidate);
        });

        LinearLayout head = new LinearLayout(context);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (!quality.isEmpty()) {
            TextView badge = new TextView(context);
            badge.setText(quality);
            badge.setTextSize(12);
            badge.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
            badge.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
            badge.setPadding(dp(7), dp(2), dp(7), dp(3));
            badge.setBackground(Theme.createRoundRectDrawable(dp(5),
                    Theme.getColor(Theme.key_featuredStickers_addButton)));
            head.addView(badge, LayoutHelper.createLinear(-2, -2, 0, 0, 8, 0));
        }
        TextView size = new TextView(context);
        size.setText(AndroidUtilities.formatFileSize(candidate.size()));
        size.setTextSize(15);
        size.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        size.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        head.addView(size, LayoutHelper.createLinear(-2, -2));
        row.addView(head, LayoutHelper.createLinear(-1, -2));

        TextView title = new TextView(context);
        title.setText(described);
        title.setTextSize(14);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        row.addView(title, LayoutHelper.createLinear(-1, -2, 0, 3, 0, 0));

        if (!source.isEmpty()) {
            TextView from = new TextView(context);
            from.setText(source);
            from.setTextSize(12);
            from.setSingleLine(true);
            from.setEllipsize(TextUtils.TruncateAt.END);
            from.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            from.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            row.addView(from, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        }
        return row;
    }

    /** Bigger picture first, and an unlabelled copy last because nobody knows what it is. */
    private static int qualityRank(Candidate candidate) {
        String quality = org.telegram.messenger.tj.TjMediaTitle.parse(
                candidate.message.getDocumentName(),
                candidate.message.messageOwner == null ? "" : candidate.message.messageOwner.message).quality;
        switch (quality.toUpperCase(Locale.ROOT)) {
            case "2160P": case "4K": return 4;
            case "1080P": return 3;
            case "720P": return 2;
            case "480P": return 1;
            default: return 0;
        }
    }

    /** Which chat a copy came from, because that is how people tell two copies apart. */
    private static String sourceName(org.telegram.messenger.MessageObject message) {
        long dialogId = message.getDialogId();
        org.telegram.messenger.MessagesController controller =
                org.telegram.messenger.MessagesController.getInstance(message.currentAccount);
        if (dialogId < 0) {
            org.telegram.tgnet.TLRPC.Chat chat = controller.getChat(-dialogId);
            return chat == null ? "" : chat.title;
        }
        org.telegram.tgnet.TLRPC.User user = controller.getUser(dialogId);
        return user == null ? "" : org.telegram.messenger.UserObject.getUserName(user);
    }

    /**
     * Opens the copy, and writes down that it was opened. The position comes from whichever of the
     * two records is further in: the library knows files it indexed, the watch history knows the
     * ones that were only ever found by searching.
     */
    private void play(Candidate candidate) {
        int account = candidate.message.currentAccount;
        long document = candidate.message.getDocument() == null ? 0 : candidate.message.getDocument().id;
        long position = Math.max(candidate.position,
                org.telegram.messenger.tj.TjWatchHistory.positionFor(account, document));
        org.telegram.messenger.tj.TjWatchHistory.Entry entry = org.telegram.messenger.tj.TjWatchHistory.entryFor(
                id, series, name, posterPath, year, pendingSeason, pendingEpisode, candidate.message);
        if (entry != null) {
            entry.position = position;
            org.telegram.messenger.tj.TjWatchHistory.remember(entry);
        }
        org.telegram.ui.Components.TjMediaPlayback.open(this,
                new TjMediaLibrary.Entry(account, UserConfig.getInstance(account).getClientUserId(), candidate.message),
                position);
    }
}
