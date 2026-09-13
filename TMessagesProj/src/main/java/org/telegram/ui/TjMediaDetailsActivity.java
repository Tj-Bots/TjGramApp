package org.telegram.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TjMediaEpisodesView;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Cells.TextCheckCell;

/** Stable full-screen details: indexing never rebuilds or dismisses this screen. */
public final class TjMediaDetailsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final TjMediaLibrary.Entry entry;
    private TjMediaStore.Record record;
    private final Runnable identify;
    private TextView play, download;
    private TextView titleView;
    private TextView overviewView;
    private TextView nextEpisode;
    private BackupImageView artwork;
    private String artworkKey;
    private FrameLayout hero;
    private TjMediaEpisodesView episodes;
    private LinearLayout episodesContainer;
    private final java.util.List<Integer> accounts;
    private final java.util.Map<Long, java.util.Set<Long>> sources;
    private final int sourceType;
    private boolean destroyed;
    private boolean resumed, touching, refreshPending;
    private long lastScrollAt;
    private final Runnable storeChanged = this::scheduleRefresh;
    private final Runnable refreshTask = this::refreshState;

    public TjMediaDetailsActivity(TjMediaLibrary.Entry entry, TjMediaStore.Record record,
            Runnable identify,
            java.util.List<Integer> accounts, java.util.Map<Long, java.util.Set<Long>> sources, int sourceType) {
        this.entry = entry; this.record = record;
        this.identify = identify;
        this.accounts = new java.util.ArrayList<>(accounts); this.sources = TjMediaSources.copy(sources); this.sourceType = sourceType;
        setCurrentAccount(entry.account);
    }

    private static String text(int id) { return TjLocale.getString(id); }
    private static int dp(float value) { return AndroidUtilities.dp(value); }

    @Override public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(text(R.string.TjMediaDetails));
        actionBar.createMenu().addItem(1, R.drawable.ic_ab_other)
                .setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == 1) moreActions();
            }
        });
        ScrollView scroll = new ScrollView(context);
        scroll.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_DOWN) touching = true;
            else if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) touching = false;
            return false;
        });
        scroll.getViewTreeObserver().addOnScrollChangedListener(() -> lastScrollAt = android.os.SystemClock.elapsedRealtime());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, 0, 0, dp(24));
        scroll.addView(body);
        fragmentView = scroll;
        String name = record.title();
        hero = new FrameLayout(context);
        hero.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        BackupImageView image = new BackupImageView(context);
        artwork = image;
        artworkKey = null;
        image.getImageReceiver().setCurrentAccount(entry.account);
        hero.addView(image, LayoutHelper.createFrame(-1, -1));
        bindArtwork();
        View shade = new View(context);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0xdd000000}));
        hero.addView(shade, LayoutHelper.createFrame(-1, -1));
        TextView title = new TextView(context);
        titleView = title;
        title.setText(name); title.setTextColor(0xffffffff); title.setTextSize(28);
        title.setMaxLines(3); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        title.setPadding(dp(20), dp(20), dp(20), dp(20));
        hero.addView(title, LayoutHelper.createFrame(-1, -2, Gravity.BOTTOM));
        body.addView(hero, LayoutHelper.createLinear(-1, heroHeight()));
        TextView technical = paragraph(context, org.telegram.ui.Components.TjMediaFileInfo.summary(entry.message));
        technical.setTextSize(13);
        technical.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        technical.setGravity(Gravity.CENTER);
        technical.setVisibility(technical.length() == 0 ? View.GONE : View.VISIBLE);
        body.addView(technical, LayoutHelper.createLinear(-1, -2));
        play = button(context, "", () -> open(resumable() ? record.position : 0));
        play.setTypeface(AndroidUtilities.bold());
        play.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        play.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_featuredStickers_addButton)));
        body.addView(play, LayoutHelper.createLinear(-1, -2, 16, 16, 16, 8));
        download = button(context, text(R.string.TjMediaDownload), () -> {
            if (!entry.isAccountAvailable() || entry.message.getDocument() == null) return;
            FileLoader loader = FileLoader.getInstance(entry.account);
            if (loader.isLoadingFile(FileLoader.getAttachFileName(entry.message.getDocument()))) loader.cancelLoadFile(entry.message.getDocument());
            else loader.loadFile(entry.message.getDocument(), entry.message, FileLoader.PRIORITY_NORMAL, 0);
            updateActions();
        });
        LinearLayout actions = new LinearLayout(context);
        actions.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        actions.addView(download, new LinearLayout.LayoutParams(0, -1, 1));
        TextView lists = button(context, text(R.string.TjMediaListsTab), this::manageItem);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(0, -1, 1);
        listParams.leftMargin = dp(4); listParams.rightMargin = dp(4);
        actions.addView(lists, listParams);
        body.addView(actions, LayoutHelper.createLinear(-1, -2, 16, 0, 16, 8));
        nextEpisode = button(context, text(R.string.TjMediaNextEpisode), () -> { if (episodes != null) episodes.openNext(); });
        body.addView(nextEpisode, LayoutHelper.createLinear(-1, -2, 16, 0, 16, 8));
        overviewView = paragraph(context, "");
        body.addView(overviewView);
        if (TjMediaTitle.parse(entry.message.getDocumentName(), entry.message.messageOwner.message).episodeConflict)
            body.addView(paragraph(context, text(R.string.TjMediaEpisodeConflict)));
        episodesContainer = new LinearLayout(context);
        episodesContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(episodesContainer, LayoutHelper.createLinear(-1, -2));
        refreshEpisodes();
        updateActions();
        return fragmentView;
    }

    private void moreActions() {
        if (getParentActivity() == null || !entry.isAccountAvailable()) return;
        showDialog(new AlertDialog.Builder(getParentActivity())
                .setItems(new CharSequence[]{text(R.string.TjMediaManageItem), text(R.string.TjMediaEditEpisode),
                        text(R.string.TjMediaLocalIdentify), text(R.string.TjMediaIdentify),
                        text(R.string.TjMediaOriginalCaption), text(R.string.TjMediaFileDetails), text(R.string.TjMediaOpenSource)}, (dialog, which) -> {
                    if (which == 0) manageItem();
                    else if (which == 1) editNumbering();
                    else if (which == 2) editLocalTitle();
                    else if (which == 3) {
                        finishFragment(false);
                        AndroidUtilities.runOnUIThread(identify);
                    } else if (which == 6) open(-1);
                    else {
                        String value = which == 4 ? TjMediaStore.displayCaption(entry.message)
                                : entry.message.getDocumentName() + "\n\n"
                                + org.telegram.ui.Components.TjMediaFileInfo.summary(entry.message) + "\n\n" + sourceName();
                        showDialog(new AlertDialog.Builder(getParentActivity())
                                .setTitle(text(which == 4 ? R.string.TjMediaOriginalCaption : R.string.TjMediaFileDetails))
                                .setMessage(value == null || value.isEmpty() ? text(R.string.TjMediaEmpty) : value)
                                .setPositiveButton(LocaleController.getString(R.string.Close), null).create());
                    }
                }).create());
    }

    private int heroHeight() {
        boolean hasArtwork = false;
        if (!entry.message.hasMediaSpoilers()) {
            hasArtwork = record.metadata != null && TjConfig.hasMediaMetadataCredential(entry.account)
                    && record.metadata.backdropUrl() != null && !record.metadata.backdropUrl().isEmpty();
            hasArtwork |= FileLoader.getClosestPhotoSizeWithSize(entry.message.photoThumbs, 640, false, null, true) != null;
            if (!hasArtwork && entry.message.getDocument() != null)
                hasArtwork = FileLoader.getClosestPhotoSizeWithSize(entry.message.getDocument().thumbs, 640, false, null, true) != null;
        }
        int height = hasArtwork ? Math.min(360, (int) (AndroidUtilities.displaySize.y / AndroidUtilities.density * .42f)) : 176;
        return titleView == null ? height : Math.max(height, (int) (titleView.getLineHeight() * 3 / AndroidUtilities.density) + 40);
    }

    private void bindArtwork() {
        if (artwork == null) return;
        if (hero != null && hero.getLayoutParams() != null) {
            int height = dp(heroHeight());
            if (hero.getLayoutParams().height != height) {
                hero.getLayoutParams().height = height; hero.requestLayout();
            }
        }
        String url = null;
        ImageLocation location = null;
        if (!entry.message.hasMediaSpoilers()) {
            if (record.metadata != null && TjConfig.hasMediaMetadataCredential(entry.account)) {
                url = record.metadata.backdropUrl();
                if (url != null && url.isEmpty()) url = null;
            }
            if (url == null) {
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(entry.message.photoThumbs, 640, false, null, true);
                location = thumb == null ? null : ImageLocation.getForObject(thumb, entry.message.photoThumbsObject);
                if (location == null && entry.message.getDocument() != null) {
                    thumb = FileLoader.getClosestPhotoSizeWithSize(entry.message.getDocument().thumbs, 640, false, null, true);
                    if (thumb != null) location = ImageLocation.getForDocument(thumb, entry.message.getDocument());
                }
            }
        }
        String key = url != null ? url : location == null ? "" : location.getKey(entry.message, null, false);
        if (key != null && key.equals(artworkKey)) return;
        artworkKey = key;
        artwork.getImageReceiver().cancelLoadImage();
        artwork.setImageDrawable(null);
        if (url != null) artwork.setImage(url, "640_360", null);
        else if (location != null) artwork.setImage(location, "640_360", (android.graphics.drawable.Drawable) null, entry.message);
    }

    private String sourceName() {
        long dialog = entry.message.getDialogId();
        MessagesController controller = MessagesController.getInstance(entry.account);
        TLRPC.Chat chat = dialog < 0 ? controller.getChat(-dialog) : null;
        String name = dialog > 0 ? UserObject.getUserName(controller.getUser(dialog)) : chat == null ? Long.toString(dialog) : chat.title;
        return name + " · " + UserObject.getUserName(UserConfig.getInstance(entry.account).getCurrentUser());
    }

    private TextView button(Context context, String name, Runnable action) {
        TextView view = paragraph(context, name);
        view.setMinHeight(dp(48)); view.setGravity(Gravity.CENTER);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        view.setTextIsSelectable(false);
        view.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray), Theme.getColor(Theme.key_listSelector)));
        view.setOnClickListener(v -> { if (entry.isAccountAvailable() && action != null) action.run(); });
        view.setFocusable(true);
        view.setLayoutParams(LayoutHelper.createLinear(-1, -2, 16, 4, 16, 4));
        return view;
    }

    private TextView paragraph(Context context, String value) {
        TextView view = new TextView(context);
        view.setText(value); view.setTextSize(16); view.setTextIsSelectable(true);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        view.setPadding(dp(20), dp(12), dp(20), dp(12));
        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        return view;
    }

    private void expandable(LinearLayout body, Context context, String label, String value) {
        TextView content = paragraph(context, value);
        content.setVisibility(View.GONE);
        body.addView(button(context, label + " ▾", () -> content.setVisibility(content.getVisibility() == View.GONE ? View.VISIBLE : View.GONE)));
        body.addView(content);
    }

    private boolean resumable() {
        return !record.watched && record.duration > 0 && record.position > 0 && record.position < record.duration * .98;
    }

    private void editLocalTitle() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        EditTextBoldCursor name = new EditTextBoldCursor(context);
        name.setSingleLine(true); name.setText(record.title());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setHint(text(R.string.TjMediaLocalTitle));
        org.telegram.ui.Components.TjMediaInputStyle.apply(name, text(R.string.TjMediaLocalTitle));
        name.setPadding(dp(20), dp(12), dp(20), dp(12));
        org.telegram.ui.Components.TjMediaInputStyle.addLabel(body, name);
        body.addView(name, LayoutHelper.createLinear(-1, 56));
        EditTextBoldCursor year = new EditTextBoldCursor(context);
        year.setSingleLine(true); year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        year.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        year.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        year.setHint(text(R.string.TjMediaYearHint));
        org.telegram.ui.Components.TjMediaInputStyle.apply(year, text(R.string.TjMediaYearHint));
        year.setPadding(dp(20), dp(12), dp(20), dp(12));
        int parsedYear = record.localManual || !record.localKey.isEmpty() ? record.localYear
                : TjMediaTitle.parse(entry.message.getDocumentName(), entry.message.messageOwner.message).year;
        year.setText(parsedYear > 0 ? Integer.toString(parsedYear) : "");
        org.telegram.ui.Components.TjMediaInputStyle.addLabel(body, year);
        body.addView(year, LayoutHelper.createLinear(-1, 56));
        TextCheckCell series = new TextCheckCell(context);
        boolean[] isSeries = {record.isSeries()};
        series.setTextAndCheck(text(R.string.TjMediaSeries), isSeries[0], false);
        series.setOnClickListener(v -> { isSeries[0] = !isSeries[0]; series.setChecked(isSeries[0]); });
        body.addView(series);
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaLocalIdentify)).setView(body)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
                    int selectedYear;
                    try { selectedYear = year.length() == 0 ? 0 : Integer.parseInt(year.getText().toString()); }
                    catch (NumberFormatException invalid) { invalidEdit(); return; }
                        TjMediaStore.getInstance().setLocalTitle(entry.message, name.getText().toString(), isSeries[0], selectedYear, saved -> {
                            if (destroyed) return;
                            if (!saved) showDialog(new AlertDialog.Builder(context).setMessage(text(R.string.TjMediaSaveError))
                                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                            else TjMediaStore.getInstance().state(entry.message, state -> {
                                if (!destroyed && state != null) {
                                    record = state;
                                    // Explicit user correction may rebuild this screen; indexing may not.
                                    refreshEpisodes(); updateActions(); bindArtwork();
                                }
                            });
                        });
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void editNumbering() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        EditTextBoldCursor[] fields = new EditTextBoldCursor[2];
        for (int i = 0; i < 2; i++) {
            fields[i] = new EditTextBoldCursor(context);
            fields[i].setSingleLine(true); fields[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            fields[i].setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            fields[i].setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            fields[i].setHint(text(i == 0 ? R.string.TjMediaSeason : R.string.TjMediaEpisode));
            org.telegram.ui.Components.TjMediaInputStyle.apply(fields[i], text(i == 0 ? R.string.TjMediaSeason : R.string.TjMediaEpisode));
            fields[i].setPadding(dp(20), dp(12), dp(20), dp(12));
            int value = i == 0 ? record.season() : record.episode();
            fields[i].setText(value >= 0 ? Integer.toString(value) : "");
            org.telegram.ui.Components.TjMediaInputStyle.addLabel(body, fields[i]);
            body.addView(fields[i], LayoutHelper.createLinear(-1, 56));
        }
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaEditEpisode)).setView(body)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
                    try {
                        int season = fields[0].length() == 0 ? -2 : Integer.parseInt(fields[0].getText().toString());
                        int episode = fields[1].length() == 0 ? -2 : Integer.parseInt(fields[1].getText().toString());
                        TjMediaStore.getInstance().setEpisode(entry.message, season, episode, saved -> {
                            if (destroyed) return;
                            if (saved) TjMediaStore.getInstance().state(entry.message, state -> {
                                if (!destroyed && state != null) { record = state; refreshEpisodes(); updateActions(); }
                            });
                            else invalidEdit();
                        });
                    } catch (NumberFormatException e) { invalidEdit(); }
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void invalidEdit() {
        if (!destroyed && getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity())
                .setMessage(text(R.string.TjMediaSaveError)).setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }

    private void manageItem() {
        Context context = getParentActivity(); if (context == null) return;
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaManageItem))
                .setItems(new CharSequence[]{text(record.favorite ? R.string.TjMediaUnfavorite : R.string.TjMediaFavorite),
                        text(record.watched ? R.string.TjMediaUnwatched : R.string.TjMediaMarkWatched), text(R.string.TjMediaCollection)}, (d, index) -> {
                    if (index == 0) saveFlags(!record.favorite, record.watched, record.collection);
                    else if (index == 1) saveFlags(record.favorite, !record.watched, record.collection);
                    else chooseList();
                }).create());
    }

    private void saveFlags(boolean favorite, boolean watched, String collection) {
        TjMediaStore.getInstance().setFlags(entry.message, favorite, watched, collection, saved -> {
            if (destroyed) return;
            if (!saved) { invalidEdit(); return; }
            record.favorite = favorite; record.watched = watched; record.collection = collection; updateActions();
        });
    }

    private void chooseList() {
        TjMediaStore.getInstance().collections(entry.account, names -> {
            if (destroyed || getParentActivity() == null) return;
            if (names == null) { invalidEdit(); return; }
            java.util.ArrayList<String> choices = new java.util.ArrayList<>(names);
            java.util.ArrayList<CharSequence> labels = new java.util.ArrayList<>(choices);
            labels.add(text(R.string.TjMediaNewCollection));
            labels.add(text(R.string.TjMediaRemoveCollection));
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCollection))
                    .setItems(labels.toArray(new CharSequence[0]), (d, index) -> {
                        if (index < choices.size()) saveFlags(record.favorite, record.watched, choices.get(index));
                        else if (index > choices.size()) saveFlags(record.favorite, record.watched, "");
                        else {
                            presentFragment(new TjMediaCollectionEditActivity(entry.account, null, "",
                                    name -> { if (entry.isAccountAvailable()) saveFlags(record.favorite, record.watched, name); }));
                        }
                    }).create());
        });
    }

    private void refreshEpisodes() {
        if (episodesContainer == null) return;
        if (episodes != null) episodes.close();
        episodes = null;
        episodesContainer.removeAllViews();
        if (record.catalogKey().isEmpty()) {
            if (record.isSeries() || record.season() >= 0 || record.episode() >= 0) {
                episodesContainer.addView(button(episodesContainer.getContext(),
                        text(R.string.TjMediaEpisode) + " " + (record.episode() >= 0 ? record.episode() : "—"),
                        () -> open(resumable() ? record.position : 0)));
                episodesContainer.addView(button(episodesContainer.getContext(), text(R.string.TjMediaLocalIdentify), this::editLocalTitle));
            }
            return;
        }
        episodes = new TjMediaEpisodesView(episodesContainer.getContext(), this, record, accounts, sources, sourceType,
                source -> TjMediaStore.getInstance().state(source.message, state -> {
                    if (!destroyed && source.isAccountAvailable()) open(source,
                            state != null && !state.watched && state.position > 0 && state.duration > 0
                                    && state.position < state.duration * .98 ? state.position : 0);
                }));
        episodesContainer.addView(episodes, LayoutHelper.createLinear(-1, -2));
    }

    private void updateActions() {
        if (play == null) return;
        if (titleView != null) titleView.setText(record.title());
        if (overviewView != null) {
            String overview = record.metadata == null ? "" : record.metadata.overview;
            overviewView.setText(overview);
            overviewView.setVisibility(overview.isEmpty() ? View.GONE : View.VISIBLE);
        }
        play.setText("▶  " + text(resumable() ? R.string.TjMediaContinue : R.string.TjMediaPlay));
        if (record.isSeries() && record.season() >= 0 && record.episode() >= 0)
            play.append(" · " + text(R.string.TjMediaSeason) + " " + record.season() + " · " + text(R.string.TjMediaEpisode) + " " + record.episode());
        if (nextEpisode != null) nextEpisode.setVisibility(record.isSeries() && record.season() >= 0
                && record.episode() >= 0 && !record.catalogKey().isEmpty() ? View.VISIBLE : View.GONE);
        boolean local = FileLoader.getInstance(entry.account).getPathToMessage(entry.message.messageOwner).exists();
        boolean loading = FileLoader.getInstance(entry.account).isLoadingFile(FileLoader.getAttachFileName(entry.message.getDocument()));
        download.setText(local ? text(R.string.TjMediaOnDevice) : loading
                ? text(R.string.TjMediaDownloading) + " · " + LocaleController.getString(R.string.Cancel) : text(R.string.TjMediaDownload));
        download.setEnabled(!local && entry.message.getDocument() != null);
    }

    private void open(long position) {
        open(entry, position);
    }

    private void open(TjMediaLibrary.Entry source, long position) {
        if (!source.isAccountAvailable()) return;
        if (position >= 0) {
            org.telegram.ui.Components.TjMediaPlayback.open(this, source, position);
            return;
        }
        Bundle args = new Bundle();
        long dialog = source.message.getDialogId();
        if (dialog > 0) args.putLong("user_id", dialog); else args.putLong("chat_id", -dialog);
        args.putInt("message_id", source.message.getId());
        if (position >= 0) args.putInt("video_timestamp", (int) Math.min(Integer.MAX_VALUE, position / 1000));
        if (!MessagesController.getInstance(source.account).checkCanOpenChat(args, this)) return;
        ChatActivity chat = new ChatActivity(args); chat.setCurrentAccount(source.account); presentFragment(chat);
    }

    @Override public void onResume() {
        super.onResume();
        resumed = true;
        if (!entry.isAccountAvailable()) { finishFragment(); return; }
        scheduleRefresh();
    }

    private void scheduleRefresh() {
        if (!resumed || destroyed || refreshPending) return;
        refreshPending = true;
        AndroidUtilities.runOnUIThread(refreshTask, 1000);
    }

    private boolean interactionActive() {
        return touching || android.os.SystemClock.elapsedRealtime() - lastScrollAt < 500
                || getVisibleDialog() != null && getVisibleDialog().isShowing();
    }

    private void refreshState() {
        refreshPending = false;
        if (!resumed || destroyed || !entry.isAccountAvailable()) return;
        if (interactionActive()) { scheduleRefresh(); return; }
        TjMediaStore.getInstance().state(entry.message, updated -> {
            if (resumed && !destroyed && updated != null && entry.isAccountAvailable()) {
                if (interactionActive()) { scheduleRefresh(); return; }
                boolean regroup = record.season() != updated.season() || record.episode() != updated.episode()
                        || !record.catalogKey().equals(updated.catalogKey());
                record = updated; updateActions(); bindArtwork();
                if (regroup) refreshEpisodes();
                else if (episodes != null) episodes.refreshCurrentPage(() -> resumed && !destroyed && !interactionActive(), this::scheduleRefresh);
            }
        });
    }

    @Override public void onPause() {
        resumed = false; touching = false; refreshPending = false;
        AndroidUtilities.cancelRunOnUIThread(refreshTask);
        super.onPause();
    }

    @Override public boolean onFragmentCreate() {
        TjMediaStore.getInstance().addListener(storeChanged);
        NotificationCenter center = NotificationCenter.getInstance(entry.account);
        center.addObserver(this, NotificationCenter.fileLoaded);
        center.addObserver(this, NotificationCenter.fileLoadFailed);
        center.addObserver(this, NotificationCenter.fileLoadProgressChanged);
        return super.onFragmentCreate();
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (!destroyed && entry.isAccountAvailable() && args.length > 0
                && FileLoader.getAttachFileName(entry.message.getDocument()).equals(args[0])) updateActions();
    }

    @Override public void onFragmentDestroy() {
        destroyed = true; if (episodes != null) episodes.close();
        AndroidUtilities.cancelRunOnUIThread(refreshTask);
        TjMediaStore.getInstance().removeListener(storeChanged);
        NotificationCenter center = NotificationCenter.getInstance(entry.account);
        center.removeObserver(this, NotificationCenter.fileLoaded);
        center.removeObserver(this, NotificationCenter.fileLoadFailed);
        center.removeObserver(this, NotificationCenter.fileLoadProgressChanged);
        super.onFragmentDestroy();
    }
}
