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
import org.telegram.tgnet.TLRPC;
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

        /** An episode known only from a file in the chats, with nothing but its number. */
        Episode(int number) {
            this.number = number;
            name = "";
            overview = "";
            still = "";
        }
    }

    private final long id;
    private final boolean series;
    private final String name;
    /** What the catalogue also calls it, and when it came out - both decide which file is right. */
    private String originalName = "";
    private String posterPath = "";
    private String releaseDate = "";
    private double rating;
    private android.widget.ImageView favoriteIcon;
    private TextView favoriteLabel;
    private int year;
    /** Which episode the current search is for, so what gets played can be remembered as that. */
    private int pendingSeason = -1, pendingEpisode = -1;
    private final TjTmdb details = new TjTmdb();
    private final TjTmdb seasonClient = new TjTmdb();
    private final ArrayList<Integer> seasons = new ArrayList<>();
    /** Seasons found only in the chats, ahead of the catalogue, with the episode numbers seen. */
    private final java.util.TreeMap<Integer, java.util.TreeSet<Integer>> chatSeasons = new java.util.TreeMap<>();
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

        // The row under the summary a streaming app has: an icon over a word for each action,
        // "I liked this one" first - it keeps the title in a row of its own on the Watch screen.
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        View favorite = actionButton(context, R.drawable.media_like, TjLocale.getString(R.string.TjWatchFavoriteShort), v -> toggleFavorite());
        favoriteIcon = (android.widget.ImageView) ((LinearLayout) favorite).getChildAt(0);
        favoriteLabel = (TextView) ((LinearLayout) favorite).getChildAt(1);
        actions.addView(favorite, LayoutHelper.createLinear(84, -2));
        // What people wrote about it, in a sheet of its own; the count arrives with the reviews.
        reviewsButton = actionButton(context, R.drawable.msg_discussion, TjLocale.getString(R.string.TjWatchReviews), v -> showReviews());
        reviewsButton.setVisibility(View.GONE);
        actions.addView(reviewsButton, LayoutHelper.createLinear(84, -2));
        // The trailer, when the catalogue has one on YouTube - played in the app's own YouTube sheet.
        trailerButton = actionButton(context, R.drawable.msg_played, TjLocale.getString(R.string.TjWatchTrailer), v -> playTrailer());
        trailerButton.setVisibility(View.GONE);
        actions.addView(trailerButton, LayoutHelper.createLinear(84, -2));
        actions.addView(actionButton(context, R.drawable.msg_share, LocaleController.getString(R.string.ShareFile), v -> shareTitle()),
                LayoutHelper.createLinear(84, -2));
        body.addView(actions, LayoutHelper.createLinear(-1, -2, 6, 10, 6, 2));
        updateFavoriteIcon(false);

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
        loadReviews();
        loadTrailer();
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

    private void toggleFavorite() {
        boolean now = org.telegram.messenger.tj.TjWatchFavorites.toggle(TjTitleActivity.this.id, series, name, posterPath, releaseDate, rating);
        updateFavoriteIcon(true);
        org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(
                now ? R.raw.contact_check : R.raw.ic_delete,
                TjLocale.getString(now ? R.string.TjWatchFavoriteAdded : R.string.TjWatchFavoriteRemoved)).show();
    }

    private View actionButton(Context context, int icon, String label, View.OnClickListener onClick) {
        LinearLayout button = new LinearLayout(context);
        button.setOrientation(LinearLayout.VERTICAL);
        button.setGravity(Gravity.CENTER_HORIZONTAL);
        button.setPadding(0, dp(8), 0, dp(6));
        button.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(12), 0, Theme.getColor(Theme.key_listSelector)));
        android.widget.ImageView image = new android.widget.ImageView(context);
        image.setImageResource(icon);
        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        image.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), android.graphics.PorterDuff.Mode.SRC_IN));
        button.addView(image, LayoutHelper.createLinear(26, 26));
        TextView text = new TextView(context);
        text.setTextSize(12);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        text.setText(label);
        button.addView(text, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 4, 5, 4, 0));
        button.setOnClickListener(onClick);
        org.telegram.ui.Components.ScaleStateListAnimator.apply(button, 0.06f, 1.4f);
        return button;
    }

    private void shareTitle() {
        if (getParentActivity() == null) return;
        String link = "https://www.themoviedb.org/" + (series ? "tv/" : "movie/") + id;
        String text = name + (year > 0 ? " (" + year + ")" : "") + "\n" + link;
        showDialog(org.telegram.ui.Components.ShareAlert.createShareAlert(getParentActivity(), null, text, false, link, false));
    }

    private void updateFavoriteIcon(boolean animated) {
        if (favoriteIcon == null) return;
        boolean favorite = org.telegram.messenger.tj.TjWatchFavorites.contains(id, series);
        android.widget.ImageView icon = favoriteIcon;
        icon.setImageResource(favorite ? R.drawable.media_like_active : R.drawable.media_like);
        ((View) icon.getParent()).setContentDescription(TjLocale.getString(favorite ? R.string.TjWatchFavoriteRemove : R.string.TjWatchFavoriteAdd));
        favoriteLabel.setTextColor(favorite ? 0xFFFF4D5E : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        if (icon != null) {
            if (favorite) {
                icon.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFF4D5E, android.graphics.PorterDuff.Mode.SRC_IN));
            } else {
                icon.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), android.graphics.PorterDuff.Mode.SRC_IN));
            }
            if (animated && favorite) {
                icon.setScaleX(0.6f);
                icon.setScaleY(0.6f);
                icon.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(3f)).start();
            }
        }
    }

    private View reviewsButton, trailerButton;
    private JSONArray reviews;
    private int reviewsTotal;
    private String trailerKey, trailerName;
    private final TjTmdb reviewsClient = new TjTmdb();
    private final TjTmdb videosClient = new TjTmdb();

    private void loadReviews() {
        reviewsClient.reviews(currentAccount, id, series, (body, error) -> {
            if (getParentActivity() == null || reviewsButton == null || body == null) return;
            JSONArray results = body.optJSONArray("results");
            if (results == null || results.length() == 0) return;
            reviews = results;
            reviewsTotal = body.optInt("total_results", results.length());
            ((TextView) ((LinearLayout) reviewsButton).getChildAt(1)).setText(
                    TjLocale.getString(R.string.TjWatchReviews) + " · " + reviewsTotal);
            reviewsButton.setVisibility(View.VISIBLE);
        });
    }

    private void showReviews() {
        if (getParentActivity() == null || reviews == null) return;
        Context context = getParentActivity();
        org.telegram.ui.ActionBar.BottomSheet sheet = new org.telegram.ui.ActionBar.BottomSheet(context, false);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(8), 0, dp(16));
        TextView header = text(context, 18, Theme.key_dialogTextBlack);
        header.setTypeface(AndroidUtilities.bold());
        header.setText(TjLocale.getString(R.string.TjWatchReviews) + "  ·  " + reviewsTotal);
        list.addView(header, LayoutHelper.createLinear(-1, -2, 18, 6, 18, 10));
        for (int i = 0; i < reviews.length(); i++) {
            JSONObject review = reviews.optJSONObject(i);
            if (review == null) continue;
            View card = reviewCard(context, review);
            if (card != null) list.addView(card, LayoutHelper.createLinear(-1, -2, 12, 4, 12, 4));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        sheet.setCustomView(scroll);
        showDialog(sheet);
    }

    private void loadTrailer() {
        videosClient.videos(currentAccount, id, series, (body, error) -> {
            if (getParentActivity() == null || trailerButton == null || body == null) return;
            JSONArray results = body.optJSONArray("results");
            String local = TjTmdb.localLanguage();
            int best = -1;
            for (int i = 0; results != null && i < results.length(); i++) {
                JSONObject video = results.optJSONObject(i);
                if (video == null || !"YouTube".equalsIgnoreCase(video.optString("site"))) continue;
                String key = video.optString("key", "");
                if (key.isEmpty()) continue;
                // A trailer beats a teaser, one in the viewer's language beats English, official first.
                String type = video.optString("type", "");
                int score = ("Trailer".equals(type) ? 4 : "Teaser".equals(type) ? 2 : 0)
                        + (local != null && local.equals(video.optString("iso_639_1")) ? 8 : 0)
                        + (video.optBoolean("official") ? 1 : 0);
                if (score > best) {
                    best = score;
                    trailerKey = key;
                    trailerName = video.optString("name", name);
                }
            }
            if (trailerKey != null) trailerButton.setVisibility(View.VISIBLE);
        });
    }

    private void playTrailer() {
        if (trailerKey == null || getParentActivity() == null) return;
        String watch = "https://www.youtube.com/watch?v=" + trailerKey;
        org.telegram.ui.Components.EmbedBottomSheet.show(this, null, null, "YouTube",
                trailerName != null ? trailerName : name, watch,
                "https://www.youtube.com/embed/" + trailerKey, 1280, 720, false);
    }

    /** One review: who wrote it, the score they gave, and the text - folded, with a translate button. */
    private View reviewCard(Context context, JSONObject review) {
        String content = cleanReview(review.optString("content", ""));
        if (content.isEmpty()) return null;
        JSONObject details = review.optJSONObject("author_details");
        String author = review.optString("author", "");
        if (details != null && !details.optString("name", "").isEmpty()) author = details.optString("name");
        double score = details == null || details.isNull("rating") ? 0 : details.optDouble("rating", 0);
        String date = review.optString("created_at", "");
        if (date.length() >= 10) date = date.substring(0, 10);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(10));
        card.setBackground(Theme.createRoundRectDrawable(dp(14), androidx.core.graphics.ColorUtils.blendARGB(
                Theme.getColor(Theme.key_dialogBackground), Theme.getColor(Theme.key_dialogTextBlack), 0.06f)));

        TextView who = text(context, 14, Theme.key_windowBackgroundWhiteBlackText);
        who.setTypeface(AndroidUtilities.bold());
        who.setText(author + (score > 0 ? String.format(Locale.US, "   ★ %.0f/10", score) : ""));
        card.addView(who, LayoutHelper.createLinear(-1, -2));
        if (!date.isEmpty()) {
            TextView when = text(context, 12, Theme.key_windowBackgroundWhiteGrayText);
            when.setText(date);
            card.addView(when, LayoutHelper.createLinear(-1, -2, 0, 1, 0, 0));
        }

        TextView body = text(context, 14, Theme.key_windowBackgroundWhiteBlackText);
        body.setLineSpacing(dp(1.5f), 1f);
        body.setText(content);
        body.setMaxLines(4);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setOnClickListener(v -> {
            boolean open = body.getMaxLines() != Integer.MAX_VALUE;
            body.setMaxLines(open ? Integer.MAX_VALUE : 4);
        });
        card.addView(body, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));

        TextView translate = text(context, 13, Theme.key_windowBackgroundWhiteBlueText4);
        translate.setTypeface(AndroidUtilities.bold());
        translate.setText(LocaleController.getString(R.string.TranslateMessage));
        translate.setPadding(0, dp(8), dp(8), dp(2));
        final String[] translated = {null};
        final boolean[] showingTranslation = {false};
        translate.setOnClickListener(v -> {
            if (translated[0] != null) {
                showingTranslation[0] = !showingTranslation[0];
                body.setText(showingTranslation[0] ? translated[0] : content);
                translate.setText(showingTranslation[0] ? TjLocale.getString(R.string.TjWatchShowOriginal)
                        : LocaleController.getString(R.string.TranslateMessage));
                return;
            }
            translate.setText(LocaleController.getString(R.string.Loading));
            translate.setEnabled(false);
            TLRPC.TL_messages_translateText req = new TLRPC.TL_messages_translateText();
            req.flags |= 2;
            TLRPC.TL_textWithEntities text = new TLRPC.TL_textWithEntities();
            text.text = content.length() > 3500 ? content.substring(0, 3500) : content;
            req.text.add(text);
            req.to_lang = org.telegram.ui.Components.TranslateAlert2.getToLanguage();
            getConnectionsManager().sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                translate.setEnabled(true);
                if (res instanceof TLRPC.TL_messages_translateResult
                        && !((TLRPC.TL_messages_translateResult) res).result.isEmpty()) {
                    translated[0] = ((TLRPC.TL_messages_translateResult) res).result.get(0).text;
                    showingTranslation[0] = true;
                    body.setText(translated[0]);
                    body.setMaxLines(Integer.MAX_VALUE);
                    translate.setText(TjLocale.getString(R.string.TjWatchShowOriginal));
                } else {
                    translate.setText(LocaleController.getString(R.string.TranslateMessage));
                    org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(
                            LocaleController.getString(R.string.ErrorOccurred)).show();
                }
            }));
        });
        card.addView(translate, LayoutHelper.createLinear(-2, -2, LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT, 0, 0, 0, 0));
        return card;
    }

    /** TMDB reviews come with Markdown and stray HTML; plain text reads better here. */
    private static String cleanReview(String text) {
        return text.replace("\r\n", "\n").replaceAll("<[^>]+>", "")
                .replaceAll("\\*\\*|__|(?m)^#+\\s*", "").replaceAll("\n{3,}", "\n\n").trim();
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
            releaseDate = date;
            if (date.length() >= 4) {
                meta.add(date.substring(0, 4));
                try { year = Integer.parseInt(date.substring(0, 4)); } catch (NumberFormatException ignore) { }
            }
            originalName = body.optString(series ? "original_name" : "original_title", "");
            double rating = body.optDouble("vote_average", 0);
            this.rating = rating;
            org.telegram.messenger.tj.TjWatchFavorites.refresh(id, series, posterPath, date, rating);
            int votes = body.optInt("vote_count", 0);
            if (rating > 0) meta.add(String.format(Locale.US, "★ %.1f", rating)
                    + (votes > 0 ? " (" + LocaleController.formatShortNumber(votes, null) + ")" : ""));
            if (series) {
                int count = body.optInt("number_of_seasons", 0);
                if (count > 0) meta.add(count == 1 ? TjLocale.getString(R.string.TjWatchOneSeason)
                        : TjLocale.formatString(R.string.TjWatchSeasonCount, count));
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
                probeChatSeasons();
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

    /**
     * The catalogue can be seasons behind what the chats already have. The next few season numbers
     * are asked for by name, and each one with files gets a chip of its own.
     */
    private void probeChatSeasons() {
        int last = 0;
        for (int season : seasons) last = Math.max(last, season);
        ArrayList<Integer> accounts = new ArrayList<>();
        for (int a = 0; a < org.telegram.messenger.UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (org.telegram.messenger.UserConfig.getInstance(a).isClientActivated()) accounts.add(a);
        }
        if (accounts.remove((Integer) currentAccount)) accounts.add(0, currentAccount);
        org.telegram.messenger.tj.TjWatchSearch.probeSeasons(accounts, targets(), 0, last + 1, 4, found -> {
            if (getParentActivity() == null || found.isEmpty()) return;
            chatSeasons.clear();
            chatSeasons.putAll(found);
            for (int season : found.keySet()) if (!seasons.contains(season)) seasons.add(season);
            java.util.Collections.sort(seasons);
            buildSeasonChips();
        });
    }

    private void selectSeason(int season) {
        selectedSeason = season;
        paintSeasonChips();
        episodes.clear();
        episodeList.removeAllViews();
        java.util.TreeSet<Integer> fromChats = chatSeasons.get(season);
        if (fromChats != null) {
            // Not in the catalogue yet: the episodes are the numbers the files carry.
            for (int number : fromChats) {
                Episode episode = new Episode(number);
                episodes.add(episode);
                episodeList.addView(episodeRow(episode));
            }
            return;
        }
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

    /** A grey track with the watched part in red, the way streaming apps mark an episode. */
    private static final class ProgressLine extends View {
        private final float progress;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint watched = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();

        ProgressLine(Context context, float progress) {
            super(context);
            this.progress = Math.max(0f, Math.min(1f, progress));
            track.setColor(0x99808080);
            watched.setColor(0xFFE50914);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = h / 2f;
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, track);
            // The line fills from where reading starts.
            if (LocaleController.isRTL) rect.set(w - w * progress, 0, w, h);
            else rect.set(0, 0, w * progress, h);
            canvas.drawRoundRect(rect, r, r, watched);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Back from the player: the lines under the episodes have moved on.
        if (episodeList != null && !episodes.isEmpty()) {
            episodeList.removeAllViews();
            for (Episode episode : episodes) episodeList.addView(episodeRow(episode));
        }
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

        // How far into it playback got, as a red line along the bottom of the picture.
        FrameLayout picture = new FrameLayout(context);
        picture.addView(still, LayoutHelper.createFrame(-1, -1));
        float progress = org.telegram.messenger.tj.TjWatchHistory.episodeProgress(id, series, selectedSeason, episode.number);
        if (progress > 0.005f) {
            picture.addView(new ProgressLine(context, progress), LayoutHelper.createFrame(-1, 4, Gravity.BOTTOM, 4, 0, 4, 3));
        }

        // The picture starts the line and the play mark ends it, whichever way the line runs.
        if (LocaleController.isRTL) {
            row.addView(play, LayoutHelper.createLinear(32, 32));
            row.addView(texts, LayoutHelper.createLinear(0, -2, 1f, 8, 0, 12, 0));
            row.addView(picture, LayoutHelper.createLinear(104, 59));
        } else {
            row.addView(picture, LayoutHelper.createLinear(104, 59));
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
        // Watched from here before: the same file again, from where it stopped. Another copy is
        // one tap away under Source in the player.
        org.telegram.messenger.MessageObject remembered =
                org.telegram.messenger.tj.TjWatchHistory.copyFor(id, series, season, episode);
        if (remembered != null && UserConfig.getInstance(remembered.currentAccount).isClientActivated()) {
            long position = org.telegram.messenger.tj.TjWatchHistory.episodePosition(id, series, season, episode);
            play(new org.telegram.messenger.tj.TjWatchFinder.Copy(remembered, position, 0), null);
            return;
        }
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.show();
        finder().find(season, episode, found -> {
            try { progress.dismiss(); } catch (Exception ignore) { }
            offer(found);
        });
    }

    private org.telegram.messenger.tj.TjWatchFinder finder() {
        return new org.telegram.messenger.tj.TjWatchFinder(name, targets(), year);
    }

    private void offer(ArrayList<org.telegram.messenger.tj.TjWatchFinder.Copy> found) {
        if (getParentActivity() == null) return;
        if (found.isEmpty()) {
            showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(name)
                    .setMessage(TjLocale.getString(R.string.TjWatchNoFile))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            return;
        }
        if (found.size() == 1) { play(found.get(0), found); return; }

        Context context = getParentActivity();
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        final AlertDialog[] dialog = new AlertDialog[1];
        for (int a = 0; a < found.size(); a++) {
            if (a > 0) list.addView(separator(context));
            list.addView(copyRow(context, found.get(a), found, dialog));
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
    private View copyRow(Context context, org.telegram.messenger.tj.TjWatchFinder.Copy candidate,
                         ArrayList<org.telegram.messenger.tj.TjWatchFinder.Copy> found, AlertDialog[] dialog) {
        String quality = candidate.quality();
        String described = candidate.described();
        String source = candidate.sourceName();

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(20), dp(10), dp(20), dp(10));
        row.setBackground(Theme.getSelectorDrawable(false));
        row.setOnClickListener(v -> {
            if (dialog[0] != null) dialog[0].dismiss();
            play(candidate, found);
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

    /**
     * Opens the copy in the Watch player, with the other copies it was found alongside so the
     * source can be changed without searching again, and with what it takes to find the next
     * episode.
     */
    private void play(org.telegram.messenger.tj.TjWatchFinder.Copy copy,
                      ArrayList<org.telegram.messenger.tj.TjWatchFinder.Copy> found) {
        TjWatchPlayerActivity.Session session = new TjWatchPlayerActivity.Session();
        session.tmdbId = id;
        session.series = series;
        session.name = name;
        session.poster = posterPath;
        session.year = year;
        session.season = pendingSeason;
        session.episode = pendingEpisode;
        session.finder = finder();
        if (series && pendingSeason == selectedSeason) {
            session.episodesSeason = selectedSeason;
            for (Episode episode : episodes) {
                session.episodes.add(new TjWatchPlayerActivity.EpisodeInfo(episode.number, episode.name));
            }
        }
        TjWatchPlayerActivity.open(this, session, copy, found);
    }
}
