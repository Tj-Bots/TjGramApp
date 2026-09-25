package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.Uri;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.FileStreamLoadOperation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.messenger.tj.TjWatchFinder;
import org.telegram.messenger.tj.TjWatchHistory;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.Components.TjSubtitleView;
import org.telegram.ui.Components.VideoPlayer;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

/**
 * The Watch player: a screen of its own for what is opened from Watch, laid out the way streaming
 * apps lay theirs out, and knowing what it is playing - which title, which episode, which other
 * copies were found - so it can change source, go to the next episode or pick another one.
 *
 * Videos opened anywhere else in the app still open in Telegram's own viewer; nothing here is
 * shared with it but the player engine underneath.
 */
public class TjWatchPlayerActivity extends BaseFragment {

    /** What is being watched, as the title screen knows it. */
    public static final class Session {
        public long tmdbId;
        public boolean series;
        public String name = "", poster = "";
        public int year;
        public int season = -1, episode = -1;
        /** How other copies and other episodes are found; null when there is no way to look. */
        public TjWatchFinder finder;
        /** The episodes of {@link #episodesSeason}, when the title screen had them loaded. */
        public final ArrayList<EpisodeInfo> episodes = new ArrayList<>();
        public int episodesSeason = -1;
    }

    public static final class EpisodeInfo {
        public final int number;
        public final String name;

        public EpisodeInfo(int number, String name) {
            this.number = number;
            this.name = name == null ? "" : name;
        }
    }

    private static final int NETFLIX_RED = 0xFFE50914;
    private static final int PANEL_BACKGROUND = 0xF0141414;
    private static final long HIDE_CONTROLS_AFTER = 3500;
    private static final long SEEK_STEP = 10_000;
    private static final long NEXT_CARD_BEFORE_END = 25_000;
    private static final long NEXT_PREFETCH_BEFORE_END = 120_000;
    private static final long NEXT_COUNTDOWN_MS = 7_000;
    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private final Session session;
    private TjWatchFinder.Copy current;
    private ArrayList<TjWatchFinder.Copy> sources;
    private final long startPosition;

    private VideoPlayer player;
    private FrameLayout root;
    private AspectRatioFrameLayout aspect;
    private TextureView textureView;
    private TjSubtitleView subtitles;
    private FrameLayout controls;
    private TextView titleView;
    private CenterButton playButton;
    private SeekBarView seekBar;
    private TextView timeView;
    private RadialProgressView buffering;
    private TextView errorView;
    private LinearLayout bottomButtons;
    private BarButton speedButton, episodesButton, sourceButton, nextButton;
    private FrameLayout lockOverlay;
    private FrameLayout panelHost;
    private LinearLayout nextBar;
    private NextPill nextPill;
    private android.animation.ValueAnimator nextCountdownAnimator;
    private LevelIndicator levelIndicator;

    private boolean controlsVisible = true;
    private boolean showElapsed;
    private boolean locked;
    private long pendingSeek = -1;
    private boolean autoSubtitlesDone;
    private boolean subtitleChosenByHand;
    private long lastSavedAt;

    // The next episode: looked for ahead of the end, offered in the last seconds.
    private int nextSeason = -1, nextEpisode = -1;
    private ArrayList<TjWatchFinder.Copy> nextCopies;
    private boolean nextSearching, nextSearched, nextDismissed;

    private int previousOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private int previousSystemUi;
    private boolean immersiveApplied;
    private float brightness = -1;

    private final Runnable hideControls = () -> setControlsVisible(false);
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            AndroidUtilities.runOnUIThread(this, 250);
        }
    };

    public static void open(BaseFragment host, Session session, TjWatchFinder.Copy copy, ArrayList<TjWatchFinder.Copy> sources) {
        if (host == null || host.getParentActivity() == null || copy == null || copy.message == null) return;
        long document = copy.message.getDocument() == null ? 0 : copy.message.getDocument().id;
        long position = Math.max(copy.position, TjWatchHistory.positionFor(copy.message.currentAccount, document));
        host.presentFragment(new TjWatchPlayerActivity(session, copy, sources, position));
    }

    /**
     * Carries on from a row of the watch history. The row knows the title, the episode and the
     * copy; other copies and the next episode are looked up by name, as the title screen would.
     */
    public static void resume(BaseFragment host, TjWatchHistory.Entry entry, MessageObject message) {
        Session session = new Session();
        try {
            session.tmdbId = Long.parseLong(entry.key.substring(entry.key.indexOf(':') + 1));
        } catch (Exception ignore) {
        }
        session.series = entry.series;
        session.name = entry.name;
        session.poster = entry.poster;
        session.year = entry.year;
        session.season = entry.season;
        session.episode = entry.episode;
        ArrayList<String> targets = new ArrayList<>();
        targets.add(entry.name);
        session.finder = TextUtils.isEmpty(entry.name) ? null : new TjWatchFinder(entry.name, targets, entry.year);
        open(host, session, new TjWatchFinder.Copy(message, entry.position, 0), null);
    }

    public TjWatchPlayerActivity(Session session, TjWatchFinder.Copy copy, ArrayList<TjWatchFinder.Copy> sources, long position) {
        this.session = session;
        this.current = copy;
        this.sources = sources == null ? new ArrayList<>() : sources;
        this.startPosition = position;
    }

    // ---------------------------------------------------------------- layout

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public View createView(Context context) {
        actionBar.setAddToContainer(false);

        root = new FrameLayout(context);
        root.setBackgroundColor(Color.BLACK);
        root.setKeepScreenOn(true);
        fragmentView = root;

        aspect = new AspectRatioFrameLayout(context);
        aspect.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(aspect, LayoutHelper.createFrame(-1, -1, Gravity.CENTER));
        textureView = new TextureView(context);
        aspect.addView(textureView, LayoutHelper.createFrame(-1, -1, Gravity.CENTER));

        subtitles = new TjSubtitleView(context);
        subtitles.setVideoView(textureView);
        root.addView(subtitles, LayoutHelper.createFrame(-1, -1));

        // Touch: a tap shows or hides the controls, a double tap on either side skips ten seconds,
        // and a slide up or down changes brightness on the left and volume on the right.
        View touchLayer = new View(context);
        GestureDetector gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            private boolean sliding;
            private boolean leftSide;
            private float startLevel;

            @Override
            public boolean onDown(MotionEvent e) {
                sliding = false;
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (locked) {
                    showLockOverlay();
                } else {
                    setControlsVisible(!controlsVisible);
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (locked) return false;
                float third = root.getWidth() / 3f;
                if (e.getX() < third) {
                    seekBy(-SEEK_STEP);
                } else if (e.getX() > third * 2) {
                    seekBy(SEEK_STEP);
                } else {
                    togglePlay();
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (locked || e1 == null) return false;
                if (!sliding) {
                    if (Math.abs(e2.getY() - e1.getY()) < dp(16) || Math.abs(distanceY) < Math.abs(distanceX)) {
                        return false;
                    }
                    sliding = true;
                    leftSide = e1.getX() < root.getWidth() / 2f;
                    startLevel = leftSide ? currentBrightness() : currentVolume();
                }
                float delta = (e1.getY() - e2.getY()) / (root.getHeight() * 0.7f);
                float level = Math.max(0f, Math.min(1f, startLevel + delta));
                if (leftSide) setBrightness(level); else setVolume(level);
                levelIndicator.show(leftSide, level);
                return true;
            }
        });
        touchLayer.setOnTouchListener((v, event) -> gestures.onTouchEvent(event));
        root.addView(touchLayer, LayoutHelper.createFrame(-1, -1));

        buffering = new RadialProgressView(context);
        buffering.setSize(dp(44));
        buffering.setProgressColor(NETFLIX_RED);
        root.addView(buffering, LayoutHelper.createFrame(64, 64, Gravity.CENTER));

        errorView = new TextView(context);
        errorView.setTextColor(Color.WHITE);
        errorView.setTextSize(15);
        errorView.setGravity(Gravity.CENTER);
        errorView.setVisibility(View.GONE);
        root.addView(errorView, LayoutHelper.createFrame(-1, -2, Gravity.CENTER, 48, 0, 48, 0));

        controls = new FrameLayout(context);
        root.addView(controls, LayoutHelper.createFrame(-1, -1));
        buildTopBar(context);
        buildCenter(context);
        buildBottom(context);

        levelIndicator = new LevelIndicator(context);
        root.addView(levelIndicator, LayoutHelper.createFrame(-1, -1));

        buildNextBar(context);
        buildLockOverlay(context);

        panelHost = new FrameLayout(context);
        panelHost.setVisibility(View.GONE);
        panelHost.setOnClickListener(v -> closePanel());
        root.addView(panelHost, LayoutHelper.createFrame(-1, -1));

        startPlayer(current, startPosition);
        return root;
    }

    private void buildTopBar(Context context) {
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xB0000000, 0x00000000}));
        controls.addView(shade, LayoutHelper.createFrame(-1, 96, Gravity.TOP));

        ImageView back = new ImageView(context);
        back.setImageResource(R.drawable.ic_ab_back);
        back.setColorFilter(Color.WHITE);
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x33ffffff, 1));
        back.setOnClickListener(v -> finishFragment());
        controls.addView(back, LayoutHelper.createFrame(48, 48, Gravity.TOP | Gravity.LEFT, 12, 10, 0, 0));

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        controls.addView(titleView, LayoutHelper.createFrame(-1, 48, Gravity.TOP, 72, 10, 72, 0));
        updateTitle();
    }

    private void buildCenter(Context context) {
        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.HORIZONTAL);
        center.setGravity(Gravity.CENTER);
        center.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        CenterButton rewind = new CenterButton(context, CenterButton.REWIND);
        rewind.setOnClickListener(v -> { seekBy(-SEEK_STEP); scheduleHide(); });
        playButton = new CenterButton(context, CenterButton.PLAY);
        playButton.setOnClickListener(v -> { togglePlay(); scheduleHide(); });
        CenterButton forward = new CenterButton(context, CenterButton.FORWARD);
        forward.setOnClickListener(v -> { seekBy(SEEK_STEP); scheduleHide(); });

        center.addView(rewind, LayoutHelper.createLinear(64, 64, 0, 0, 72, 0));
        center.addView(playButton, LayoutHelper.createLinear(72, 72));
        center.addView(forward, LayoutHelper.createLinear(64, 64, 72, 0, 0, 0));
        controls.addView(center, LayoutHelper.createFrame(-2, -2, Gravity.CENTER));
    }

    private void buildBottom(Context context) {
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, new int[]{0xC0000000, 0x00000000}));
        controls.addView(shade, LayoutHelper.createFrame(-1, 140, Gravity.BOTTOM));

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout seekRow = new LinearLayout(context);
        seekRow.setOrientation(LinearLayout.HORIZONTAL);
        seekRow.setGravity(Gravity.CENTER_VERTICAL);
        seekBar = new SeekBarView(context);
        seekRow.addView(seekBar, LayoutHelper.createLinear(0, 36, 1f));
        timeView = new TextView(context);
        timeView.setTextColor(Color.WHITE);
        timeView.setTextSize(13);
        timeView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        timeView.setPadding(dp(6), dp(8), dp(6), dp(8));
        // As in most players: a tap switches between the time left and "watched / total".
        timeView.setOnClickListener(v -> {
            showElapsed = !showElapsed;
            tick();
            scheduleHide();
        });
        seekRow.addView(timeView, LayoutHelper.createLinear(-2, -2, 6, 0, 0, 0));
        bottom.addView(seekRow, LayoutHelper.createLinear(-1, -2, 24, 0, 24, 0));

        bottomButtons = new LinearLayout(context);
        bottomButtons.setOrientation(LinearLayout.HORIZONTAL);
        bottomButtons.setGravity(Gravity.CENTER);
        speedButton = addBarButton(context, R.drawable.msg_speed, speedLabel(), v -> showSpeedPanel());
        addBarButton(context, R.drawable.outline_header_lock_24, TjLocale.getString(R.string.TjPlayerLock), v -> setLocked(true));
        episodesButton = addBarButton(context, R.drawable.msg_list, TjLocale.getString(R.string.TjPlayerEpisodes), v -> showEpisodesPanel());
        addBarButton(context, R.drawable.tj_subtitles, TjLocale.getString(R.string.TjPlayerAudioSubs), v -> showTracksPanel());
        sourceButton = addBarButton(context, R.drawable.msg_replace, TjLocale.getString(R.string.TjPlayerSource), v -> showSourcesPanel());
        nextButton = addBarButton(context, R.drawable.ic_action_next, TjLocale.getString(R.string.TjPlayerNext), v -> goToNext());
        bottom.addView(bottomButtons, LayoutHelper.createLinear(-1, 44, 12, 2, 12, 8));

        controls.addView(bottom, LayoutHelper.createFrame(-1, -2, Gravity.BOTTOM));
        updateBarButtons();
    }

    private BarButton addBarButton(Context context, int icon, String label, View.OnClickListener listener) {
        BarButton button = new BarButton(context, icon, label);
        button.setOnClickListener(v -> {
            listener.onClick(v);
            scheduleHide();
        });
        bottomButtons.addView(button, LayoutHelper.createLinear(-2, -1, 10, 0, 10, 0));
        return button;
    }

    /**
     * The small "next episode" button the streaming apps put in the corner at the credits, filling
     * up while it counts down to going on by itself.
     */
    private void buildNextBar(Context context) {
        nextBar = new LinearLayout(context);
        nextBar.setOrientation(LinearLayout.HORIZONTAL);
        nextBar.setGravity(Gravity.CENTER_VERTICAL);
        nextBar.setVisibility(View.GONE);

        nextPill = new NextPill(context, TjLocale.getString(R.string.TjPlayerNext));
        nextPill.setOnClickListener(v -> playNext());
        nextBar.addView(nextPill, LayoutHelper.createLinear(-2, 40));
        root.addView(nextBar, LayoutHelper.createFrame(-2, -2,
                Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 28, 0, 28, 28));
    }

    private void buildLockOverlay(Context context) {
        lockOverlay = new FrameLayout(context);
        lockOverlay.setVisibility(View.GONE);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x99000000);
        background.setCornerRadius(dp(12));
        column.setBackground(background);
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.outline_header_lock_24);
        icon.setColorFilter(Color.WHITE);
        column.addView(icon, LayoutHelper.createLinear(28, 28, Gravity.CENTER_HORIZONTAL));
        TextView text = new TextView(context);
        text.setText(TjLocale.getString(R.string.TjPlayerUnlock));
        text.setTextColor(Color.WHITE);
        text.setTextSize(13);
        column.addView(text, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
        column.setOnClickListener(v -> setLocked(false));
        lockOverlay.addView(column, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 32));
        root.addView(lockOverlay, LayoutHelper.createFrame(-1, -1));
    }

    // ---------------------------------------------------------------- playback

    private void startPlayer(TjWatchFinder.Copy copy, long position) {
        saveProgress();
        current = copy;
        autoSubtitlesDone = false;
        subtitleChosenByHand = false;
        errorView.setVisibility(View.GONE);
        subtitles.clear();
        updateTitle();
        updateBarButtons();
        ensureSeasonLoaded();
        rememberInHistory(position);

        final Uri uri = uriFor(copy.message);
        if (uri == null) {
            showError();
            return;
        }
        if (player == null) {
            player = new VideoPlayer();
            player.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {
                    onPlayerState(playbackState);
                }

                @Override
                public void onError(VideoPlayer videoPlayer, Exception e) {
                    FileLog.e(e);
                    showError();
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    // The same arithmetic as Telegram's own viewer, rotation included.
                    if (unappliedRotationDegrees == 90 || unappliedRotationDegrees == 270) {
                        int swap = width;
                        width = height;
                        height = swap;
                    }
                    aspect.setAspectRatio(height == 0 ? 1 : (width * pixelWidthHeightRatio) / height, unappliedRotationDegrees);
                }

                @Override
                public void onRenderedFirstFrame() {
                }
            });
            player.setSubtitleListener(cues -> subtitles.setCues(cues));
            player.setTracksChangedListener(this::onTracksChanged);
        }
        player.setTextureView(textureView);
        pendingSeek = position > 0 ? position : -1;
        buffering.setVisibility(View.VISIBLE);
        playButton.setVisibility(View.INVISIBLE);
        player.preparePlayer(uri, "other");
        player.setPlaybackSpeed(currentSpeed);
        player.play();
        AndroidUtilities.cancelRunOnUIThread(ticker);
        AndroidUtilities.runOnUIThread(ticker);
        setControlsVisible(true);
    }

    /** The file on the device if it is there, otherwise a stream of it straight from Telegram. */
    private static Uri uriFor(MessageObject message) {
        try {
            TLRPC.Document document = message.getDocument();
            if (document == null) return null;
            if (!TextUtils.isEmpty(message.messageOwner.attachPath)) {
                File attached = new File(message.messageOwner.attachPath);
                if (attached.exists()) return Uri.fromFile(attached);
            }
            File file = FileLoader.getInstance(message.currentAccount).getPathToMessage(message.messageOwner);
            if (file != null && file.exists()) return Uri.fromFile(file);
            return FileStreamLoadOperation.prepareUri(message.currentAccount, document, message);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void onPlayerState(int state) {
        boolean waiting = state == ExoPlayer.STATE_BUFFERING;
        buffering.setVisibility(waiting ? View.VISIBLE : View.GONE);
        playButton.setVisibility(waiting ? View.INVISIBLE : View.VISIBLE);
        if (state == ExoPlayer.STATE_READY && controlsVisible) {
            scheduleHide();
        }
        if (state == ExoPlayer.STATE_READY && pendingSeek >= 0) {
            long duration = player.getDuration();
            long target = pendingSeek;
            pendingSeek = -1;
            // A finished copy starts over; a nearly finished one is not worth resuming.
            if (duration > 0 && target < duration - 10_000) {
                player.seekTo(target);
            }
        }
        if (state == ExoPlayer.STATE_ENDED) {
            saveProgress();
            if (!showNextCardIfReady(true)) {
                setControlsVisible(true);
            }
        }
        playButton.setPlaying(player != null && player.isPlaying());
    }

    private void showError() {
        buffering.setVisibility(View.GONE);
        errorView.setText(TjLocale.getString(R.string.TjMediaPlaybackError));
        errorView.setVisibility(View.VISIBLE);
        setControlsVisible(true);
    }

    private void tick() {
        if (player == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration > 0 && !seekBar.dragging) {
            seekBar.setProgress(position / (float) duration, player.getBufferedPosition() / (float) duration);
        }
        long shown = seekBar.dragging && duration > 0 ? (long) (seekBar.progress * duration) : position;
        if (duration <= 0) {
            timeView.setText("");
        } else if (showElapsed) {
            timeView.setText(formatTime(shown) + " / " + formatTime(duration));
        } else {
            timeView.setText("-" + formatTime(Math.max(0, duration - shown)));
        }
        playButton.setPlaying(player.isPlaying());

        long now = System.currentTimeMillis();
        if (now - lastSavedAt > 5000) {
            lastSavedAt = now;
            saveProgress();
        }
        if (duration > 3 * 60_000 && duration - position < NEXT_PREFETCH_BEFORE_END) {
            prefetchNext(null);
        }
        if (duration > 0 && duration - position < NEXT_CARD_BEFORE_END) {
            showNextCardIfReady(false);
        }
    }

    private void togglePlay() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            saveProgress();
        } else {
            if (player.getPlaybackState() == ExoPlayer.STATE_ENDED) player.seekTo(0);
            player.play();
        }
        playButton.setPlaying(player.isPlaying());
    }

    private void seekBy(long delta) {
        if (player == null) return;
        long duration = player.getDuration();
        long target = Math.max(0, player.getCurrentPosition() + delta);
        if (duration > 0) target = Math.min(target, duration - 500);
        player.seekTo(target);
        tick();
    }

    private float currentSpeed = 1f;

    private String speedLabel() {
        String value = currentSpeed == (int) currentSpeed ? String.valueOf((int) currentSpeed) : String.valueOf(currentSpeed);
        return TjLocale.getString(R.string.TjPlayerSpeed) + " (" + value + "x)";
    }

    private void saveProgress() {
        if (player == null || current == null) return;
        long duration = player.getDuration();
        long position = player.getCurrentPosition();
        if (duration <= 0 || position < 0) return;
        TjWatchHistory.progress(current.message, position, duration);
        TjWatchHistory.episodeProgress(current.message, session.tmdbId, session.series,
                session.season, session.episode, position, duration);
        TjMediaStore.getInstance().progress(current.message, position, duration);
    }

    private final org.telegram.messenger.tj.TjTmdb seasonClient = new org.telegram.messenger.tj.TjTmdb();

    /**
     * The names and the list of the season being watched, when they are not known yet - opened
     * from the history, or carried on into a new season - so the title and the episodes list have
     * something to show.
     */
    private void ensureSeasonLoaded() {
        if (!session.series || session.tmdbId == 0 || session.season < 0 || current == null) return;
        if (session.episodesSeason == session.season && !session.episodes.isEmpty()) return;
        final int season = session.season;
        seasonClient.season(current.message.currentAccount, session.tmdbId, season, (body, error) -> {
            if (fragmentView == null || session.season != season) return;
            org.json.JSONArray list = body == null ? null : body.optJSONArray("episodes");
            if (list == null || list.length() == 0) return;
            session.episodes.clear();
            for (int i = 0; i < list.length(); i++) {
                org.json.JSONObject object = list.optJSONObject(i);
                if (object == null) continue;
                int number = object.optInt("episode_number", -1);
                if (number >= 0) session.episodes.add(new EpisodeInfo(number, object.optString("name", "")));
            }
            session.episodesSeason = season;
            updateTitle();
            updateBarButtons();
        });
    }

    private void rememberInHistory(long position) {
        TjWatchHistory.rememberCopy(current.message, session.tmdbId, session.series, session.season, session.episode);
        TjWatchHistory.Entry entry = TjWatchHistory.entryFor(session.tmdbId, session.series, session.name,
                session.poster, session.year, session.season, session.episode, current.message);
        if (entry != null) {
            entry.position = Math.max(0, position);
            TjWatchHistory.remember(entry);
        }
    }

    // ---------------------------------------------------------------- tracks

    private void onTracksChanged() {
        if (player == null || autoSubtitlesDone || subtitleChosenByHand) return;
        if (!TjSettingsActivity.isSubtitleAutoEnabled()) return;
        ArrayList<VideoPlayer.TjTrack> tracks = player.getSubtitleTracks();
        if (tracks.isEmpty()) return;
        autoSubtitlesDone = true;
        int chosen = PhotoViewer.matchTrackLanguage(tracks, PhotoViewer.currentAppLanguage());
        if (chosen < 0) chosen = PhotoViewer.matchTrackLanguage(tracks, "en");
        if (chosen < 0) chosen = 0;
        player.selectSubtitleTrack(tracks.get(chosen));
    }

    // ---------------------------------------------------------------- controls

    private void setControlsVisible(boolean visible) {
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        if (locked) visible = false;
        controlsVisible = visible;
        controls.animate().alpha(visible ? 1f : 0f).setDuration(180).start();
        controls.setVisibility(View.VISIBLE);
        if (!visible) {
            AndroidUtilities.runOnUIThread(() -> {
                if (!controlsVisible) controls.setVisibility(View.GONE);
            }, 190);
        }
        subtitles.setBottomOffset(visible ? dp(110) : 0);
        if (nextBar != null) {
            nextBar.animate().translationY(visible ? -dp(92) : 0).setDuration(180).start();
        }
        if (visible) scheduleHide();
    }

    private void scheduleHide() {
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        if (player != null && player.isPlaying() && panelHost.getVisibility() != View.VISIBLE) {
            AndroidUtilities.runOnUIThread(hideControls, HIDE_CONTROLS_AFTER);
        }
    }

    private void setLocked(boolean value) {
        locked = value;
        if (value) {
            setControlsVisible(false);
            showLockOverlay();
        } else {
            lockOverlay.setVisibility(View.GONE);
            setControlsVisible(true);
        }
    }

    private final Runnable hideLockOverlay = () -> {
        if (lockOverlay != null) lockOverlay.setVisibility(View.GONE);
    };

    private void showLockOverlay() {
        lockOverlay.setVisibility(View.VISIBLE);
        AndroidUtilities.cancelRunOnUIThread(hideLockOverlay);
        AndroidUtilities.runOnUIThread(hideLockOverlay, 2500);
    }

    private void updateTitle() {
        if (titleView == null) return;
        StringBuilder title = new StringBuilder();
        if (session.series && session.episode >= 0) {
            if (session.season > 0) {
                title.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(session.season).append(' ');
            }
            title.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(session.episode);
            String episodeName = episodeName(session.season, session.episode);
            if (!episodeName.isEmpty()) title.append(" · \"").append(episodeName).append('"');
            if (!TextUtils.isEmpty(session.name)) title.insert(0, session.name + " · ");
        } else {
            title.append(session.name);
        }
        titleView.setText(title);
    }

    private String episodeName(int season, int episode) {
        if (season != session.episodesSeason) return "";
        for (EpisodeInfo info : session.episodes) {
            if (info.number == episode) {
                String name = info.name.trim();
                // A catalogue with no name for it says "Episode 3", which says nothing new.
                boolean placeholder = name.isEmpty() || name.contains(String.valueOf(episode))
                        && name.replaceAll("[\\d\\s.:#\\-]", "").length() <= 10;
                return placeholder ? "" : name;
            }
        }
        return "";
    }

    private void updateBarButtons() {
        if (bottomButtons == null) return;
        boolean episodic = session.series && session.episode >= 0 && session.finder != null;
        episodesButton.setVisibility(episodic && session.season == session.episodesSeason && !session.episodes.isEmpty() ? View.VISIBLE : View.GONE);
        nextButton.setVisibility(episodic ? View.VISIBLE : View.GONE);
        sourceButton.setVisibility(sources.size() > 1 || session.finder != null ? View.VISIBLE : View.GONE);
        speedButton.setLabel(speedLabel());
    }

    // ---------------------------------------------------------------- panels

    private LinearLayout openPanel(String title, float widthFraction) {
        Context context = getParentActivity();
        panelHost.removeAllViews();
        FrameLayout sheet = new FrameLayout(context);
        sheet.setBackgroundColor(PANEL_BACKGROUND);
        sheet.setClickable(true);
        ScrollView scroll = new ScrollView(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(8), dp(12), dp(8), dp(12));
        if (title != null) {
            TextView header = new TextView(context);
            header.setText(title);
            header.setTextColor(Color.WHITE);
            header.setTextSize(17);
            header.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            header.setPadding(dp(12), dp(4), dp(12), dp(10));
            column.addView(header);
        }
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, LayoutHelper.createFrame(-1, -1));
        int width = (int) (Math.max(root.getWidth(), dp(320)) * widthFraction);
        panelHost.addView(sheet, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
        panelHost.setVisibility(View.VISIBLE);
        sheet.setTranslationX(width);
        sheet.animate().translationX(0).setDuration(200).start();
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        return column;
    }

    private void closePanel() {
        panelHost.setVisibility(View.GONE);
        panelHost.removeAllViews();
        scheduleHide();
    }

    private TextView panelRow(LinearLayout column, CharSequence text, CharSequence subtitle, boolean selected, Runnable action) {
        Context context = column.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x22ffffff, 2));
        TextView main = new TextView(context);
        main.setText(selected ? "✓  " + text : text);
        main.setTextColor(selected ? Color.WHITE : 0xFFB3B3B3);
        main.setTextSize(15);
        if (selected) main.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        row.addView(main);
        if (!TextUtils.isEmpty(subtitle)) {
            TextView second = new TextView(context);
            second.setText(subtitle);
            second.setTextColor(0xFF8C8C8C);
            second.setTextSize(12);
            second.setMaxLines(2);
            second.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(second);
        }
        row.setOnClickListener(v -> action.run());
        column.addView(row, LayoutHelper.createLinear(-1, -2));
        return main;
    }

    private void panelNote(LinearLayout column, String text) {
        TextView note = new TextView(column.getContext());
        note.setText(text);
        note.setTextColor(0xFF8C8C8C);
        note.setTextSize(14);
        note.setPadding(dp(12), dp(8), dp(12), dp(8));
        column.addView(note);
    }

    private void showSpeedPanel() {
        LinearLayout column = openPanel(TjLocale.getString(R.string.TjPlayerSpeed), 0.3f);
        for (float speed : SPEEDS) {
            String label = (speed == (int) speed ? String.valueOf((int) speed) : String.valueOf(speed)) + "x";
            panelRow(column, label, null, speed == currentSpeed, () -> {
                currentSpeed = speed;
                if (player != null) player.setPlaybackSpeed(speed);
                updateBarButtons();
                closePanel();
            });
        }
    }

    private void showTracksPanel() {
        if (player == null) return;
        Context context = getParentActivity();
        panelHost.removeAllViews();
        LinearLayout sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.HORIZONTAL);
        sheet.setBackgroundColor(PANEL_BACKGROUND);
        sheet.setClickable(true);
        sheet.setPadding(dp(8), dp(12), dp(8), dp(12));

        LinearLayout audioColumn = trackColumn(sheet, TjLocale.getString(R.string.TjPlayerAudio));
        ArrayList<VideoPlayer.TjTrack> audio = player.getAudioTracks();
        if (audio.isEmpty()) {
            panelRow(audioColumn, TjLocale.getString(R.string.TjPlayerAudioDefault), null, true, () -> { });
        }
        for (VideoPlayer.TjTrack track : audio) {
            panelRow(audioColumn, track.label, null, track.selected, () -> {
                player.selectAudioTrack(track);
                closePanel();
            });
        }

        LinearLayout subtitleColumn = trackColumn(sheet, TjLocale.getString(R.string.TjPlayerSubtitles));
        ArrayList<VideoPlayer.TjTrack> texts = player.getSubtitleTracks();
        boolean anySelected = false;
        for (VideoPlayer.TjTrack track : texts) anySelected |= track.selected;
        panelRow(subtitleColumn, TjLocale.getString(R.string.TjPlayerSubtitlesOff), null, !anySelected, () -> {
            subtitleChosenByHand = true;
            player.selectSubtitleTrack(null);
            subtitles.clear();
            closePanel();
        });
        for (VideoPlayer.TjTrack track : texts) {
            panelRow(subtitleColumn, track.label, null, track.selected, () -> {
                subtitleChosenByHand = true;
                player.selectSubtitleTrack(track);
                closePanel();
            });
        }

        int width = (int) (root.getWidth() * 0.62f);
        panelHost.addView(sheet, new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.RIGHT));
        panelHost.setVisibility(View.VISIBLE);
        sheet.setTranslationX(width);
        sheet.animate().translationX(0).setDuration(200).start();
        AndroidUtilities.cancelRunOnUIThread(hideControls);
    }

    private LinearLayout trackColumn(LinearLayout sheet, String title) {
        Context context = sheet.getContext();
        ScrollView scroll = new ScrollView(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView header = new TextView(context);
        header.setText(title);
        header.setTextColor(Color.WHITE);
        header.setTextSize(17);
        header.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        header.setPadding(dp(12), dp(4), dp(12), dp(10));
        column.addView(header);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
        sheet.addView(scroll, LayoutHelper.createLinear(0, -1, 1f));
        return column;
    }

    private void showSourcesPanel() {
        LinearLayout column = openPanel(TjLocale.getString(R.string.TjPlayerSource), 0.5f);
        if (sources.isEmpty() && session.finder != null) {
            panelNote(column, TjLocale.getString(R.string.TjPlayerSearching));
            session.finder.find(session.season, session.episode, found -> {
                sources = found;
                updateBarButtons();
                if (panelHost.getVisibility() == View.VISIBLE) showSourcesPanel();
            });
            return;
        }
        fillCopies(column, sources, copy -> {
            if (copy == current) {
                closePanel();
                return;
            }
            long position = player == null ? 0 : player.getCurrentPosition();
            closePanel();
            startPlayer(copy, position);
        });
    }

    private void showEpisodesPanel() {
        String header = TjLocale.getString(R.string.TjMediaSeason) + " " + session.season;
        LinearLayout column = openPanel(header, 0.5f);
        for (EpisodeInfo info : session.episodes) {
            String name = episodeName(session.episodesSeason, info.number);
            String label = TjLocale.getString(R.string.TjMediaEpisode) + " " + info.number;
            panelRow(column, label, name, info.number == session.episode, () -> {
                if (info.number == session.episode) {
                    closePanel();
                    return;
                }
                closePanel();
                playEpisode(session.season, info.number, null);
            });
        }
    }

    private void fillCopies(LinearLayout column, ArrayList<TjWatchFinder.Copy> copies, org.telegram.messenger.Utilities.Callback<TjWatchFinder.Copy> onPick) {
        if (copies.isEmpty()) {
            panelNote(column, TjLocale.getString(R.string.TjPlayerNotFound));
            return;
        }
        for (TjWatchFinder.Copy copy : copies) {
            StringBuilder head = new StringBuilder();
            String quality = copy.quality();
            if (!quality.isEmpty()) head.append(quality).append(" · ");
            head.append(AndroidUtilities.formatFileSize(copy.size()));
            String source = copy.sourceName();
            if (!source.isEmpty()) head.append(" · ").append(source);
            boolean selected = current != null && copy.message.getId() == current.message.getId()
                    && copy.message.getDialogId() == current.message.getDialogId();
            panelRow(column, head, copy.described(), selected, () -> onPick.run(copy));
        }
    }

    // ---------------------------------------------------------------- episodes

    /** The episode after this one: the next number, or the first of the next season. */
    private void prefetchNext(Runnable then) {
        if (!session.series || session.finder == null || session.episode < 0) {
            if (then != null) then.run();
            return;
        }
        if (nextSearched) {
            if (then != null) then.run();
            return;
        }
        if (nextSearching) {
            if (then != null) pendingAfterNext = then;
            return;
        }
        nextSearching = true;
        pendingAfterNext = then;
        final int season = session.season, episode = session.episode + 1;
        session.finder.find(season, episode, found -> {
            if (!found.isEmpty() || !lastOfSeason(session.episode)) {
                finishNextSearch(season, episode, found);
                return;
            }
            session.finder.find(season + 1, 1, nextSeasonFound -> finishNextSearch(season + 1, 1, nextSeasonFound));
        });
    }

    private Runnable pendingAfterNext;

    private void finishNextSearch(int season, int episode, ArrayList<TjWatchFinder.Copy> found) {
        nextSearching = false;
        nextSearched = true;
        nextSeason = season;
        nextEpisode = episode;
        nextCopies = found;
        Runnable then = pendingAfterNext;
        pendingAfterNext = null;
        if (then != null) then.run();
    }

    private boolean lastOfSeason(int episode) {
        if (session.season != session.episodesSeason || session.episodes.isEmpty()) return false;
        int last = 0;
        for (EpisodeInfo info : session.episodes) last = Math.max(last, info.number);
        return episode >= last;
    }

    /** The copy to carry on with without asking: one from the same chat as the one playing. */
    private TjWatchFinder.Copy sameChatCopy(ArrayList<TjWatchFinder.Copy> copies) {
        if (copies == null || current == null) return null;
        for (TjWatchFinder.Copy copy : copies) {
            if (copy.message.currentAccount == current.message.currentAccount
                    && copy.message.getDialogId() == current.message.getDialogId()) {
                return copy;
            }
        }
        return null;
    }

    private boolean showNextCardIfReady(boolean ended) {
        if (nextDismissed && !ended) return false;
        if (!nextSearched || nextCopies == null || nextCopies.isEmpty()) {
            if (ended && session.series && session.finder != null) {
                prefetchNext(() -> {
                    if (nextCopies != null && !nextCopies.isEmpty()) showNextCardIfReady(true);
                });
            }
            return false;
        }
        if (nextBar.getVisibility() == View.VISIBLE) return true;
        nextBar.setVisibility(View.VISIBLE);
        nextBar.setAlpha(0f);
        nextBar.animate().alpha(1f).setDuration(200).start();
        cancelCountdown();
        {
            nextPill.setProgress(0f);
            nextCountdownAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            nextCountdownAnimator.setDuration(NEXT_COUNTDOWN_MS);
            nextCountdownAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            nextCountdownAnimator.addUpdateListener(a -> nextPill.setProgress((float) a.getAnimatedValue()));
            nextCountdownAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(android.animation.Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    if (!cancelled && nextCountdownAnimator == animation) playNext();
                }
            });
            nextCountdownAnimator.start();
        }
        return true;
    }

    private void cancelCountdown() {
        if (nextCountdownAnimator != null) {
            android.animation.ValueAnimator animator = nextCountdownAnimator;
            nextCountdownAnimator = null;
            animator.cancel();
        }
    }

    private String nextLabel() {
        StringBuilder label = new StringBuilder();
        if (nextSeason > 0) label.append(TjLocale.getString(R.string.TjMediaSeason)).append(' ').append(nextSeason).append(' ');
        label.append(TjLocale.getString(R.string.TjMediaEpisode)).append(' ').append(nextEpisode);
        String name = episodeName(nextSeason, nextEpisode);
        if (!name.isEmpty()) label.append(" · ").append(name);
        return label.toString();
    }

    private void dismissNextCard() {
        nextDismissed = true;
        hideNextCard();
    }

    private void hideNextCard() {
        cancelCountdown();
        nextBar.setVisibility(View.GONE);
    }

    /** From the button: straight on when the same chat has it, otherwise the copies to pick from. */
    private void goToNext() {
        if (nextSearched) {
            playNext();
            return;
        }
        LinearLayout column = openPanel(TjLocale.getString(R.string.TjPlayerNext), 0.5f);
        panelNote(column, TjLocale.getString(R.string.TjPlayerSearching));
        prefetchNext(() -> {
            if (panelHost.getVisibility() == View.VISIBLE) closePanel();
            playNext();
        });
    }

    private void playNext() {
        hideNextCard();
        if (nextCopies == null || nextCopies.isEmpty()) {
            LinearLayout column = openPanel(TjLocale.getString(R.string.TjPlayerNext), 0.5f);
            panelNote(column, TjLocale.getString(R.string.TjPlayerNoNext));
            return;
        }
        final int season = nextSeason, episode = nextEpisode;
        final ArrayList<TjWatchFinder.Copy> copies = nextCopies;
        // The copy from the same chat when there is one - probably the same picture and the same
        // subtitles - otherwise the best one found. Another can still be picked under Source.
        TjWatchFinder.Copy same = sameChatCopy(copies);
        switchEpisode(season, episode, same != null ? same : copies.get(0), copies);
    }

    /** An episode picked from the list: found first, then played or offered like the next one. */
    private void playEpisode(int season, int episode, Runnable after) {
        LinearLayout column = openPanel(TjLocale.getString(R.string.TjMediaEpisode) + " " + episode, 0.5f);
        panelNote(column, TjLocale.getString(R.string.TjPlayerSearching));
        session.finder.find(season, episode, found -> {
            if (getParentActivity() == null) return;
            if (found.isEmpty()) {
                LinearLayout empty = openPanel(TjLocale.getString(R.string.TjMediaEpisode) + " " + episode, 0.5f);
                panelNote(empty, TjLocale.getString(R.string.TjPlayerNotFound));
                return;
            }
            TjWatchFinder.Copy same = sameChatCopy(found);
            if (same != null) {
                closePanel();
                switchEpisode(season, episode, same, found);
            } else {
                LinearLayout pick = openPanel(TjLocale.getString(R.string.TjPlayerChooseCopy), 0.55f);
                fillCopies(pick, found, copy -> {
                    closePanel();
                    switchEpisode(season, episode, copy, found);
                });
            }
        });
    }

    private void switchEpisode(int season, int episode, TjWatchFinder.Copy copy, ArrayList<TjWatchFinder.Copy> copies) {
        saveProgress();
        session.season = season;
        session.episode = episode;
        sources = copies;
        nextSearched = false;
        nextSearching = false;
        nextDismissed = false;
        nextCopies = null;
        pendingAfterNext = null;
        long document = copy.message.getDocument() == null ? 0 : copy.message.getDocument().id;
        long position = Math.max(copy.position, TjWatchHistory.positionFor(copy.message.currentAccount, document));
        startPlayer(copy, position);
    }

    // ---------------------------------------------------------------- brightness and volume

    private float currentBrightness() {
        Activity activity = getParentActivity();
        if (activity == null) return 0.5f;
        float value = activity.getWindow().getAttributes().screenBrightness;
        if (value < 0) {
            try {
                value = android.provider.Settings.System.getInt(activity.getContentResolver(),
                        android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f;
            } catch (Exception e) {
                value = 0.5f;
            }
        }
        return value;
    }

    private void setBrightness(float value) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        brightness = Math.max(0.02f, value);
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = brightness;
        activity.getWindow().setAttributes(params);
    }

    private float currentVolume() {
        AudioManager audio = audioManager();
        if (audio == null) return 0.5f;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return max <= 0 ? 0.5f : audio.getStreamVolume(AudioManager.STREAM_MUSIC) / (float) max;
    }

    private void setVolume(float value) {
        AudioManager audio = audioManager();
        if (audio == null) return;
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        try {
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private AudioManager audioManager() {
        Activity activity = getParentActivity();
        return activity == null ? null : (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onResume() {
        super.onResume();
        applyFullscreen(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (player != null && player.isPlaying()) {
            player.pause();
        }
        saveProgress();
        applyFullscreen(false);
    }

    @Override
    public void onFragmentDestroy() {
        seasonClient.cancel();
        AndroidUtilities.cancelRunOnUIThread(ticker);
        AndroidUtilities.cancelRunOnUIThread(hideControls);
        cancelCountdown();
        AndroidUtilities.cancelRunOnUIThread(hideLockOverlay);
        saveProgress();
        if (player != null) {
            player.setDelegate(null);
            player.setSubtitleListener(null);
            player.setTracksChangedListener(null);
            player.releasePlayer(true);
            player = null;
        }
        applyFullscreen(false);
        super.onFragmentDestroy();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (panelHost != null && panelHost.getVisibility() == View.VISIBLE) {
            if (invoked) closePanel();
            return false;
        }
        if (locked) {
            if (invoked) showLockOverlay();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    @Override
    public int getNavigationBarColor() {
        return Color.BLACK;
    }

    /** Landscape and edge to edge while the player is in front, and everything as it was after. */
    @SuppressWarnings("deprecation")
    private void applyFullscreen(boolean enable) {
        Activity activity = getParentActivity();
        if (activity == null) return;
        View decor = activity.getWindow().getDecorView();
        if (enable) {
            if (!immersiveApplied) {
                previousOrientation = activity.getRequestedOrientation();
                previousSystemUi = decor.getSystemUiVisibility();
                immersiveApplied = true;
            }
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            if (brightness >= 0) setBrightness(brightness);
        } else if (immersiveApplied) {
            immersiveApplied = false;
            activity.setRequestedOrientation(previousOrientation);
            decor.setSystemUiVisibility(previousSystemUi);
            WindowManager.LayoutParams params = activity.getWindow().getAttributes();
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
            activity.getWindow().setAttributes(params);
        }
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        seconds %= 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    // ---------------------------------------------------------------- views

    /** Play/pause, or the round "10" arrows either side of it. */
    private static final class CenterButton extends View {
        static final int REWIND = 0, PLAY = 1, FORWARD = 2;
        private final int type;
        private boolean playing;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        CenterButton(Context context, int type) {
            super(context);
            this.type = type;
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setColor(Color.WHITE);
            stroke.setStrokeWidth(dp(2.5f));
            stroke.setStrokeCap(Paint.Cap.ROUND);
            fill.setColor(Color.WHITE);
            text.setColor(Color.WHITE);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(dp(13));
            text.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x33ffffff, 1));
        }

        void setPlaying(boolean value) {
            if (playing != value) {
                playing = value;
                invalidate();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            if (type == PLAY) {
                float size = Math.min(getWidth(), getHeight()) * 0.42f;
                if (playing) {
                    float bar = size * 0.32f;
                    canvas.drawRect(cx - size * 0.5f, cy - size * 0.6f, cx - size * 0.5f + bar, cy + size * 0.6f, fill);
                    canvas.drawRect(cx + size * 0.5f - bar, cy - size * 0.6f, cx + size * 0.5f, cy + size * 0.6f, fill);
                } else {
                    path.reset();
                    path.moveTo(cx - size * 0.4f, cy - size * 0.62f);
                    path.lineTo(cx + size * 0.62f, cy);
                    path.lineTo(cx - size * 0.4f, cy + size * 0.62f);
                    path.close();
                    canvas.drawPath(path, fill);
                }
                return;
            }
            float radius = Math.min(getWidth(), getHeight()) * 0.34f;
            rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
            // Rewind turns back - its head at the top pointing left; forward the other way.
            boolean back = type == FORWARD;
            // An open ring with an arrowhead where it starts, pointing the way the time goes.
            canvas.drawArc(rect, back ? -60 : -120, back ? -300 : 300, false, stroke);
            float angle = (float) Math.toRadians(back ? -60 : -120);
            float ax = cx + radius * (float) Math.cos(angle), ay = cy + radius * (float) Math.sin(angle);
            float head = dp(5);
            path.reset();
            if (back) {
                path.moveTo(ax - head, ay - head * 0.9f);
                path.lineTo(ax, ay);
                path.lineTo(ax - head * 1.2f, ay + head * 0.5f);
            } else {
                path.moveTo(ax + head, ay - head * 0.9f);
                path.lineTo(ax, ay);
                path.lineTo(ax + head * 1.2f, ay + head * 0.5f);
            }
            canvas.drawPath(path, stroke);
            canvas.drawText("10", cx, cy + text.getTextSize() * 0.36f, text);
        }
    }

    /**
     * "Next episode" with a play mark, filling with white from where the text starts while it
     * counts down; the words turn dark as the white passes under them.
     */
    private static final class NextPill extends View {
        private final String text;
        private float progress = 1f;
        private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.text.TextPaint label = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path triangle = new Path();
        private final RectF rect = new RectF();
        private final boolean rtl = LocaleController.isRTL;

        NextPill(Context context, String text) {
            super(context);
            this.text = text;
            back.setColor(0xB3595959);
            fill.setColor(Color.WHITE);
            label.setTextSize(dp(14));
            label.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        }

        void setProgress(float value) {
            progress = value;
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = (int) (dp(16) + dp(11) + dp(10) + label.measureText(text) + dp(18));
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight(), r = dp(6);
            rect.set(0, 0, w, h);
            canvas.drawRoundRect(rect, r, r, back);
            float filled = w * progress;
            float left = rtl ? w - filled : 0, right = rtl ? w : filled;
            canvas.save();
            canvas.clipRect(left, 0, right, h);
            canvas.drawRoundRect(rect, r, r, fill);
            canvas.restore();

            drawContent(canvas, Color.WHITE);
            canvas.save();
            canvas.clipRect(left, 0, right, h);
            drawContent(canvas, Color.BLACK);
            canvas.restore();
        }

        private void drawContent(Canvas canvas, int color) {
            float h = getHeight(), size = dp(11);
            float markStart = rtl ? getWidth() - dp(16) - size : dp(16);
            triangle.reset();
            triangle.moveTo(markStart, h / 2f - size * 0.6f);
            triangle.lineTo(markStart + size, h / 2f);
            triangle.lineTo(markStart, h / 2f + size * 0.6f);
            triangle.close();
            mark.setColor(color);
            canvas.drawPath(triangle, mark);
            label.setColor(color);
            float textWidth = label.measureText(text);
            float textStart = rtl ? markStart - dp(10) - textWidth : markStart + size + dp(10);
            canvas.drawText(text, textStart, h / 2f - (label.descent() + label.ascent()) / 2f, label);
        }
    }

    /** Icon over a label, for the row along the bottom. */
    private static final class BarButton extends LinearLayout {
        private final TextView label;

        BarButton(Context context, int icon, String text) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(6), 0, dp(6), 0);
            setBackground(org.telegram.ui.ActionBar.Theme.createSelectorDrawable(0x33ffffff, 2));
            ImageView image = new ImageView(context);
            image.setImageResource(icon);
            image.setColorFilter(Color.WHITE);
            image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            addView(image, LayoutHelper.createLinear(22, 22));
            label = new TextView(context);
            label.setText(text);
            label.setTextColor(Color.WHITE);
            label.setTextSize(13);
            label.setSingleLine(true);
            label.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
            addView(label, LayoutHelper.createLinear(-2, -2, 6, 0, 0, 0));
        }

        void setLabel(String text) {
            label.setText(text);
        }
    }

    /** The red line: what has played, what has loaded, and a dot to drag. */
    private final class SeekBarView extends View {
        float progress, buffered;
        boolean dragging;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint loaded = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);

        SeekBarView(Context context) {
            super(context);
            track.setColor(0x4DFFFFFF);
            loaded.setColor(0x80FFFFFF);
            played.setColor(NETFLIX_RED);
        }

        void setProgress(float progress, float buffered) {
            this.progress = Math.max(0f, Math.min(1f, progress));
            this.buffered = Math.max(0f, Math.min(1f, buffered));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float left = dp(10), right = getWidth() - dp(10), cy = getHeight() / 2f, h = dp(3);
            float width = right - left;
            canvas.drawRect(left, cy - h / 2, right, cy + h / 2, track);
            canvas.drawRect(left, cy - h / 2, left + width * buffered, cy + h / 2, loaded);
            canvas.drawRect(left, cy - h / 2, left + width * progress, cy + h / 2, played);
            canvas.drawCircle(left + width * progress, cy, dragging ? dp(10) : dp(8), played);
        }

        @SuppressLint("ClickableViewAccessibility")
        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float left = dp(10), width = getWidth() - dp(20);
            float fraction = Math.max(0f, Math.min(1f, (event.getX() - left) / width));
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    AndroidUtilities.cancelRunOnUIThread(hideControls);
                    progress = fraction;
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    progress = fraction;
                    invalidate();
                    tick();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    progress = fraction;
                    if (player != null && player.getDuration() > 0) {
                        player.seekTo((long) (fraction * player.getDuration()));
                    }
                    invalidate();
                    scheduleHide();
                    return true;
            }
            return super.onTouchEvent(event);
        }
    }

    /** A thin bar on the side that was slid on, for as long as the finger is there. */
    private static final class LevelIndicator extends View {
        private boolean left;
        private float level;
        private final Paint back = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint front = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final Runnable hide = () -> animate().alpha(0f).setDuration(200).start();

        LevelIndicator(Context context) {
            super(context);
            back.setColor(0x55FFFFFF);
            front.setColor(Color.WHITE);
            setAlpha(0f);
        }

        void show(boolean left, float level) {
            this.left = left;
            this.level = level;
            animate().cancel();
            setAlpha(1f);
            invalidate();
            AndroidUtilities.cancelRunOnUIThread(hide);
            AndroidUtilities.runOnUIThread(hide, 800);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float barWidth = dp(4), barHeight = getHeight() * 0.4f;
            float x = left ? dp(40) : getWidth() - dp(40) - barWidth;
            float top = (getHeight() - barHeight) / 2f;
            rect.set(x, top, x + barWidth, top + barHeight);
            canvas.drawRoundRect(rect, barWidth, barWidth, back);
            rect.set(x, top + barHeight * (1f - level), x + barWidth, top + barHeight);
            canvas.drawRoundRect(rect, barWidth, barWidth, front);
        }
    }
}
