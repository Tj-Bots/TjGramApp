package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.messenger.tj.TjMediaPageKey;
import org.telegram.messenger.tj.TjMediaMetadata;
import org.telegram.messenger.tj.TjMediaTitle;
import org.telegram.messenger.tj.TjMediaCatalog;
import org.telegram.messenger.tj.TjMediaAutoMatcher;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.TjMediaHomeView;
import org.telegram.ui.Components.glass.GlassTabView;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.BlurredBackgroundDrawable;
import org.telegram.ui.Components.blur3.drawable.color.impl.BlurredBackgroundProviderImpl;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TjMediaCardCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** General-media entry point; movie recognition must never hide ordinary files. */
public class TjMediaCenterActivity extends BaseFragment implements MainTabsActivity.TabFragmentDelegate {
    private Runnable mainTabBackAction;
    public void setMainTabBackAction(Runnable action) { mainTabBackAction = action; }

    @Override public void onParentScrollToTop() {
        if (libraryView == 9 && home != null) home.smoothScrollTo(0, 0);
        else if (grid != null) grid.smoothScrollToPosition(0);
    }
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ArrayList<TjMediaLibrary.Entry> visible = new ArrayList<>();
    private final ArrayList<String> renderedRows = new ArrayList<>();
    private boolean renderedHomeLoading;
    private TjMediaLibrary library;
    private TjMediaLibrary scanner;
    private TjMediaAutoMatcher autoMatcher;
    private final Runnable scanChanged = this::onScanChanged;
    private Adapter adapter;
    private EditTextBoldCursor search;
    private TextView status;
    private TextView accountButton;
    private TextView typeButton;
    private TextView sourceButton;
    private final HashMap<Long, Set<Long>> sources = new HashMap<>();
    private final HashMap<Long, Set<Long>> explicitSources = new HashMap<>();
    private final HashMap<Long, Set<Long>> sourceFolders = new HashMap<>();
    private int sourceType;
    private int accountMode;
    private boolean preferencesLoaded;
    private long screenOwner;
    private TextView viewButton;
    private int libraryView = 0;
    private int enabledTypes = org.telegram.messenger.tj.TjMediaKind.ALL_MASK;
    private MainTabsLayout mainTabs;
    private LinearLayout typeTabs;
    private android.widget.HorizontalScrollView typeStrip;
    private TjMediaHomeView home;
    private RecyclerListView grid;
    private final Runnable homeRefresh = this::refreshHome;
    private final Runnable searchAction = this::reload;
    private boolean pendingStoreUpdate;
    private boolean viewResumed, refreshScheduled, pendingVisualRefresh;
    private boolean automaticPage, jumpToPageStart;
    private final HashMap<Integer, TjMediaPageKey> displayedBoundaries = new HashMap<>();
    private boolean displayedBackwards;
    private org.telegram.messenger.tj.TjMediaCatalogPageKey displayedCatalogBoundary;
    private final Runnable automaticRefresh = this::applyAutomaticRefresh;
    private final Runnable storeChanged = () -> { pendingStoreUpdate = true; scheduleAutomaticRefresh(); };
    private String collection = "";
    private int collectionAccount = -1;
    private boolean gridTouching;
    private int localGeneration;
    private int localPending;
    private boolean localError;
    private boolean localBackwards;
    private org.telegram.messenger.tj.TjMediaCatalogPageKey catalogNext, catalogPrevious;
    private boolean catalogMore = true, catalogHasPrevious;
    private final HashMap<Integer, TjMediaPageKey> localCursors = new HashMap<>();
    private final HashMap<Integer, TjMediaPageKey> localPrevious = new HashMap<>(), localEnds = new HashMap<>();
    private boolean showRemotePreview = true, discoveryRequested;
    private TextView previousButton;
    private TextView scanStatus;
    private final HashMap<String, TjMediaStore.Record> localRecords = new HashMap<>();
    private final ArrayList<TjMediaLibrary.Entry> localEntries = new ArrayList<>();
    private final TjMediaMetadata metadataClient = new TjMediaMetadata();
    private TjMediaCatalog<TjMediaLibrary.Entry> catalog = new TjMediaCatalog<>();
    private int mediaType;
    private String searchQuery = "";
    private String pendingSettingsLink;

    public TjMediaCenterActivity() { }

    public TjMediaCenterActivity(Bundle args) {
        super(args);
        pendingSettingsLink = args == null ? null : args.getString("tj_settings_link");
    }

    public static TjMediaCenterActivity forSettingsLink(boolean lists) {
        Bundle args = new Bundle();
        args.putString("tj_settings_link", lists ? "lists" : "metadata");
        return new TjMediaCenterActivity(args);
    }

    @Override public void onBecomeFullyVisible() {
        super.onBecomeFullyVisible();
        if (pendingSettingsLink == null || getParentActivity() == null
                || !UserConfig.getInstance(currentAccount).isClientActivated()
                || screenOwner != UserConfig.getInstance(currentAccount).getClientUserId()) return;
        String destination = pendingSettingsLink;
        pendingSettingsLink = null;
        if (arguments != null) arguments.remove("tj_settings_link");
        if ("lists".equals(destination)) openCollections();
        else if ("metadata".equals(destination)) configureMetadata(currentAccount);
    }

    @Override
    public View createView(Context context) {
        if (screenOwner == 0) screenOwner = UserConfig.getInstance(currentAccount).getClientUserId();
        AndroidUtilities.cancelRunOnUIThread(searchAction);
        if (library != null) library.close();
        actionBar.setTitle(text(R.string.TjMediaCenter));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.createMenu().addItem(1, R.drawable.msg_settings).setContentDescription(LocaleController.getString(R.string.Settings));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) {
                if (id == -1) {
                    if (mainTabBackAction != null) mainTabBackAction.run();
                    else finishFragment();
                }
                else if (id == 1) mediaSettings();
                else if (id == 3) chooseFilters();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        if (mainTabBackAction != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
                androidx.core.graphics.Insets system = AndroidUtilities.getDefaultWindowInsets(insets, false);
                root.setPadding(system.left, system.top + ActionBar.getCurrentActionBarHeight(), system.right,
                        system.bottom);
                return insets;
            });
        }
        org.telegram.ui.ActionBar.ActionBarMenuItem searchItem = actionBar.createMenu().addItem(2, R.drawable.msg_search)
                .setIsSearchField(true);
        searchItem.setSearchFieldHint(text(R.string.TjMediaSearch));
        search = searchItem.getSearchField();
        search.setText(searchQuery);
        actionBar.createMenu().addItem(3, R.drawable.menu_tag_filter).setContentDescription(text(R.string.TjMediaFilters));
        accountButton = label(context);
        accountButton.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        accountButton.setOnClickListener(v -> chooseAccounts());
        LinearLayout scopeRow = new LinearLayout(context);
        scopeRow.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        scopeRow.addView(accountButton, new LinearLayout.LayoutParams(0, -2, 1));
        scanStatus = label(context);
        scanStatus.setTextSize(12); scanStatus.setMaxWidth(AndroidUtilities.dp(170));
        scanStatus.setMaxLines(2); scanStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scanStatus.setVisibility(View.GONE);
        scanStatus.setOnClickListener(v -> {
            if (autoMatcher != null) { stopMatching(); refresh(); }
            else if (scanner != null && scanner.hasError() && !scanner.isLoading()) scanner.loadMore();
            else { stopScan(); refresh(); }
        });
        scopeRow.addView(scanStatus, LayoutHelper.createLinear(-2, -2));
        root.addView(scopeRow, LayoutHelper.createLinear(-1, -2));
        mainTabs = new MainTabsLayout(context, getResourceProvider());
        mainTabs.setClipChildren(false);
        mainTabs.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        mainTabs.setPadding(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4),
                AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4), AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN + 4));
        mainTabs.setMaxWidth(AndroidUtilities.dp(328 + DialogsActivity.MAIN_TABS_MARGIN * 2));
        for (int page : new int[]{0, 9, 3}) {
            int label = page == 0 ? R.string.TjMediaLibraryTab : page == 9 ? R.string.TjMediaWatchTab : R.string.TjMediaListsTab;
            GlassTabView.TabAnimation icon = page == 0
                    ? GlassTabView.TabAnimation.MEDIA_LIBRARY : page == 9
                    ? GlassTabView.TabAnimation.MEDIA_WATCH
                    : GlassTabView.TabAnimation.MEDIA_LISTS;
            GlassTabView tab = GlassTabView.createMainTab(context, getResourceProvider(), icon, label);
            tab.setTag(page);
            tab.setText(text(label));
            tab.setOnClickListener(v -> {
                if (page == 3) { openCollections(); return; }
                libraryView = page; mediaType = 0; reload();
            });
            mainTabs.addView(tab);
            mainTabs.setViewVisible(tab, true, false);
        }
        // Use the host navigation's themed fallback; this bar has no content beneath it to blur.
        BlurredBackgroundSourceColor tabsSource = new BlurredBackgroundSourceColor();
        tabsSource.setColor(getThemedColor(Theme.key_windowBackgroundWhite));
        BlurredBackgroundDrawable tabsBackground = new BlurredBackgroundDrawableViewFactory(tabsSource)
                .create(mainTabs, BlurredBackgroundProviderImpl.mainTabs(getResourceProvider()));
        tabsBackground.setRadius(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT / 2f));
        tabsBackground.setPadding(AndroidUtilities.dp(DialogsActivity.MAIN_TABS_MARGIN - 0.334f));
        mainTabs.setBackground(tabsBackground);
        typeTabs = new LinearLayout(context);
        typeTabs.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        typeStrip = new android.widget.HorizontalScrollView(context);
        typeStrip.setHorizontalScrollBarEnabled(false);
        typeStrip.addView(typeTabs);
        root.addView(typeStrip, LayoutHelper.createLinear(-1, -2));
        // These labels are also used in the filter sheet, not permanent toolbar rows.
        typeButton = label(context);
        sourceButton = label(context);
        viewButton = label(context);
        RecyclerListView list = new RecyclerListView(context) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) gridTouching = true;
                else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) gridTouching = false;
                return super.dispatchTouchEvent(event);
            }
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (getLayoutManager() instanceof GridLayoutManager && w > 0) {
                    float cardWidth = 180 * Math.max(1f, getResources().getConfiguration().fontScale);
                    ((GridLayoutManager) getLayoutManager()).setSpanCount(libraryView == 7 || libraryView == 8 ? Math.max(1,
                            (int) ((w - getPaddingLeft() - getPaddingRight()) / AndroidUtilities.density / cardWidth)) : 1);
                }
            }
        };
        grid = list;
        list.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(12));
        list.setClipToPadding(false);
        list.setLayoutManager(new GridLayoutManager(context, Math.max(1,
                (int) (AndroidUtilities.displaySize.x / AndroidUtilities.density / 180))));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((view, position) -> {
            if (position >= 0 && position < visible.size()) showDetails(visible.get(position));
        });
        list.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= visible.size()) return false;
            editItem(visible.get(position));
            return true;
        });
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        home = new TjMediaHomeView(context, new TjMediaHomeView.Delegate() {
            @Override public void open(TjMediaLibrary.Entry entry) { showDetails(entry); }
            @Override public void view(int view) { libraryView = view; reload(); }
        });
        root.addView(home, new LinearLayout.LayoutParams(-1, 0, 1));
        status = label(context);
        status.setMinHeight(AndroidUtilities.dp(40));
        status.setTextSize(13);
        status.setOnClickListener(v -> {
            if (libraryView == 9 && localError && localPending == 0) {
                refreshHome();
                refresh();
                return;
            }
            if (libraryView != 0 && libraryView != 9) {
                if (localPending == 0) loadLocal(localError && localBackwards);
            } else if (libraryView == 0 && !localCursors.isEmpty()) {
                loadLocal(localError && localBackwards);
            } else if (library != null && !library.isLoading()) {
                discoveryRequested = libraryView == 0;
                library.loadMore();
                refresh();
            }
        });
        LinearLayout pages = new LinearLayout(context);
        pages.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        pages.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        previousButton = label(context);
        previousButton.setText(text(R.string.TjMediaNewerPage));
        previousButton.setOnClickListener(v -> loadLocal(true));
        previousButton.setVisibility(View.GONE);
        pages.addView(previousButton, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(pages, LayoutHelper.createLinear(-1, -2));
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(DialogsActivity.MAIN_TABS_HEIGHT_WITH_MARGINS));
        bottomParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(mainTabs, bottomParams);
        loadPreferences();
        TjMediaStore.getInstance().addListener(storeChanged);
        org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().addListener(scanChanged);
        scanner = org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().scanner();
        library = new TjMediaLibrary(() -> {
            if (discoveryRequested && !library.isLoading()) {
                discoveryRequested = false;
                localCursors.clear(); localCursors.putAll(localEnds);
                if (localCursors.isEmpty()) for (int account : accounts) localCursors.put(account, null);
                loadLocal();
            }
            refresh();
            if (libraryView == 9 && visible.isEmpty() && localPending == 0) {
                AndroidUtilities.cancelRunOnUIThread(homeRefresh);
                AndroidUtilities.runOnUIThread(homeRefresh, 200);
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                AndroidUtilities.cancelRunOnUIThread(searchAction);
                AndroidUtilities.runOnUIThread(searchAction, 350);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        reload();
        return root;
    }

    private static String text(int id) { return TjLocale.getString(id); }

    private static String libraryError(TjMediaLibrary library) {
        if (library.hasWriteError()) return text(R.string.TjMediaSaveError);
        String code = library.errorCode();
        return code == null ? text(R.string.TjMediaRetry) : text(R.string.TjMediaNetworkError) + " · " + code;
    }

    private void scheduleAutomaticRefresh() {
        if (!viewResumed || refreshScheduled || fragmentView == null) return;
        refreshScheduled = true;
        AndroidUtilities.runOnUIThread(automaticRefresh, 750);
    }

    private boolean interactionInProgress() {
        return !viewResumed || gridTouching || getVisibleDialog() != null && getVisibleDialog().isShowing()
                || grid != null && (grid.getScrollState() != RecyclerView.SCROLL_STATE_IDLE || grid.isComputingLayout())
                || libraryView == 9 && home != null && home.isInteracting();
    }

    private void applyAutomaticRefresh() {
        refreshScheduled = false;
        if (!viewResumed || library == null) return;
        if (interactionInProgress() || localPending > 0) { scheduleAutomaticRefresh(); return; }
        if (pendingVisualRefresh) { pendingVisualRefresh = false; refresh(); }
        if (!pendingStoreUpdate) return;
        pendingStoreUpdate = false;
        automaticPage = true;
        if (libraryView == 9) refreshHome();
        else loadLocal(displayedBackwards);
    }

    private void onScanChanged() {
        scanner = org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().scanner();
        if (scanStatus == null) return;
        // Scan activity alone is not evidence that displayed content changed.
        scanStatus.setVisibility(scanner != null || org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().isRequested() ? View.VISIBLE : View.GONE);
        scanStatus.setText(scanner == null ? text(R.string.TjMediaWatchingFolders)
                : scanner.hasError() && !scanner.isLoading() ? libraryError(scanner)
                : String.format(java.util.Locale.getDefault(), text(R.string.TjMediaScanProgress), scanner.scannedCount()));
        scanStatus.setEnabled(true);
    }

    private TextView label(Context context) {
        TextView view = new TextView(context);
        view.setTextSize(14);
        view.setMinHeight(AndroidUtilities.dp(48));
        view.setGravity(Gravity.CENTER);
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        view.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        view.setBackground(Theme.getSelectorDrawable(false));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (host.isClickable()) info.setClassName(android.widget.Button.class.getName());
            }
        });
        return view;
    }

    private CharSequence[] typeNames() {
        return new CharSequence[]{text(R.string.TjMediaAll), text(R.string.TjMediaPhotos),
                text(R.string.TjMediaVideos), text(R.string.TjMediaFiles),
                text(R.string.TjMediaMusic), text(R.string.TjMediaVoice), text(R.string.TjMediaGifs)};
    }

    private int typeSelection() {
        return mediaType == 0 ? -enabledTypes : mediaType;
    }

    private void chooseFilters() {
        showDialog(new org.telegram.ui.ActionBar.BottomSheet.Builder(getParentActivity())
                .setTitle(text(R.string.TjMediaFilters))
                .setItems(new CharSequence[]{text(R.string.TjMediaAccounts), text(R.string.TjMediaSources),
                        text(R.string.TjMediaType), text(R.string.TjMediaView)}, (dialog, which) -> {
                    if (which == 0) chooseAccounts();
                    else if (which == 1) chooseSources();
                    else if (which == 2) chooseMediaTypes();
                    else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaView))
                            .setItems(viewNames(), (d, view) -> {
                                if (view == 5) chooseCollection(new ArrayList<>(accounts), null,
                                        value -> { collection = value; collectionAccount = -1; libraryView = 5; reload(); });
                                else { libraryView = view; reload(); }
                            }).create());
                }).create());
    }

    private void chooseMediaTypes() {
        ArrayList<String> names = new ArrayList<>();
        CharSequence[] labels = typeNames();
        boolean[] selected = new boolean[labels.length - 1];
        for (int i = 1; i < labels.length; i++) {
            names.add(labels[i].toString()); selected[i - 1] = (enabledTypes & (1 << i)) != 0;
        }
        chooseMany(text(R.string.TjMediaType), names, selected, () -> {
            int mask = 0;
            for (int i = 0; i < selected.length; i++) if (selected[i]) mask |= 1 << (i + 1);
            if (mask == 0) {
                showDialog(new AlertDialog.Builder(getParentActivity())
                        .setMessage(text(R.string.TjMediaSelectType))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                return;
            }
            enabledTypes = mask;
            if ((enabledTypes & (1 << mediaType)) == 0) mediaType = 0;
            savePreferences(); reload();
        });
    }

    private void bindTabs() {
        int page = libraryView == 9 || libraryView == 7 || libraryView == 8 ? 9
                : libraryView == 0 || libraryView == 6 ? 0 : 3;
        for (int i = 0; i < mainTabs.getChildCount(); i++) {
            GlassTabView tab = (GlassTabView) mainTabs.getChildAt(i);
            boolean selected = ((Integer) tab.getTag()) == page;
            tab.setSelected(selected, true);
        }
        typeStrip.setVisibility(page == 0 ? View.VISIBLE : View.GONE);
        typeTabs.removeAllViews();
        CharSequence[] names = typeNames();
        for (int type : new int[]{0, 2, 1, 3, 4, 5, 6}) {
            if (type != 0 && (enabledTypes & (1 << type)) == 0) continue;
            TextView tab = label(getContext()); tab.setText(names[type]); tab.setSelected(mediaType == type);
            tab.setTextSize(14);
            tab.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            tab.setMinHeight(AndroidUtilities.dp(44));
            tab.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(18),
                    Theme.getColor(mediaType == type ? Theme.key_windowBackgroundGray : Theme.key_windowBackgroundWhite), Theme.getColor(Theme.key_listSelector)));
            tab.setTextColor(Theme.getColor(mediaType == type ? Theme.key_windowBackgroundWhiteBlueText : Theme.key_windowBackgroundWhiteGrayText));
            tab.setOnClickListener(v -> { mediaType = type; reload(); });
            typeTabs.addView(tab, LayoutHelper.createLinear(-2, -2));
        }
    }

    private void reload() {
        metadataClient.cancel();
        stopMatching();
        if (screenOwner != UserConfig.getInstance(currentAccount).getClientUserId()) {
            if (library != null) library.close();
            finishFragment();
            return;
        }
        preferencesLoaded = false;
        accounts.clear();
        sources.clear();
        explicitSources.clear();
        sourceFolders.clear();
        loadPreferences();
        bindTabs();
        AndroidUtilities.cancelRunOnUIThread(homeRefresh);
        AndroidUtilities.cancelRunOnUIThread(automaticRefresh);
        refreshScheduled = false; pendingStoreUpdate = false; pendingVisualRefresh = false;
        automaticPage = false; jumpToPageStart = true;
        displayedBoundaries.clear(); displayedBackwards = false; displayedCatalogBoundary = null;
        localGeneration++;
        localPending = 0;
        localError = false;
        localBackwards = false;
        catalogNext = catalogPrevious = null;
        catalogMore = true; catalogHasPrevious = false;
        localCursors.clear();
        localPrevious.clear(); localEnds.clear();
        showRemotePreview = true; discoveryRequested = false;
        localEntries.clear();
        localRecords.clear();
        if (library == null) return;
        grid.setVisibility(libraryView == 9 ? View.GONE : View.VISIBLE);
        home.setVisibility(libraryView == 9 ? View.VISIBLE : View.GONE);
        ((GridLayoutManager) grid.getLayoutManager()).setSpanCount(libraryView == 7 || libraryView == 8
                ? Math.max(1, (int) (AndroidUtilities.displaySize.x / AndroidUtilities.density / 180)) : 1);
        if (libraryView == 0 || libraryView == 9) {
            library.reset(accounts, sources, sourceType, search.getText().toString());
            if (libraryView == 9) refreshHome();
            else {
                for (int account : accounts) localCursors.put(account, null);
                loadLocal();
            }
        }
        else {
            library.close();
            for (int account : accounts) if (libraryView != 5 || collectionAccount < 0 || collectionAccount == account) localCursors.put(account, null);
            loadLocal();
        }
    }

    private CharSequence[] viewNames() {
        return new CharSequence[]{text(R.string.TjMediaBrowse), text(R.string.TjMediaContinue), text(R.string.TjMediaHistory),
                text(R.string.TjMediaFavorites), text(R.string.TjMediaWatched), text(R.string.TjMediaCollection), text(R.string.TjMediaIndexed),
                text(R.string.TjMediaMovies), text(R.string.TjMediaSeries), text(R.string.TjMediaHome)};
    }

    private void refreshHome() {
        if (libraryView != 9 || library == null) return;
        if (localPending > 0) {
            AndroidUtilities.runOnUIThread(homeRefresh, 200);
            return;
        }
        localCursors.clear();
        automaticPage = false;
        ArrayList<TjMediaLibrary.Entry> nextEntries = new ArrayList<>();
        HashMap<String, TjMediaStore.Record> nextRecords = new HashMap<>();
        int generation = localGeneration;
        localPending = accounts.size() * 5;
        localError = false;
        for (int account : accounts) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            for (int mode : new int[]{0, 1, 3, 7, 8}) TjMediaStore.getInstance().loadPreview(account, mode,
                    search.getText().toString(), sources.get(owner), sourceType,
                    (enabledTypes & (1 << org.telegram.messenger.tj.TjMediaKind.VIDEO)) == 0 ? -enabledTypes : org.telegram.messenger.tj.TjMediaKind.VIDEO,
                    Math.max(1, 120 / Math.max(1, accounts.size() * 5)), records -> {
                        if (generation != localGeneration) return;
                        localPending--;
                        if (records == null) localError = true;
                        else for (TjMediaStore.Record record : records) {
                            TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, owner, record.message);
                            if (nextRecords.put(entry.key, record) == null) nextEntries.add(entry);
                        }
                        if (localPending == 0) {
                            if (!localError || !nextEntries.isEmpty()) {
                                nextEntries.sort((a, b) -> Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date));
                                localEntries.clear();
                                localEntries.addAll(nextEntries);
                                localRecords.clear();
                                localRecords.putAll(nextRecords);
                            }
                            refresh();
                        }
                    });
        }
        if (accounts.isEmpty()) refresh();
    }

    private void loadLocal() { loadLocal(false); }

    private void loadLocal(boolean backwards) {
        if (localPending > 0) return;
        localBackwards = backwards;
        if (libraryView == 7 || libraryView == 8) { loadCatalog(backwards); return; }
        int generation = localGeneration;
        final boolean automatic = automaticPage;
        automaticPage = false;
        HashMap<Integer, TjMediaPageKey> boundaries = new HashMap<>(automatic ? displayedBoundaries : backwards ? localPrevious : localCursors);
        if (boundaries.isEmpty()) { refresh(); return; }
        localError = false;
        localPending = boundaries.size();
        ArrayList<TjMediaLibrary.Entry> nextEntries = new ArrayList<>();
        HashMap<String, TjMediaStore.Record> nextRecords = new HashMap<>();
        HashMap<Integer, TjMediaPageKey> older = new HashMap<>(), newer = new HashMap<>(), ends = new HashMap<>();
        HashMap<Integer, TjMediaStore.Page> pages = new HashMap<>();
        boolean firstPage = !backwards && boundaries.values().stream().allMatch(java.util.Objects::isNull);
        for (int account : boundaries.keySet()) {
            TjMediaPageKey after = boundaries.get(account);
            long owner = UserConfig.getInstance(account).getClientUserId();
            TjMediaStore.getInstance().loadWindow(account, libraryView == 6 || libraryView == 9 ? 0 : libraryView,
                    search.getText().toString(), collection, after, sources.get(owner), sourceType, typeSelection(),
                    Math.max(1, 400 / boundaries.size()), records -> {
                        if (generation != localGeneration) return;
                        localPending--;
                        if (records == null) localError = true;
                        else {
                            pages.put(account, records);
                            for (TjMediaStore.Record record : records) {
                                TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, owner, record.message);
                                if (nextRecords.put(entry.key, record) == null) nextEntries.add(entry);
                            }
                        }
                        if (localPending == 0 && !localError) {
                            org.telegram.messenger.tj.TjMediaPageMerge merge = new org.telegram.messenger.tj.TjMediaPageMerge(backwards);
                            for (TjMediaStore.Page page : pages.values()) {
                                merge.add(backwards ? page.previousKey : page.nextKey, backwards ? page.hasPrevious : page.hasMore);
                            }
                            boolean moreInDirection = merge.hasMore();
                            TjMediaPageKey edge = merge.edge();
                            nextEntries.removeIf(entry -> !merge.includes(entryBoundary(entry, nextRecords)));
                            nextEntries.sort((a, b) -> TjMediaPageKey.compare(entryBoundary(a, nextRecords), entryBoundary(b, nextRecords)));
                            // Only consume the shared chronological prefix. Unshown candidates
                            // are queried again, rather than silently skipped by per-owner cursors.
                            TjMediaPageKey first = nextEntries.isEmpty() ? edge : entryBoundary(nextEntries.get(0), nextRecords);
                            TjMediaPageKey last = nextEntries.isEmpty() ? edge : entryBoundary(nextEntries.get(nextEntries.size() - 1), nextRecords);
                            for (int selectedAccount : boundaries.keySet()) {
                                long selectedOwner = UserConfig.getInstance(selectedAccount).getClientUserId();
                                TjMediaPageKey oldEdge = backwards ? last : edge;
                                TjMediaPageKey newEdge = backwards ? edge : first;
                                if (oldEdge != null) {
                                    TjMediaPageKey key = TjMediaPageKey.forAccount(selectedOwner, oldEdge, false);
                                    ends.put(selectedAccount, key);
                                    if (backwards || moreInDirection) older.put(selectedAccount, key);
                                }
                                if (newEdge != null && (backwards ? moreInDirection : !firstPage))
                                    newer.put(selectedAccount, TjMediaPageKey.forAccount(selectedOwner, newEdge, true));
                            }
                            HashSet<String> retained = new HashSet<>();
                            for (TjMediaLibrary.Entry entry : nextEntries) retained.add(entry.key);
                            nextRecords.keySet().retainAll(retained);
                            localEntries.clear(); localEntries.addAll(nextEntries);
                            localRecords.clear(); localRecords.putAll(nextRecords);
                            localCursors.clear(); localCursors.putAll(older);
                            localPrevious.clear(); localPrevious.putAll(newer);
                            localEnds.clear(); localEnds.putAll(ends);
                            displayedBoundaries.clear(); displayedBoundaries.putAll(boundaries);
                            displayedBackwards = backwards;
                            showRemotePreview = firstPage;
                            jumpToPageStart = !automatic;
                        }
                        refresh();
                    });
        }
        refresh();
    }

    private void loadCatalog(boolean backwards) {
        final boolean automatic = automaticPage;
        automaticPage = false;
        if (!automatic && (backwards ? !catalogHasPrevious : !catalogMore)) { refresh(); return; }
        final org.telegram.messenger.tj.TjMediaCatalogPageKey boundary = automatic ? displayedCatalogBoundary : backwards ? catalogPrevious : catalogNext;
        int generation = localGeneration;
        localPending = 1; localError = false;
        TjMediaStore.getInstance().loadCatalog(new ArrayList<>(accounts), libraryView == 8,
                search.getText().toString(), boundary, sources, sourceType, typeSelection(), records -> {
                    if (generation != localGeneration) return;
                    localPending = 0;
                    localError = records == null;
                    if (records != null) {
                        localEntries.clear(); localRecords.clear();
                        for (TjMediaStore.Record record : records) {
                            int account = record.message.currentAccount;
                            TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, UserConfig.getInstance(account).getClientUserId(), record.message);
                            localEntries.add(entry); localRecords.put(entry.key, record);
                        }
                        catalogNext = records.nextKey; catalogPrevious = records.previousKey;
                        catalogMore = records.hasMore; catalogHasPrevious = records.hasPrevious;
                        displayedCatalogBoundary = boundary; displayedBackwards = backwards;
                        showRemotePreview = false; jumpToPageStart = !automatic;
                    }
                    refresh();
                });
        refresh();
    }

    private TjMediaPageKey entryBoundary(TjMediaLibrary.Entry entry, HashMap<String, TjMediaStore.Record> records) {
        boolean byPlayed = libraryView == 1 || libraryView == 2;
        TjMediaStore.Record record = records.get(entry.key);
        return new TjMediaPageKey(entry.ownerId, byPlayed,
                byPlayed && record != null ? record.playedAt : entry.message.messageOwner.date,
                entry.message.getDialogId(), entry.message.getId());
    }

    private void refresh() {
        if (adapter == null || library == null) return;
        if (interactionInProgress()) {
            pendingVisualRefresh = true;
            scheduleAutomaticRefresh();
            return;
        }
        GridLayoutManager layout = (GridLayoutManager) grid.getLayoutManager();
        int anchorPosition = layout.findFirstVisibleItemPosition();
        String anchor = anchorPosition >= 0 && anchorPosition < visible.size() ? visible.get(anchorPosition).key : null;
        View anchorView = anchorPosition < 0 ? null : layout.findViewByPosition(anchorPosition);
        int anchorOffset = anchorView == null ? 0 : anchorView.getTop() - grid.getPaddingTop();
        ArrayList<String> oldKeys = new ArrayList<>();
        for (TjMediaLibrary.Entry entry : visible) oldKeys.add(entry.key);
        ArrayList<String> oldRows = new ArrayList<>(renderedRows);
        visible.clear();
        ArrayList<TjMediaLibrary.Entry> candidates = new ArrayList<>(localEntries);
        if (libraryView == 0 && showRemotePreview) {
            HashSet<String> keys = new HashSet<>();
            for (TjMediaLibrary.Entry entry : candidates) keys.add(entry.key);
            for (TjMediaLibrary.Entry entry : library.snapshot()) if (keys.add(entry.key)) candidates.add(entry);
            candidates.sort((a, b) -> Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date));
        }
        for (TjMediaLibrary.Entry entry : candidates) {
            if (!entry.isAccountAvailable()) continue;
            MessageObject m = entry.message;
            long did = m.getDialogId();
            Set<Long> selected = sources.get(entry.ownerId);
            if (selected != null && !selected.isEmpty() && !selected.contains(did)) continue;
            TLRPC.Chat chat = did < 0 ? MessagesController.getInstance(entry.account).getChat(-did) : null;
            boolean channel = chat != null && ChatObject.isChannel(chat) && !chat.megagroup;
            TjMediaStore.Record cached = localRecords.get(entry.key);
            if (cached != null && cached.sourceType != 0) {
                if (sourceType != 0 && sourceType != cached.sourceType) continue;
            } else if (sourceType == 1 && did < 0 || sourceType == 2 && (did > 0 || channel)
                    || sourceType == 3 && !channel) continue;
            if (org.telegram.messenger.tj.TjMediaKind.matches(typeSelection(), org.telegram.messenger.tj.TjMediaKind.of(m))) visible.add(entry);
        }
        catalog = new TjMediaCatalog<>();
        if (libraryView == 7 || libraryView == 8) {
            for (TjMediaLibrary.Entry entry : visible) {
                TjMediaStore.Record record = localRecords.get(entry.key);
                if (record == null || record.catalogKey().isEmpty()) continue;
                catalog.add(record.catalogKey(), record.isSeries(),
                        new TjMediaCatalog.Source<>(entry.key, entry, record.season(), record.episode()));
            }
            visible.clear();
            for (TjMediaCatalog.Group<TjMediaLibrary.Entry> group : catalog.groups()) visible.add(group.sources.get(0).value);
        }
        ArrayList<String> newKeys = new ArrayList<>();
        ArrayList<String> newRows = new ArrayList<>();
        for (TjMediaLibrary.Entry entry : visible) {
            newKeys.add(entry.key);
            TjMediaStore.Record record = localRecords.get(entry.key);
            MessageObject message = entry.message;
            long dialog = message.getDialogId();
            MessagesController controller = MessagesController.getInstance(entry.account);
            TLRPC.Chat chat = dialog < 0 ? controller.getChat(-dialog) : null;
            String source = dialog > 0 ? UserObject.getUserName(controller.getUser(dialog)) : chat == null ? "" : chat.title;
            newRows.add(libraryView + "|" + entry.key + "|" + message.messageOwner.edit_date + "|" + message.getDocumentName()
                    + "|" + TjMediaStore.displayCaption(message) + "|" + source + "|" + message.hasMediaSpoilers()
                    + "|" + (message.getDocument() == null ? "" : message.getDocument().id + ":" + message.getDocument().size)
                    + "|" + (record == null ? "" : record.title() + ":" + record.position + ":" + record.duration
                    + ":" + record.favorite + ":" + record.watched + ":" + record.collection
                    + ":" + (record.metadata == null ? "" : record.metadata.backdropUrl())));
        }
        boolean rowsChanged = !newKeys.equals(oldKeys) || !newRows.equals(oldRows);
        if (rowsChanged) {
            renderedRows.clear(); renderedRows.addAll(newRows);
            androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
                @Override public int getOldListSize() { return oldKeys.size(); }
                @Override public int getNewListSize() { return newKeys.size(); }
                @Override public boolean areItemsTheSame(int oldPosition, int newPosition) { return oldKeys.get(oldPosition).equals(newKeys.get(newPosition)); }
                @Override public boolean areContentsTheSame(int oldPosition, int newPosition) {
                    return oldPosition < oldRows.size() && oldRows.get(oldPosition).equals(newRows.get(newPosition));
                }
            }, false).dispatchUpdatesTo(adapter);
        }
        if (jumpToPageStart) { layout.scrollToPositionWithOffset(0, 0); jumpToPageStart = false; }
        else if (anchor != null) {
            for (int i = 0; i < visible.size(); i++) if (anchor.equals(visible.get(i).key)) {
                layout.scrollToPositionWithOffset(i, anchorOffset); break;
            }
        }
        boolean homeLoading = library.isLoading() || localPending > 0;
        if (libraryView == 9 && home != null && (rowsChanged || homeLoading != renderedHomeLoading))
            home.bind(visible, localRecords, homeLoading);
        renderedHomeLoading = homeLoading;
        accountButton.setText((accounts.size() == 1
                ? UserObject.getUserName(UserConfig.getInstance(accounts.get(0)).getCurrentUser())
                : text(R.string.TjMediaAccounts) + " · " + accounts.size()) + " ▾");
        typeButton.setText(typeNames()[mediaType]);
        viewButton.setText(viewNames()[libraryView] + (libraryView == 5 ? " · " + collection : ""));
        int selectedSources = 0;
        for (Set<Long> selected : sources.values()) for (long id : selected) if (id != 0) selectedSources++;
        int folderCount = 0;
        for (Set<Long> selected : sourceFolders.values()) folderCount += selected.size();
        sourceButton.setText(folderCount > 0 ? text(R.string.TjMediaSelectFolders) + " · " + folderCount
                : !sources.isEmpty() ? text(R.string.TjMediaSelectChats) + " · " + selectedSources : sourceNames()[sourceType]);
        boolean loading = libraryView == 0 || libraryView == 9 ? library.isLoading() || localPending > 0 : localPending > 0;
        boolean error = libraryView == 0 || libraryView == 9 ? library.hasError() || localError : localError;
        boolean more = libraryView == 0 || libraryView == 9 ? library.hasMore() || !localCursors.isEmpty() : !localCursors.isEmpty();
        boolean titlePage = libraryView == 7 || libraryView == 8;
        if (titlePage) more = catalogMore;
        previousButton.setVisibility(libraryView != 9 && (titlePage ? catalogHasPrevious : !localPrevious.isEmpty()) ? View.VISIBLE : View.GONE);
        previousButton.setEnabled(localPending == 0);
        status.setText(text(loading ? R.string.TjMediaLoading
                : error ? localError ? R.string.TjMediaLookupError : R.string.TjMediaRetry
                : more ? R.string.TjMediaMore
                : visible.isEmpty() ? R.string.TjMediaEmpty : R.string.TjMediaEnd));
        status.setEnabled(!loading && (more || error));
        status.setVisibility(!loading && !error && !more && !visible.isEmpty() ? View.GONE : View.VISIBLE);
        if (error && !localError && !loading) status.setText(libraryError(library));
        if (!search.getText().toString().trim().isEmpty() && !TjMediaStore.getInstance().isSearchReady()) {
            status.setText(status.getText() + " · " + text(R.string.TjMediaSearchUpdating));
        }
        if ((libraryView == 7 || libraryView == 8 || libraryView == 9) && !TjMediaStore.getInstance().isLocalCatalogReady())
            status.setText(status.getText() + " · " + text(R.string.TjMediaLocalCatalogUpdating));
        scanStatus.setVisibility(scanner != null || autoMatcher != null || org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().isRequested() ? View.VISIBLE : View.GONE);
        if (scanner != null) {
            scanStatus.setText(scanner.hasError() && !scanner.isLoading() ? libraryError(scanner)
                    : String.format(java.util.Locale.getDefault(), text(R.string.TjMediaScanProgress), scanner.scannedCount()));
            scanStatus.setEnabled(true);
        } else if (org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().isRequested()) {
            scanStatus.setText(text(R.string.TjMediaWatchingFolders));
        }
        if (autoMatcher != null) updateMatchStatus();
    }

    private void chooseAccounts() {
        if (getParentActivity() == null) return;
        ArrayList<Integer> active = org.telegram.messenger.tj.TjAccountOrder.activeAccounts();
        ArrayList<String> names = new ArrayList<>();
        for (int i : active) {
            names.add(UserObject.getUserName(UserConfig.getInstance(i).getCurrentUser()));
        }
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaAccounts))
                .setItems(new CharSequence[]{text(R.string.TjMediaCurrentAccount), text(R.string.TjMediaAllAccounts),
                        text(R.string.TjMediaSelectAccounts)}, (dialog, which) -> {
                    if (which < 2) {
                        accountMode = which;
                        accounts.clear();
                        if (which == 0) accounts.add(currentAccount);
                        else accounts.addAll(active);
                        savePreferences();
                        reload();
                    } else {
                        boolean[] selected = new boolean[active.size()];
                        for (int i = 0; i < selected.length; i++) selected[i] = accounts.contains(active.get(i));
                        chooseMany(text(R.string.TjMediaAccounts), names, selected, () -> {
                            accounts.clear();
                            for (int i = 0; i < selected.length; i++) if (selected[i]) accounts.add(active.get(i));
                            if (accounts.isEmpty()) accounts.add(currentAccount);
                            accountMode = 2;
                            savePreferences();
                            reload();
                        });
                    }
                }).show();
    }

    private CharSequence[] sourceNames() {
        return new CharSequence[]{text(R.string.TjMediaAllSources), text(R.string.TjMediaPrivate),
                text(R.string.TjMediaGroups), text(R.string.TjMediaChannels)};
    }

    private void chooseSources() {
        CharSequence[] options = new CharSequence[]{sourceNames()[0], sourceNames()[1], sourceNames()[2],
                sourceNames()[3], text(R.string.TjMediaSelectChats), text(R.string.TjMediaSelectFolders)};
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaSources))
                .setItems(options, (dialog, which) -> {
                    if (which < 4) {
                        sourceType = which;
                        sources.clear();
                        explicitSources.clear(); sourceFolders.clear();
                        savePreferences();
                        reload();
                    } else {
                        ArrayList<String> names = new ArrayList<>();
                        for (int account : accounts) names.add(UserObject.getUserName(UserConfig.getInstance(account).getCurrentUser()));
                        if (accounts.size() == 1) chooseSourceAccount(accounts.get(0), which == 5);
                        else new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaAccounts))
                                .setItems(names.toArray(new CharSequence[0]), (d, index) -> chooseSourceAccount(accounts.get(index), which == 5)).show();
                    }
                }).show();
    }

    private void chooseChats(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        long owner = UserConfig.getInstance(account).getClientUserId();
        Set<Long> current = explicitSources.get(owner);
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            long id = controller.dialogs_dict.keyAt(i);
            if (id == 0 || DialogObject.isEncryptedDialog(id)) continue;
            TLRPC.Chat chat = id < 0 ? controller.getChat(-id) : null;
            ids.add(id);
            names.add(id > 0 ? UserObject.getUserName(controller.getUser(id)) : chat == null ? Long.toString(id) : chat.title);
        }
        // Preserve selected peers not currently present in the loaded dialog list.
        if (current != null) for (long id : current) if (id != 0 && !ids.contains(id)) {
            ids.add(id);
            names.add(Long.toString(id));
        }
        boolean[] selected = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) selected[i] = current != null && current.contains(ids.get(i));
        chooseMany(text(R.string.TjMediaSelectChats), names, selected, () -> {
            HashSet<Long> chosen = new HashSet<>();
            for (int i = 0; i < selected.length; i++) if (selected[i]) chosen.add(ids.get(i));
            // An explicit empty set uses a sentinel, never silently broadens to all chats.
            if (chosen.isEmpty()) chosen.add(0L);
            ensureExplicitScope();
            explicitSources.put(owner, chosen);
            sourceType = 0;
            savePreferences();
            reload();
        });
    }

    private void chooseSourceAccount(int account, boolean folders) {
        if (folders) chooseFolders(account); else chooseChats(account);
    }

    private void ensureExplicitScope() {
        for (int account : accounts) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            if (!explicitSources.containsKey(owner)) {
                HashSet<Long> empty = new HashSet<>(); empty.add(0L);
                explicitSources.put(owner, empty);
            }
        }
    }

    private void chooseFolders(int account) {
        long owner = UserConfig.getInstance(account).getClientUserId();
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (MessagesController.DialogFilter folder : MessagesController.getInstance(account).dialogFilters) {
            if (folder.isDefault()) continue;
            ids.add((long) folder.id); names.add(folder.name);
        }
        Set<Long> current = sourceFolders.get(owner);
        // Keep unavailable selections visible and removable without changing their IDs.
        if (current != null) for (long id : current) if (!ids.contains(id)) {
            ids.add(id); names.add(text(R.string.TjMediaUnavailableFolder) + " · " + id);
        }
        boolean[] selected = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) selected[i] = current != null && current.contains(ids.get(i));
        chooseMany(text(R.string.TjMediaSelectFolders), names, selected, () -> {
            HashSet<Long> chosen = new HashSet<>();
            for (int i = 0; i < selected.length; i++) if (selected[i]) chosen.add(ids.get(i));
            ensureExplicitScope();
            sourceFolders.put(owner, chosen);
            sourceType = 0;
            savePreferences(); reload();
        });
    }

    private void chooseMany(String title, ArrayList<String> names, boolean[] selected, Runnable apply) {
        Context context = getParentActivity();
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        EditTextBoldCursor filter = new EditTextBoldCursor(context);
        filter.setSingleLine(true);
        filter.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        filter.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        filter.setHint(LocaleController.getString(R.string.Search));
        filter.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        body.addView(filter, LayoutHelper.createLinear(-1, 48));
        ArrayList<Integer> positions = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) positions.add(i);
        RecyclerListView list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        RecyclerListView.SelectionAdapter choices = new RecyclerListView.SelectionAdapter() {
            @Override public int getItemCount() { return positions.size(); }
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                return new RecyclerListView.Holder(new TextCheckCell(parent.getContext()));
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                int index = positions.get(position);
                ((TextCheckCell) holder.itemView).setTextAndCheck(names.get(index), selected[index], true);
            }
        };
        list.setAdapter(choices);
        list.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= positions.size()) return;
            int index = positions.get(position);
            selected[index] = !selected[index];
            choices.notifyItemChanged(position);
        });
        body.addView(list, LayoutHelper.createLinear(-1, Math.min(320, (int) (AndroidUtilities.displaySize.y / AndroidUtilities.density * .4f))));
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                positions.clear();
                for (int i = 0; i < names.size(); i++) if (org.telegram.messenger.tj.TjMediaTitle.matches(names.get(i), "", s.toString())) positions.add(i);
                choices.notifyDataSetChanged();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        TextView all = label(context);
        all.setText(text(R.string.TjMediaSelectAll));
        all.setOnClickListener(v -> {
            boolean allChecked = true;
            for (int index : positions) if (!selected[index]) allChecked = false;
            for (int index : positions) selected[index] = !allChecked;
            choices.notifyDataSetChanged();
        });
        body.addView(all);
        showDialog(new AlertDialog.Builder(context).setTitle(title).setView(body)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> apply.run())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void loadPreferences() {
        if (preferencesLoaded) return;
        preferencesLoaded = true;
        SharedPreferences preferences = TjConfig.mediaLibrary(currentAccount);
        enabledTypes = preferences == null ? org.telegram.messenger.tj.TjMediaKind.ALL_MASK
                : preferences.getInt("enabled_media_types", org.telegram.messenger.tj.TjMediaKind.ALL_MASK)
                    & org.telegram.messenger.tj.TjMediaKind.ALL_MASK;
        if (enabledTypes == 0) enabledTypes = org.telegram.messenger.tj.TjMediaKind.ALL_MASK;
        accountMode = preferences == null ? 0 : preferences.getInt("accounts_mode", 0);
        Set<String> owners = preferences == null ? new HashSet<>() : preferences.getStringSet("accounts", new HashSet<>());
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            UserConfig user = UserConfig.getInstance(i);
            if (!user.isClientActivated()) continue;
            long owner = user.getClientUserId();
            if (accountMode == 1 || accountMode == 2 && owners.contains(Long.toString(owner))) accounts.add(i);
            Set<String> saved = preferences == null ? new HashSet<>() : preferences.getStringSet("sources_" + owner, new HashSet<>());
            if (!saved.isEmpty()) {
                HashSet<Long> peers = new HashSet<>();
                for (String id : saved) try { peers.add(Long.parseLong(id)); } catch (NumberFormatException ignored) { }
                explicitSources.put(owner, peers);
            }
            Set<String> folderIds = preferences == null ? new HashSet<>()
                    : preferences.getStringSet("source_folders_" + owner, new HashSet<>());
            HashSet<Long> folders = new HashSet<>();
            for (String id : folderIds) try { folders.add(Long.parseLong(id)); } catch (NumberFormatException ignored) { }
            if (!folders.isEmpty()) sourceFolders.put(owner, folders);
        }
        if (accounts.isEmpty() && UserConfig.getInstance(currentAccount).isClientActivated()) accounts.add(currentAccount);
        sources.putAll(org.telegram.messenger.tj.TjMediaSources.resolve(accounts, explicitSources, sourceFolders));
        sourceType = preferences == null ? 0 : Math.max(0, Math.min(3, preferences.getInt("source_type", 0)));
    }

    private void savePreferences() {
        SharedPreferences preferences = TjConfig.mediaLibrary(currentAccount);
        if (preferences == null) return;
        HashSet<String> owners = new HashSet<>();
        for (int account : accounts) owners.add(Long.toString(UserConfig.getInstance(account).getClientUserId()));
        SharedPreferences.Editor editor = preferences.edit().putInt("accounts_mode", accountMode)
                .putInt("enabled_media_types", enabledTypes)
                .putStringSet("accounts", owners).putInt("source_type", sourceType);
        for (String key : preferences.getAll().keySet()) if (key.startsWith("sources_") || key.startsWith("source_folders_")) editor.remove(key);
        for (java.util.Map.Entry<Long, Set<Long>> source : explicitSources.entrySet()) {
            HashSet<String> peers = new HashSet<>();
            for (long id : source.getValue()) peers.add(Long.toString(id));
            editor.putStringSet("sources_" + source.getKey(), peers);
        }
        for (java.util.Map.Entry<Long, Set<Long>> folder : sourceFolders.entrySet()) {
            HashSet<String> ids = new HashSet<>();
            for (long id : folder.getValue()) ids.add(Long.toString(id));
            editor.putStringSet("source_folders_" + folder.getKey(), ids);
        }
        editor.apply();
    }

    private void openSource(TjMediaLibrary.Entry entry) {
        openSource(entry, -1);
    }

    private void openSource(TjMediaLibrary.Entry entry, long startPosition) {
        if (!entry.isAccountAvailable()) { refresh(); return; }
        Bundle args = new Bundle();
        long dialog = entry.message.getDialogId();
        if (dialog > 0) args.putLong("user_id", dialog);
        else args.putLong("chat_id", -dialog);
        args.putInt("message_id", entry.message.getId());
        if (startPosition >= 0)
            args.putInt("video_timestamp", (int) Math.min(Integer.MAX_VALUE, startPosition / 1000));
        if (!MessagesController.getInstance(entry.account).checkCanOpenChat(args, this)) return;
        ChatActivity chat = new ChatActivity(args);
        chat.setCurrentAccount(entry.account);
        presentFragment(chat);
    }

    private void editItem(TjMediaLibrary.Entry entry) {
        int generation = localGeneration;
        TjMediaStore.getInstance().state(entry.message, record -> {
            if (generation != localGeneration || getParentActivity() == null) return;
            if (record == null) { showStateError(entry, () -> editItem(entry)); return; }
            new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCenter))
                    .setItems(new CharSequence[]{text(R.string.TjMediaOpenSource),
                            text(record.favorite ? R.string.TjMediaUnfavorite : R.string.TjMediaFavorite),
                            text(record.watched ? R.string.TjMediaUnwatched : R.string.TjMediaMarkWatched),
                            text(R.string.TjMediaCollection)}, (dialog, which) -> {
                        if (which == 0) { openSource(entry); return; }
                        if (which == 1) record.favorite = !record.favorite;
                        if (which == 2) record.watched = !record.watched;
                        if (which == 3) chooseCollection(java.util.Collections.singletonList(entry.account), record.collection,
                                value -> { record.collection = value; saveRecord(record); });
                        else saveRecord(record);
                    }).show();
        });
    }

    private void chooseCollection(java.util.List<Integer> selectedAccounts, String current,
                                  TjMediaStore.Callback<String> callback) {
        if (selectedAccounts.isEmpty() || getParentActivity() == null) return;
        int generation = localGeneration;
        java.util.TreeSet<String> names = new java.util.TreeSet<>();
        int[] pending = {selectedAccounts.size()};
        boolean[] failed = {false};
        for (int account : selectedAccounts) TjMediaStore.getInstance().collections(account, result -> {
            if (generation != localGeneration || getParentActivity() == null) return;
            if (result == null) failed[0] = true; else names.addAll(result);
            if (--pending[0] != 0) return;
            if (failed[0]) {
                showDialog(new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.TjMediaLookupError))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                return;
            }
            ArrayList<String> values = new ArrayList<>(names);
            ArrayList<CharSequence> labels = new ArrayList<>(values);
            if (current != null) {
                labels.add(text(R.string.TjMediaNewCollection));
                if (!current.isEmpty()) labels.add(text(R.string.TjMediaRemoveCollection));
            }
            if (labels.isEmpty()) {
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCollection))
                        .setMessage(text(R.string.TjMediaNoCollections))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                return;
            }
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCollection))
                    .setItems(labels.toArray(new CharSequence[0]), (dialog, which) -> {
                        if (which < values.size()) callback.run(values.get(which));
                        else if (which == values.size()) editCollection("", callback);
                        else callback.run("");
                    }).create());
        });
    }

    private void editCollection(String initial, TjMediaStore.Callback<String> callback) {
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setText(initial);
        input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCollection))
                .setMessage(text(R.string.TjMediaCollectionHint)).setView(input)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> callback.run(input.getText().toString().trim()))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void saveRecord(TjMediaStore.Record record) {
        TjMediaStore.getInstance().setFlags(record.message, record.favorite, record.watched, record.collection, success -> {
            if (getParentActivity() == null) return;
            if (success) { if (libraryView != 0) reload(); }
            else new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.TjMediaSaveError))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
        });
    }

    private void showStateError(TjMediaLibrary.Entry entry, Runnable retry) {
        if (getParentActivity() == null || !entry.isAccountAvailable()) return;
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCenter))
                .setMessage(text(R.string.TjMediaStateError))
                .setPositiveButton(LocaleController.getString(R.string.Retry), (d, w) -> retry.run())
                .setNeutralButton(text(R.string.TjMediaOpenSource), (d, w) -> openSource(entry))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void showDetails(TjMediaLibrary.Entry entry) {
        int generation = localGeneration;
        TjMediaStore.getInstance().index(entry.message, entry.storeRevision, success -> {
            if (generation != localGeneration || getParentActivity() == null) return;
            if (!success) { showStateError(entry, () -> showDetails(entry)); return; }
            showIndexedDetails(entry);
        });
    }

    private void showIndexedDetails(TjMediaLibrary.Entry entry) {
        int generation = localGeneration;
        TjMediaStore.getInstance().state(entry.message, record -> {
            if (generation != localGeneration || getParentActivity() == null) return;
            if (record == null) { showStateError(entry, () -> showDetails(entry)); return; }
            localRecords.put(entry.key, record);
            if (org.telegram.messenger.tj.TjMediaKind.of(entry.message) == org.telegram.messenger.tj.TjMediaKind.VIDEO
                    && (record.isSeries() || record.metadata != null || !record.localKey.isEmpty() && !record.localUncertain)) {
                presentFragment(new TjMediaDetailsActivity(entry, record,
                        () -> identify(entry), accounts, sources, sourceType));
                return;
            }
            openOrdinaryMedia(entry);
        });
    }

    private void openCollections() {
        presentFragment(new TjMediaCollectionsActivity(currentAccount, accounts, (account, name) -> {
            if (!accounts.contains(account)) return;
            collection = name; collectionAccount = account; libraryView = 5; mediaType = 0; reload();
        }));
    }

    private void openOrdinaryMedia(TjMediaLibrary.Entry entry) {
        if (!entry.isAccountAvailable() || getParentActivity() == null) return;
        MessageObject message = entry.message;
        int kind = org.telegram.messenger.tj.TjMediaKind.of(message);
        if (kind == org.telegram.messenger.tj.TjMediaKind.PHOTO || kind == org.telegram.messenger.tj.TjMediaKind.GIF
                || kind == org.telegram.messenger.tj.TjMediaKind.VIDEO) {
            // PhotoViewer is bound to the active Telegram account. Cross-account media
            // must enter its own chat instead of requesting files under the wrong owner.
            if (entry.account != UserConfig.selectedAccount) { openSource(entry, 0); return; }
            PhotoViewer viewer = PhotoViewer.getInstance();
            viewer.setParentActivity(this);
            if (viewer.openPhoto(message, message.getDialogId(), 0, 0, new PhotoViewer.EmptyPhotoViewerProvider(), false)) return;
        } else if (message.isMusic() || message.isVoice()) {
            if (org.telegram.messenger.MediaController.getInstance().playMessage(message)) return;
        } else {
            java.io.File file = org.telegram.messenger.FileLoader.getInstance(entry.account).getPathToMessage(message.messageOwner);
            if (file.exists() && AndroidUtilities.openForView(message, getParentActivity(), getResourceProvider(), true)) return;
        }
        openSource(entry);
    }

    private void configureMetadata(int account) {
        Context context = getParentActivity();
        if (context == null) return;
        long owner = UserConfig.getInstance(account).getClientUserId();
        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint(text(R.string.TjMediaKeyHint));
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4096)});
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        if (android.os.Build.VERSION.SDK_INT >= 26) input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaMetadata))
                .setMessage(text(R.string.TjMediaMetadataInfo) + "\n\n" + text(R.string.TjMediaAttribution)).setView(input)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    input.setText("");
                    Utilities.globalQueue.postRunnable(() -> {
                        boolean success = UserConfig.getInstance(account).getClientUserId() == owner
                                && TjConfig.setMediaMetadataCredential(account, owner, value);
                        AndroidUtilities.runOnUIThread(() -> {
                            if (getParentActivity() == null) return;
                            if (success) { metadataClient.cancel(); refresh(); }
                            new AlertDialog.Builder(getParentActivity()).setMessage(text(success ? R.string.TjMediaMetadataSaved : R.string.TjMediaSaveError))
                                    .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
                        });
                    });
                }).setNeutralButton(text(R.string.TjMediaGetKey), (dialog, which) ->
                        org.telegram.messenger.browser.Browser.openUrl(getParentActivity(), "https://www.themoviedb.org/settings/api"))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void mediaSettings() {
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaCenter))
                .setItems(new CharSequence[]{text(R.string.TjMediaMetadata), text(R.string.TjMediaRefresh),
                        text(R.string.TjMediaClearIndex), text(R.string.TjMediaAbout),
                        text(scanner == null ? R.string.TjMediaScan : R.string.TjMediaStopScan),
                        text(autoMatcher == null ? R.string.TjMediaAutoMatch : R.string.TjMediaStopMatching)}, (dialog, which) -> {
                    if (which == 0) configureMetadata(currentAccount);
                    else if (which == 1) reload();
                    else if (which == 3) showMediaAbout();
                    else if (which == 4) {
                        if (scanner != null) { stopScan(); refresh(); }
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaScan))
                                .setMessage(text(R.string.TjMediaScanInfo))
                                .setPositiveButton(text(R.string.TjMediaScan), (d, w) -> startScan())
                                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
                    }
                    else if (which == 5) {
                        if (autoMatcher != null) { stopMatching(); refresh(); }
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaAutoMatch))
                                .setMessage(text(R.string.TjMediaAutoMatchInfo))
                                .setPositiveButton(text(R.string.TjMediaAutoMatch), (d, w) -> startMatching())
                                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
                    }
                    else new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaClearIndex))
                            .setMessage(text(R.string.TjMediaClearIndexInfo))
                            .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                                stopScan();
                                stopMatching();
                                localGeneration++;
                                AndroidUtilities.cancelRunOnUIThread(homeRefresh);
                                library.close();
                                TjMediaStore.getInstance().clear(currentAccount, success -> {
                                    if (getParentActivity() == null) return;
                                    if (success) {
                                        libraryView = 6;
                                        localPending = 0;
                                        reload();
                                    } else new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.TjMediaSaveError))
                                            .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
                                });
                            }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).show();
                }).show();
    }

    private void stopMatching() {
        if (autoMatcher != null) { autoMatcher.cancel(); autoMatcher = null; }
    }

    private void updateMatchStatus() {
        if (autoMatcher == null || scanStatus == null) return;
        scanStatus.setVisibility(View.VISIBLE);
        scanStatus.setText(String.format(java.util.Locale.getDefault(), text(R.string.TjMediaAutoMatchProgress), autoMatcher.checked(), autoMatcher.matched()));
        scanStatus.setEnabled(true);
    }

    private String metadataLanguage() {
        String language = LocaleController.getInstance().getCurrentLocaleInfo().shortName;
        return (language.equals("iw") ? "he" : language).replace('_', '-');
    }

    private void startMatching() {
        libraryView = 9;
        reload();
        library.close();
        autoMatcher = new TjMediaAutoMatcher(new TjMediaAutoMatcher.Listener() {
            @Override public void changed() {
                updateMatchStatus();
                pendingStoreUpdate = true;
                scheduleAutomaticRefresh();
            }
            @Override public void finished(int error) {
                if (autoMatcher == null) return;
                long matched = autoMatcher.matched();
                autoMatcher = null;
                pendingStoreUpdate = true;
                scheduleAutomaticRefresh();
                if (getParentActivity() == null) return;
                String message = error == TjMediaMetadata.OK
                        ? String.format(java.util.Locale.getDefault(), text(R.string.TjMediaAutoMatchComplete), matched)
                        : text(error == TjMediaAutoMatcher.STORAGE ? R.string.TjMediaSaveError
                        : error == TjMediaMetadata.CREDENTIAL ? R.string.TjMediaKeyError
                        : error == TjMediaMetadata.RATE_LIMIT ? R.string.TjMediaRateLimit : R.string.TjMediaLookupError);
                scanStatus.setVisibility(View.VISIBLE);
                scanStatus.setText(message);
                scanStatus.setEnabled(false);
            }
        });
        autoMatcher.start(new ArrayList<>(accounts), new HashMap<>(sources), sourceType, metadataLanguage());
    }

    private void stopScan() {
        org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().stop();
        scanner = null;
    }

    private void startScan() {
        org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance()
                .start(new ArrayList<>(accounts), explicitSources, sourceFolders, sourceType);
    }

    private void chooseGroupSources(TjMediaCatalog.Group<TjMediaLibrary.Entry> group) {
        if (!group.series) { chooseVersions(group.sources); return; }
        java.util.Map<Integer, java.util.Map<Integer, java.util.List<TjMediaCatalog.Source<TjMediaLibrary.Entry>>>> seasons = group.seasons();
        ArrayList<Integer> numbers = new ArrayList<>(seasons.keySet());
        ArrayList<CharSequence> names = new ArrayList<>();
        for (int number : numbers) names.add(number < 0 ? text(R.string.TjMediaUnknownEpisode) : text(R.string.TjMediaSeason) + " " + number);
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaSeasons))
                .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                    java.util.Map<Integer, java.util.List<TjMediaCatalog.Source<TjMediaLibrary.Entry>>> episodes = seasons.get(numbers.get(which));
                    ArrayList<Integer> episodeNumbers = new ArrayList<>(episodes.keySet());
                    ArrayList<CharSequence> episodeNames = new ArrayList<>();
                    for (int number : episodeNumbers) {
                        String progress = episodeProgress(episodes.get(number));
                        episodeNames.add((number < 0 ? text(R.string.TjMediaUnknownEpisode)
                                : text(R.string.TjMediaEpisode) + " " + number) + " · " + episodes.get(number).size()
                                + (progress.isEmpty() ? "" : " · " + progress));
                    }
                    new AlertDialog.Builder(getParentActivity()).setTitle(names.get(which))
                            .setItems(episodeNames.toArray(new CharSequence[0]), (d, index) -> chooseVersions(episodes.get(episodeNumbers.get(index)))).show();
                }).show();
    }

    private void loadGroupSources(TjMediaMetadata.Title title) {
        loadGroupSources(title, -1, -1);
    }

    private void loadGroupSources(TjMediaMetadata.Title title, int afterSeason, int afterEpisode) {
        int generation = localGeneration;
        boolean[] cancelled = {false};
        int[] pending = {accounts.size()};
        boolean[] failed = {false};
        TjMediaCatalog<TjMediaLibrary.Entry> all = new TjMediaCatalog<>();
        AlertDialog loading = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        loading.setOnCancelListener(d -> cancelled[0] = true);
        showDialog(loading);
        Runnable complete = () -> {
            if (--pending[0] > 0) return;
            loading.dismiss();
            if (cancelled[0] || generation != localGeneration || getParentActivity() == null) return;
            TjMediaCatalog.Group<TjMediaLibrary.Entry> group = all.get(title.id, title.series);
            if (failed[0] || group == null) new AlertDialog.Builder(getParentActivity())
                    .setMessage(text(failed[0] ? R.string.TjMediaLookupError : R.string.TjMediaEmpty))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
            else if (afterSeason >= 0 && afterEpisode >= 0) {
                java.util.List<TjMediaCatalog.Source<TjMediaLibrary.Entry>> next = group.nextEpisode(afterSeason, afterEpisode);
                if (next.isEmpty()) showDialog(new AlertDialog.Builder(getParentActivity())
                        .setMessage(text(R.string.TjMediaNoNextEpisode))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                else chooseVersions(next);
            } else chooseGroupSources(group);
        };
        for (int account : new ArrayList<>(accounts)) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            class PageLoader {
                void load(TjMediaPageKey after) {
                    TjMediaStore.getInstance().loadTitle(account, title.id, title.series, after, sources.get(owner), sourceType, page -> {
                        if (cancelled[0] || generation != localGeneration) { loading.dismiss(); return; }
                        if (page == null) { failed[0] = true; complete.run(); return; }
                        for (TjMediaStore.Record item : page) {
                            TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, owner, item.message);
                            if (!entry.isAccountAvailable()) continue;
                            localRecords.put(entry.key, item);
                            all.add(title.id, title.series, new TjMediaCatalog.Source<>(entry.key, entry, item.season(), item.episode()));
                        }
                        if (page.hasMore) load(page.nextKey);
                        else complete.run();
                    });
                }
            }
            new PageLoader().load(null);
        }
        if (accounts.isEmpty()) { pending[0] = 1; complete.run(); }
    }

    private void chooseVersions(java.util.List<TjMediaCatalog.Source<TjMediaLibrary.Entry>> variants) {
        ArrayList<CharSequence> names = new ArrayList<>();
        for (TjMediaCatalog.Source<TjMediaLibrary.Entry> source : variants) {
            TjMediaLibrary.Entry entry = source.value;
            String filename = entry.message.getDocumentName();
            TjMediaTitle hint = TjMediaTitle.parse(filename, entry.message.messageOwner.message);
            long did = entry.message.getDialogId();
            MessagesController controller = MessagesController.getInstance(entry.account);
            TLRPC.Chat chat = did < 0 ? controller.getChat(-did) : null;
            String chatName = did > 0 ? UserObject.getUserName(controller.getUser(did)) : chat == null ? Long.toString(did) : chat.title;
            String progress = episodeProgress(java.util.Collections.singletonList(source));
            names.add((filename == null || filename.isEmpty() ? text(R.string.TjMediaVideos) : filename) + "\n"
                    + UserObject.getUserName(UserConfig.getInstance(entry.account).getCurrentUser()) + " · " + chatName
                    + (hint.quality.isEmpty() ? "" : " · " + hint.quality)
                    + (entry.message.getDocument() == null ? "" : " · " + AndroidUtilities.formatFileSize(entry.message.getDocument().size))
                    + (progress.isEmpty() ? "" : "\n" + progress));
        }
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaVersions))
                .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                    TjMediaLibrary.Entry entry = variants.get(which).value;
                    TjMediaStore.Record state = localRecords.get(entry.key);
                    openSource(entry, state == null || state.watched || state.duration <= 0
                            || state.position >= state.duration * .98 ? 0 : state.position);
                }).show();
    }

    private String episodeProgress(java.util.List<TjMediaCatalog.Source<TjMediaLibrary.Entry>> variants) {
        TjMediaStore.Record recent = null;
        for (TjMediaCatalog.Source<TjMediaLibrary.Entry> source : variants) {
            TjMediaStore.Record record = localRecords.get(source.identity);
            if (record == null) continue;
            if (record.watched) return text(R.string.TjMediaWatched);
            if (record.duration > 0 && record.position >= record.duration * .98) return text(R.string.TjMediaCompleted);
            if (record.duration > 0 && record.position > 0 && (recent == null || record.playedAt > recent.playedAt)) recent = record;
        }
        return recent == null ? "" : text(R.string.TjMediaContinue) + " · "
                + Math.min(100, Math.round(recent.position * 100.0 / recent.duration)) + "%";
    }

    private void editEpisode(TjMediaStore.Record record) {
        if (getParentActivity() == null) return;
        int generation = localGeneration;
        LinearLayout body = new LinearLayout(getParentActivity());
        body.setOrientation(LinearLayout.VERTICAL);
        EditTextBoldCursor[] fields = new EditTextBoldCursor[2];
        for (int i = 0; i < fields.length; i++) {
            EditTextBoldCursor field = fields[i] = new EditTextBoldCursor(getParentActivity());
            field.setSingleLine(true);
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(i == 0 ? 3 : 4)});
            field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            field.setHint(text(i == 0 ? R.string.TjMediaSeason : R.string.TjMediaEpisode));
            int value = i == 0 ? record.season() : record.episode();
            if (value >= 0) field.setText(Integer.toString(value));
            field.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
            body.addView(field, LayoutHelper.createLinear(-1, 56));
        }
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaEditEpisode))
                .setMessage(text(R.string.TjMediaEditEpisodeInfo)).setView(body)
                .setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
                    int season = fields[0].length() == 0 ? -2 : Integer.parseInt(fields[0].getText().toString());
                    int episode = fields[1].length() == 0 ? -2 : Integer.parseInt(fields[1].getText().toString());
                    TjMediaStore.getInstance().setEpisode(record.message, season, episode, success -> {
                        if (generation != localGeneration || getParentActivity() == null) return;
                        if (success) reload();
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setMessage(text(R.string.TjMediaSaveError))
                                .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                    });
                }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void showMediaAbout() {
        Context context = getParentActivity();
        if (context == null) return;
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        android.widget.ImageView logo = new android.widget.ImageView(context);
        logo.setImageDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(context, R.drawable.tj_tmdb_logo));
        logo.setContentDescription("TMDB");
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        body.addView(logo, LayoutHelper.createLinear(180, 24, Gravity.CENTER_HORIZONTAL, 20, 12, 20, 12));
        TextView notice = new TextView(context);
        notice.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        notice.setTextSize(16);
        notice.setPadding(AndroidUtilities.dp(24), 0, AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        String attribution = text(R.string.TjMediaAttribution);
        String required = "This product uses the TMDB API but is not endorsed or certified by TMDB.";
        notice.setText(attribution + (attribution.equals(required) ? "" : "\n\n" + required)
                + "\n\n" + text(R.string.TjMediaIndexInfo)
                + "\n\n" + text(R.string.TjMediaSearchInfo)
                + "\n\n" + text(R.string.TjMediaFolderScopeInfo));
        body.addView(notice, LayoutHelper.createLinear(-1, -2));
        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.addView(body);
        showDialog(new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaAbout)).setView(scroll)
                .setPositiveButton("TMDB", (d, w) -> org.telegram.messenger.browser.Browser.openUrl(context, "https://www.themoviedb.org"))
                .setNegativeButton(LocaleController.getString(R.string.Close), null).create());
    }

    private void identify(TjMediaLibrary.Entry entry) {
        if (!entry.isAccountAvailable() || getParentActivity() == null) return;
        if (!TjConfig.hasMediaMetadataCredential(entry.account)) { configureMetadata(entry.account); return; }
        TjMediaTitle hint = TjMediaTitle.parse(entry.message.getDocumentName(), entry.message.messageOwner.message);
        LinearLayout body = new LinearLayout(getParentActivity());
        body.setOrientation(LinearLayout.VERTICAL);
        EditTextBoldCursor input = new EditTextBoldCursor(getParentActivity());
        input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setText(hint.title);
        input.setPadding(AndroidUtilities.dp(20), 0, AndroidUtilities.dp(20), 0);
        body.addView(input, LayoutHelper.createLinear(-1, 56));
        TextCheckCell series = new TextCheckCell(getParentActivity());
        boolean[] isSeries = {hint.season >= 0};
        series.setTextAndCheck(text(R.string.TjMediaSeries), isSeries[0], false);
        series.setOnClickListener(v -> { isSeries[0] = !isSeries[0]; series.setChecked(isSeries[0]); });
        body.addView(series);
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaIdentify))
                .setMessage(text(R.string.TjMediaLookupInfo)).setView(body)
                .setPositiveButton(LocaleController.getString(R.string.Search), (dialog, which) -> lookup(entry, input.getText().toString(), isSeries[0]))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }

    private void showSaveError() {
        if (getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity())
                .setMessage(text(R.string.TjMediaSaveError))
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }

    private void lookup(TjMediaLibrary.Entry entry, String query, boolean series) {
        int generation = localGeneration;
        AlertDialog loading = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        loading.setOnCancelListener(dialog -> metadataClient.cancel());
        showDialog(loading);
        metadataClient.search(entry.account, query, series, metadataLanguage(), (titles, error) -> {
            loading.dismiss();
            if (generation != localGeneration || getParentActivity() == null) return;
            if (error != TjMediaMetadata.OK || titles.isEmpty()) {
                new AlertDialog.Builder(getParentActivity()).setMessage(text(error == TjMediaMetadata.CREDENTIAL ? R.string.TjMediaKeyError
                        : error == TjMediaMetadata.RATE_LIMIT ? R.string.TjMediaRateLimit
                        : error != TjMediaMetadata.OK ? R.string.TjMediaLookupError : R.string.TjMediaEmpty))
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).show();
                return;
            }
            ArrayList<CharSequence> names = new ArrayList<>();
            for (TjMediaMetadata.Title title : titles) names.add(title.name + " · " + title.date);
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaChooseMatch))
                    .setItems(names.toArray(new CharSequence[0]), (dialog, which) ->
                            TjMediaStore.getInstance().setMetadata(entry.message, titles.get(which), success -> {
                                if (generation != localGeneration || getParentActivity() == null) return;
                                if (success) { reload(); showDetails(entry); }
                                else showSaveError();
                            })).create());
        });
    }

    @Override public void onResume() {
        super.onResume();
        viewResumed = true;
        if (screenOwner != 0 && screenOwner != UserConfig.getInstance(currentAccount).getClientUserId()) { finishFragment(); return; }
        pendingStoreUpdate = true;
        scheduleAutomaticRefresh();
    }

    @Override public void onFragmentDestroy() {
        viewResumed = false;
        AndroidUtilities.cancelRunOnUIThread(automaticRefresh);
        refreshScheduled = false;
        stopMatching();
        org.telegram.messenger.tj.TjMediaScanCoordinator.getInstance().removeListener(scanChanged);
        TjMediaStore.getInstance().removeListener(storeChanged);
        AndroidUtilities.cancelRunOnUIThread(homeRefresh);
        metadataClient.cancel();
        localGeneration++;
        AndroidUtilities.cancelRunOnUIThread(searchAction);
        if (library != null) library.close();
        super.onFragmentDestroy();
    }

    @Override public void onPause() {
        viewResumed = false;
        AndroidUtilities.cancelRunOnUIThread(automaticRefresh);
        refreshScheduled = false;
        stopMatching();
        super.onPause();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return visible.size(); }
        @Override public int getItemViewType(int position) { return libraryView == 7 || libraryView == 8 ? 1 : 0; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 0) {
                org.telegram.ui.Cells.TjMediaRowCell cell = new org.telegram.ui.Cells.TjMediaRowCell(parent.getContext());
                cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                return new RecyclerListView.Holder(cell);
            }
            TjMediaCardCell cell = new TjMediaCardCell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            return new RecyclerListView.Holder(cell);
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            TjMediaLibrary.Entry entry = visible.get(position);
            MessageObject message = entry.message;
            String title = message.getDocumentName();
            if (title == null || title.isEmpty()) title = TjMediaStore.displayCaption(message);
            if (title == null || title.isEmpty()) title = text(R.string.TjMediaCenter);
            MessagesController controller = MessagesController.getInstance(entry.account);
            long id = message.getDialogId();
            TLRPC.Chat chat = id < 0 ? controller.getChat(-id) : null;
            String source = id > 0 ? UserObject.getUserName(controller.getUser(id)) : chat == null ? Long.toString(id) : chat.title;
            TjMediaStore.Record record = localRecords.get(entry.key);
            if (record != null && !record.title().trim().isEmpty()) title = record.title();
            if (holder.itemView instanceof org.telegram.ui.Cells.TjMediaRowCell) {
                ((org.telegram.ui.Cells.TjMediaRowCell) holder.itemView).bind(message, title, source);
                return;
            }
            ((TjMediaCardCell) holder.itemView).bind(message, title,
                    UserObject.getUserName(UserConfig.getInstance(entry.account).getCurrentUser()) + " · " + source,
                    record == null ? 0 : record.position, record == null ? 0 : record.duration,
                    record == null || record.metadata == null ? null : record.metadata.backdropUrl());
        }
    }
}
