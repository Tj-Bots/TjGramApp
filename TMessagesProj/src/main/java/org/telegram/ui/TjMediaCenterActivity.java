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
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TjMediaCardCell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** General-media entry point; movie recognition must never hide ordinary files. */
public class TjMediaCenterActivity extends BaseFragment {
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final ArrayList<TjMediaLibrary.Entry> visible = new ArrayList<>();
    private TjMediaLibrary library;
    private TjMediaLibrary scanner;
    private TjMediaAutoMatcher autoMatcher;
    private final Runnable scanNext = () -> {
        if (scanner != null && !scanner.isLoading() && !scanner.hasError() && scanner.hasMore()) scanner.loadMore();
    };
    private Adapter adapter;
    private EditTextBoldCursor search;
    private TextView status;
    private TextView accountButton;
    private TextView typeButton;
    private TextView sourceButton;
    private final HashMap<Long, Set<Long>> sources = new HashMap<>();
    private int sourceType;
    private int accountMode;
    private boolean preferencesLoaded;
    private long screenOwner;
    private TextView viewButton;
    private int libraryView = 9;
    private TjMediaHomeView home;
    private RecyclerListView grid;
    private final Runnable homeRefresh = this::refreshHome;
    private final Runnable searchAction = this::reload;
    private final Runnable storeChanged = () -> {
        if (fragmentView != null) {
            AndroidUtilities.cancelRunOnUIThread(searchAction);
            AndroidUtilities.runOnUIThread(searchAction, 200);
        }
    };
    private String collection = "";
    private int localGeneration;
    private int localPending;
    private boolean localError;
    private final HashMap<Integer, Integer> localOffsets = new HashMap<>();
    private final HashMap<String, TjMediaStore.Record> localRecords = new HashMap<>();
    private final ArrayList<TjMediaLibrary.Entry> localEntries = new ArrayList<>();
    private final TjMediaMetadata metadataClient = new TjMediaMetadata();
    private TjMediaCatalog<TjMediaLibrary.Entry> catalog = new TjMediaCatalog<>();
    private int mediaType;
    private String searchQuery = "";
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
                if (id == -1) finishFragment();
                else if (id == 1) mediaSettings();
            }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        search = new EditTextBoldCursor(context);
        search.setSingleLine(true);
        search.setTextSize(16);
        search.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        search.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        search.setHint(text(R.string.TjMediaSearch));
        search.setText(searchQuery);
        search.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);
        root.addView(search, LayoutHelper.createLinear(-1, 56));
        LinearLayout controls = new LinearLayout(context);
        accountButton = label(context);
        typeButton = label(context);
        controls.addView(accountButton, LayoutHelper.createLinear(-2, 48));
        controls.addView(typeButton, LayoutHelper.createLinear(-2, 48));
        android.widget.HorizontalScrollView filterStrip = new android.widget.HorizontalScrollView(context);
        filterStrip.setHorizontalScrollBarEnabled(false);
        filterStrip.addView(controls);
        root.addView(filterStrip, LayoutHelper.createLinear(-1, 48));
        sourceButton = label(context);
        sourceButton.setOnClickListener(v -> chooseSources());
        controls.addView(sourceButton, LayoutHelper.createLinear(-2, 48));
        viewButton = label(context);
        viewButton.setOnClickListener(v -> new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaView))
                .setItems(viewNames(), (dialog, which) -> {
                    if (which == 5) {
                        chooseCollection(new ArrayList<>(accounts), null, value -> { collection = value; libraryView = 5; reload(); });
                    } else { libraryView = which; reload(); }
                }).show());
        controls.addView(viewButton, LayoutHelper.createLinear(-2, 48));
        accountButton.setOnClickListener(v -> chooseAccounts());
        typeButton.setOnClickListener(v -> new AlertDialog.Builder(getParentActivity())
                .setTitle(text(R.string.TjMediaType)).setItems(typeNames(), (dialog, which) -> {
                    mediaType = which;
                    reload();
                }).show());
        RecyclerListView list = new RecyclerListView(context) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w, h, oldw, oldh);
                if (getLayoutManager() instanceof GridLayoutManager && w > 0) {
                    float cardWidth = 180 * Math.max(1f, getResources().getConfiguration().fontScale);
                    ((GridLayoutManager) getLayoutManager()).setSpanCount(Math.max(1,
                            (int) ((w - getPaddingLeft() - getPaddingRight()) / AndroidUtilities.density / cardWidth)));
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
        status.setMinHeight(AndroidUtilities.dp(56));
        status.setOnClickListener(v -> {
            if (autoMatcher != null) { stopMatching(); refresh(); return; }
            if (scanner != null) {
                if (scanner.hasError() && !scanner.isLoading()) { scanner.loadMore(); refresh(); }
                else { stopScan(); refresh(); }
                return;
            }
            if (libraryView == 9 && localError && localPending == 0) {
                refreshHome();
                refresh();
                return;
            }
            if (libraryView != 0 && libraryView != 9) {
                if (localPending == 0) loadLocal();
            } else if (library != null && !library.isLoading()) {
                library.loadMore();
                refresh();
            }
        });
        root.addView(status, LayoutHelper.createLinear(-1, -2));
        loadPreferences();
        TjMediaStore.getInstance().addListener(storeChanged);
        library = new TjMediaLibrary(() -> {
            refresh();
            if (libraryView == 9) {
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
                text(R.string.TjMediaMusic), text(R.string.TjMediaVoice)};
    }

    private void reload() {
        metadataClient.cancel();
        dismissCurrentDialog();
        stopMatching();
        stopScan();
        if (screenOwner != UserConfig.getInstance(currentAccount).getClientUserId()) {
            if (library != null) library.close();
            finishFragment();
            return;
        }
        preferencesLoaded = false;
        accounts.clear();
        sources.clear();
        loadPreferences();
        AndroidUtilities.cancelRunOnUIThread(homeRefresh);
        localGeneration++;
        localPending = 0;
        localError = false;
        localOffsets.clear();
        localEntries.clear();
        localRecords.clear();
        if (library == null) return;
        grid.setVisibility(libraryView == 9 ? View.GONE : View.VISIBLE);
        home.setVisibility(libraryView == 9 ? View.VISIBLE : View.GONE);
        if (libraryView == 0 || libraryView == 9) {
            library.reset(accounts, sources, sourceType, search.getText().toString());
            if (libraryView == 9) refreshHome();
        }
        else {
            library.close();
            for (int account : accounts) localOffsets.put(account, 0);
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
        localOffsets.clear();
        ArrayList<TjMediaLibrary.Entry> nextEntries = new ArrayList<>();
        HashMap<String, TjMediaStore.Record> nextRecords = new HashMap<>();
        int generation = localGeneration;
        localPending = accounts.size() * 5;
        localError = false;
        for (int account : accounts) {
            long owner = UserConfig.getInstance(account).getClientUserId();
            for (int mode : new int[]{0, 1, 3, 7, 8}) TjMediaStore.getInstance().load(account, mode,
                    search.getText().toString(), "", 0, sources.get(owner), sourceType, mediaType, records -> {
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

    private void loadLocal() {
        int generation = localGeneration;
        localError = false;
        ArrayList<Integer> pending = new ArrayList<>(localOffsets.keySet());
        localPending = pending.size();
        for (int account : pending) {
            int offset = localOffsets.get(account);
            long owner = UserConfig.getInstance(account).getClientUserId();
            TjMediaStore.getInstance().load(account, libraryView == 6 || libraryView == 9 ? 0 : libraryView,
                    search.getText().toString(), collection, offset, sources.get(owner), sourceType, mediaType, records -> {
                        if (generation != localGeneration) return;
                        localPending--;
                        if (records == null) localError = true;
                        else {
                            if (!records.hasMore) localOffsets.remove(account);
                            else localOffsets.put(account, records.nextOffset);
                            for (TjMediaStore.Record record : records) {
                                TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, owner, record.message);
                                if (localRecords.put(entry.key, record) == null) localEntries.add(entry);
                            }
                            localEntries.sort((a, b) -> libraryView == 1 || libraryView == 2
                                    ? Long.compare(localRecords.get(b.key).playedAt, localRecords.get(a.key).playedAt)
                                    : Integer.compare(b.message.messageOwner.date, a.message.messageOwner.date));
                        }
                        refresh();
                    });
        }
        refresh();
    }

    private void refresh() {
        if (adapter == null || library == null) return;
        visible.clear();
        for (TjMediaLibrary.Entry entry : libraryView == 0 ? library.snapshot() : localEntries) {
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
            if (mediaType == 0 || mediaType == 1 && m.isPhoto()
                    || mediaType == 2 && (m.isVideo() || m.isGif())
                    || mediaType == 3 && m.getDocument() != null && !m.isVideo() && !m.isMusic() && !m.isVoice() && !m.isRoundVideo() && !m.isGif()
                    || mediaType == 4 && m.isMusic()
                    || mediaType == 5 && (m.isVoice() || m.isRoundVideo())) visible.add(entry);
        }
        catalog = new TjMediaCatalog<>();
        if (libraryView == 7 || libraryView == 8) {
            for (TjMediaLibrary.Entry entry : visible) {
                TjMediaStore.Record record = localRecords.get(entry.key);
                if (record == null || record.metadata == null) continue;
                catalog.add(record.metadata.id, record.metadata.series,
                        new TjMediaCatalog.Source<>(entry.key, entry, record.season(), record.episode()));
            }
            visible.clear();
            for (TjMediaCatalog.Group<TjMediaLibrary.Entry> group : catalog.groups()) visible.add(group.sources.get(0).value);
        }
        adapter.notifyDataSetChanged();
        if (libraryView == 9 && home != null) home.bind(visible, localRecords, library.isLoading() || localPending > 0);
        accountButton.setText(text(R.string.TjMediaAccounts) + " · " + accounts.size());
        typeButton.setText(typeNames()[mediaType]);
        viewButton.setText(viewNames()[libraryView] + (libraryView == 5 ? " · " + collection : ""));
        int selectedSources = 0;
        for (Set<Long> selected : sources.values()) selectedSources += selected.size();
        sourceButton.setText(sourceNames()[sourceType] + (selectedSources == 0 ? "" : " · " + selectedSources));
        boolean loading = libraryView == 0 || libraryView == 9 ? library.isLoading() || localPending > 0 : localPending > 0;
        boolean error = libraryView == 0 || libraryView == 9 ? library.hasError() || localError : localError;
        boolean more = libraryView == 0 || libraryView == 9 ? library.hasMore() : !localOffsets.isEmpty();
        status.setText(text(loading ? R.string.TjMediaLoading
                : error ? R.string.TjMediaRetry
                : more ? R.string.TjMediaMore
                : visible.isEmpty() ? R.string.TjMediaEmpty : R.string.TjMediaEnd));
        status.setEnabled(!loading && (more || error));
        if (scanner != null) {
            status.setText(scanner.hasError() && !scanner.isLoading() ? text(R.string.TjMediaRetry)
                    : String.format(java.util.Locale.getDefault(), text(R.string.TjMediaScanProgress), scanner.scannedCount()));
            status.setEnabled(true);
        }
        if (autoMatcher != null) updateMatchStatus();
    }

    private void chooseAccounts() {
        if (getParentActivity() == null) return;
        ArrayList<Integer> active = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            if (!UserConfig.getInstance(i).isClientActivated()) continue;
            active.add(i);
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
                sourceNames()[3], text(R.string.TjMediaSelectChats)};
        new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaSources))
                .setItems(options, (dialog, which) -> {
                    if (which < 4) {
                        sourceType = which;
                        sources.clear();
                        savePreferences();
                        reload();
                    } else {
                        ArrayList<String> names = new ArrayList<>();
                        for (int account : accounts) names.add(UserObject.getUserName(UserConfig.getInstance(account).getCurrentUser()));
                        if (accounts.size() == 1) chooseChats(accounts.get(0));
                        else new AlertDialog.Builder(getParentActivity()).setTitle(text(R.string.TjMediaAccounts))
                                .setItems(names.toArray(new CharSequence[0]), (d, index) -> chooseChats(accounts.get(index))).show();
                    }
                }).show();
    }

    private void chooseChats(int account) {
        MessagesController controller = MessagesController.getInstance(account);
        long owner = UserConfig.getInstance(account).getClientUserId();
        Set<Long> current = sources.get(owner);
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
        if (current != null) for (long id : current) if (!ids.contains(id)) {
            ids.add(id);
            names.add(Long.toString(id));
        }
        boolean[] selected = new boolean[ids.size()];
        for (int i = 0; i < ids.size(); i++) selected[i] = current == null || current.contains(ids.get(i));
        chooseMany(text(R.string.TjMediaSelectChats), names, selected, () -> {
            HashSet<Long> chosen = new HashSet<>();
            for (int i = 0; i < selected.length; i++) if (selected[i]) chosen.add(ids.get(i));
            // An explicit empty set uses a sentinel, never silently broadens to all chats.
            if (chosen.isEmpty()) chosen.add(0L);
            sources.put(owner, chosen);
            sourceType = 0;
            savePreferences();
            reload();
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
                sources.put(owner, peers);
            }
        }
        if (accounts.isEmpty() && UserConfig.getInstance(currentAccount).isClientActivated()) accounts.add(currentAccount);
        sourceType = preferences == null ? 0 : Math.max(0, Math.min(3, preferences.getInt("source_type", 0)));
    }

    private void savePreferences() {
        SharedPreferences preferences = TjConfig.mediaLibrary(currentAccount);
        if (preferences == null) return;
        HashSet<String> owners = new HashSet<>();
        for (int account : accounts) owners.add(Long.toString(UserConfig.getInstance(account).getClientUserId()));
        SharedPreferences.Editor editor = preferences.edit().putInt("accounts_mode", accountMode)
                .putStringSet("accounts", owners).putInt("source_type", sourceType);
        for (String key : preferences.getAll().keySet()) if (key.startsWith("sources_")) editor.remove(key);
        for (java.util.Map.Entry<Long, Set<Long>> source : sources.entrySet()) {
            HashSet<String> peers = new HashSet<>();
            for (long id : source.getValue()) peers.add(Long.toString(id));
            editor.putStringSet("sources_" + source.getKey(), peers);
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
        TjMediaStore.getInstance().state(entry.message, record -> {
            if (generation != localGeneration || getParentActivity() == null) return;
            if (record == null) { showStateError(entry, () -> showDetails(entry)); return; }
            localRecords.put(entry.key, record);
            Context context = getParentActivity();
            LinearLayout body = new LinearLayout(context);
            body.setOrientation(LinearLayout.VERTICAL);
            TjMediaCardCell hero = new TjMediaCardCell(context);
            String name = record.metadata == null ? TjMediaTitle.parse(entry.message.getDocumentName(), TjMediaStore.displayCaption(entry.message)).title : record.metadata.name;
            hero.bind(entry.message, name, entry.message.getDocumentName() == null ? "" : entry.message.getDocumentName(),
                    record.position, record.duration, record.metadata == null ? null : record.metadata.backdropUrl());
            body.addView(hero, LayoutHelper.createLinear(-1, -2));
            if (record.metadata != null && record.metadataOrigin == 2) {
                TextView automatic = label(context);
                automatic.setText(text(R.string.TjMediaAutomaticMatch));
                automatic.setOnClickListener(v -> { dismissCurrentDialog(); identify(entry); });
                body.addView(automatic);
            }
            TextView description = new TextView(context);
            description.setTextSize(15);
            description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            description.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
            description.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            String caption = TjMediaStore.displayCaption(entry.message);
            description.setText(record.metadata == null ? caption
                    : record.metadata.date + " · ★ " + String.format(java.util.Locale.ROOT, "%.1f", record.metadata.rating)
                    + "\n\n" + record.metadata.overview
                    + (caption == null || caption.isEmpty() ? "" : "\n\n" + text(R.string.TjMediaOriginalCaption) + "\n" + caption));
            description.setTextIsSelectable(true);
            body.addView(description);
            if (entry.message.getDocument() != null) {
                TjMediaTitle hint = TjMediaTitle.parse(entry.message.getDocumentName(), entry.message.messageOwner.message);
                TextView file = new TextView(context);
                file.setTextSize(14);
                file.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                file.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), AndroidUtilities.dp(12));
                file.setText(text(R.string.TjMediaFileDetails) + "\n" + entry.message.getDocumentName()
                        + "\n" + AndroidUtilities.formatFileSize(entry.message.getDocument().size)
                        + (hint.quality.isEmpty() ? "" : " · " + hint.quality));
                file.setTextIsSelectable(true);
                body.addView(file);
            }
            if (record.metadata != null) {
                TextView variants = label(context);
                variants.setText(text(record.metadata.series ? R.string.TjMediaSeasons : R.string.TjMediaVersions));
                variants.setOnClickListener(v -> loadGroupSources(record.metadata));
                body.addView(variants);
                if (record.metadata.series) {
                    if (record.season() >= 0 && record.episode() >= 0) {
                        TextView next = label(context);
                        next.setText(text(R.string.TjMediaNextEpisode));
                        next.setOnClickListener(v -> loadGroupSources(record.metadata, record.season(), record.episode()));
                        body.addView(next);
                    }
                    TextView episode = label(context);
                    episode.setText(text(R.string.TjMediaEditEpisode));
                    episode.setOnClickListener(v -> editEpisode(record));
                    body.addView(episode);
                }
            }
            TextView identify = label(context);
            identify.setText(text(R.string.TjMediaIdentify));
            body.addView(identify);
            TextView lists = label(context);
            lists.setText(text(R.string.TjMediaManageItem));
            body.addView(lists);
            if (record.metadata != null) {
                TextView remove = label(context);
                remove.setText(text(R.string.TjMediaRemoveMatch));
                remove.setOnClickListener(v -> TjMediaStore.getInstance().setMetadata(entry.message, null, success -> {
                    if (generation != localGeneration || getParentActivity() == null) return;
                    if (success) { reload(); showDetails(entry); }
                    else showSaveError();
                }));
                body.addView(remove);
            }
            android.widget.ScrollView scroll = new android.widget.ScrollView(context);
            scroll.addView(body);
            boolean playable = entry.message.isVideo() || entry.message.isRoundVideo()
                    || entry.message.isVoice() || entry.message.isMusic();
            boolean resume = playable && !record.watched && record.duration > 0
                    && record.position > 0 && record.position < record.duration * .98;
            AlertDialog.Builder builder = new AlertDialog.Builder(context).setTitle(text(R.string.TjMediaDetails)).setView(scroll)
                    .setPositiveButton(text(resume ? R.string.TjMediaContinue : playable ? R.string.TjMediaPlay : R.string.TjMediaOpenSource),
                            (dialog, which) -> openSource(entry, resume ? record.position : playable ? 0 : -1))
                    .setNegativeButton(LocaleController.getString(R.string.Close), null);
            if (resume) builder.setNeutralButton(text(R.string.TjMediaRestart), (dialog, which) -> openSource(entry, 0));
            AlertDialog details = builder.create();
            if (playable) {
                TextView source = label(context);
                source.setText(text(R.string.TjMediaOpenSource));
                source.setOnClickListener(v -> { details.dismiss(); openSource(entry); });
                body.addView(source);
            }
            identify.setOnClickListener(v -> { details.dismiss(); identify(entry); });
            lists.setOnClickListener(v -> { details.dismiss(); editItem(entry); });
            showDialog(details);
        });
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
        if (autoMatcher == null || status == null) return;
        status.setText(String.format(java.util.Locale.getDefault(), text(R.string.TjMediaAutoMatchProgress), autoMatcher.checked(), autoMatcher.matched()));
        status.setEnabled(true);
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
                AndroidUtilities.cancelRunOnUIThread(homeRefresh);
                AndroidUtilities.runOnUIThread(homeRefresh, 200);
            }
            @Override public void finished(int error) {
                if (autoMatcher == null) return;
                long matched = autoMatcher.matched();
                autoMatcher = null;
                refreshHome();
                if (getParentActivity() == null) return;
                String message = error == TjMediaMetadata.OK
                        ? String.format(java.util.Locale.getDefault(), text(R.string.TjMediaAutoMatchComplete), matched)
                        : text(error == TjMediaAutoMatcher.STORAGE ? R.string.TjMediaSaveError
                        : error == TjMediaMetadata.CREDENTIAL ? R.string.TjMediaKeyError
                        : error == TjMediaMetadata.RATE_LIMIT ? R.string.TjMediaRateLimit : R.string.TjMediaLookupError);
                showDialog(new AlertDialog.Builder(getParentActivity()).setMessage(message)
                        .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
            }
        });
        autoMatcher.start(new ArrayList<>(accounts), new HashMap<>(sources), sourceType, metadataLanguage());
    }

    private void stopScan() {
        AndroidUtilities.cancelRunOnUIThread(scanNext);
        if (scanner != null) { scanner.close(); scanner = null; }
    }

    private void startScan() {
        stopScan();
        libraryView = 9;
        reload();
        library.close();
        scanner = new TjMediaLibrary(() -> {
            if (scanner == null) return;
            refresh();
            AndroidUtilities.cancelRunOnUIThread(homeRefresh);
            AndroidUtilities.runOnUIThread(homeRefresh, 200);
            if (!scanner.isLoading() && !scanner.hasError()) {
                if (scanner.hasMore()) {
                    AndroidUtilities.cancelRunOnUIThread(scanNext);
                    AndroidUtilities.runOnUIThread(scanNext, 500);
                } else {
                    stopScan();
                    refresh();
                    if (getParentActivity() != null) showDialog(new AlertDialog.Builder(getParentActivity())
                            .setMessage(text(R.string.TjMediaScanComplete))
                            .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
                }
            }
        }, true);
        scanner.reset(new ArrayList<>(accounts), new HashMap<>(sources), sourceType, "");
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
                void load(int offset) {
                    TjMediaStore.getInstance().loadTitle(account, title.id, title.series, offset, sources.get(owner), sourceType, page -> {
                        if (cancelled[0] || generation != localGeneration) { loading.dismiss(); return; }
                        if (page == null) { failed[0] = true; complete.run(); return; }
                        for (TjMediaStore.Record item : page) {
                            TjMediaLibrary.Entry entry = new TjMediaLibrary.Entry(account, owner, item.message);
                            if (!entry.isAccountAvailable()) continue;
                            localRecords.put(entry.key, item);
                            all.add(title.id, title.series, new TjMediaCatalog.Source<>(entry.key, entry, item.season(), item.episode()));
                        }
                        if (page.hasMore) load(page.nextOffset);
                        else complete.run();
                    });
                }
            }
            new PageLoader().load(0);
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
                + "\n\n" + text(R.string.TjMediaIndexInfo));
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
        if (library != null) reload();
    }

    @Override public void onFragmentDestroy() {
        stopMatching();
        stopScan();
        TjMediaStore.getInstance().removeListener(storeChanged);
        AndroidUtilities.cancelRunOnUIThread(homeRefresh);
        metadataClient.cancel();
        localGeneration++;
        AndroidUtilities.cancelRunOnUIThread(searchAction);
        if (library != null) library.close();
        super.onFragmentDestroy();
    }

    @Override public void onPause() {
        stopMatching();
        stopScan();
        super.onPause();
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return visible.size(); }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
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
            if (record != null && record.metadata != null) title = record.metadata.name;
            ((TjMediaCardCell) holder.itemView).bind(message, title,
                    UserObject.getUserName(UserConfig.getInstance(entry.account).getCurrentUser()) + " · " + source,
                    record == null ? 0 : record.position, record == null ? 0 : record.duration,
                    record == null || record.metadata == null ? null : record.metadata.backdropUrl());
        }
    }
}
