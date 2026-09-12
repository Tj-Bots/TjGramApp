package org.telegram.messenger.tj;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.NotificationCenter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One foreground scan job, independent of fragments; each stream checkpoints by owner. */
public final class TjMediaScanCoordinator implements NotificationCenter.NotificationCenterDelegate {
    private static final TjMediaScanCoordinator INSTANCE = new TjMediaScanCoordinator();
    public static TjMediaScanCoordinator getInstance() { return INSTANCE; }
    private final ArrayList<Runnable> listeners = new ArrayList<>();
    private final HashMap<Integer, Long> owners = new HashMap<>();
    private final HashMap<Long, Set<Long>> sources = new HashMap<>();
    private final HashMap<Long, Set<Long>> explicitSources = new HashMap<>(), folders = new HashMap<>();
    private boolean refreshScheduled;
    private boolean requested;
    private final Runnable refreshSources = () -> {
        refreshScheduled = false;
        if (!requested || ApplicationLoader.mainInterfacePaused) return;
        HashMap<Long, Set<Long>> updated = TjMediaSources.resolve(activeAccounts(), explicitSources, folders);
        if (!updated.equals(sources)) {
            pause(); sources.clear(); sources.putAll(updated); resume(); notifyListeners();
        }
    };
    private TjMediaLibrary scanner;
    private int sourceType;
    private final Runnable next = () -> {
        if (scanner != null && !scanner.isLoading() && !scanner.hasError()) scanner.loadMore();
    };

    private TjMediaScanCoordinator() { }
    public TjMediaLibrary scanner() { return scanner; }
    public boolean isRequested() { return requested; }
    public void addListener(Runnable listener) { if (!listeners.contains(listener)) listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }
    private void notifyListeners() { for (Runnable listener : new ArrayList<>(listeners)) listener.run(); }

    public void start(List<Integer> accounts, Map<Long, Set<Long>> selectedSources,
                      Map<Long, Set<Long>> selectedFolders, int type) {
        observe(false);
        pause();
        owners.clear(); sources.clear();
        explicitSources.clear(); explicitSources.putAll(TjMediaSources.copy(selectedSources));
        folders.clear(); folders.putAll(TjMediaSources.copy(selectedFolders));
        for (int account : accounts) {
            UserConfig user = UserConfig.getInstance(account);
            if (user.isClientActivated()) owners.put(account, user.getClientUserId());
        }
        sources.putAll(TjMediaSources.resolve(activeAccounts(), explicitSources, folders));
        sourceType = type;
        requested = !owners.isEmpty();
        observe(true);
        if (!ApplicationLoader.mainInterfacePaused) resume();
        notifyListeners();
    }

    public void stop() {
        requested = false;
        observe(false);
        pause();
        owners.clear(); sources.clear();
        explicitSources.clear(); folders.clear();
        notifyListeners();
    }

    public void setForeground(boolean foreground) {
        if (foreground) resume(); else pause();
        notifyListeners();
    }

    private void pause() {
        AndroidUtilities.cancelRunOnUIThread(next);
        AndroidUtilities.cancelRunOnUIThread(refreshSources);
        refreshScheduled = false;
        if (scanner != null) scanner.close();
        scanner = null;
    }

    private void resume() {
        if (!requested || scanner != null) return;
        ArrayList<Integer> accounts = activeAccounts();
        if (accounts.isEmpty()) { stop(); return; }
        sources.clear(); sources.putAll(TjMediaSources.resolve(accounts, explicitSources, folders));
        scanner = new TjMediaLibrary(this::changed, true);
        scanner.reset(accounts, sources, sourceType, "");
    }

    private ArrayList<Integer> activeAccounts() {
        ArrayList<Integer> accounts = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : owners.entrySet()) {
            UserConfig user = UserConfig.getInstance(entry.getKey());
            if (user.isClientActivated() && user.getClientUserId() == entry.getValue()) accounts.add(entry.getKey());
        }
        return accounts;
    }

    private void observe(boolean add) {
        for (int account : owners.keySet()) {
            NotificationCenter center = NotificationCenter.getInstance(account);
            for (int event : new int[]{NotificationCenter.dialogFiltersUpdated, NotificationCenter.dialogsNeedReload,
                    NotificationCenter.contactsDidLoad, NotificationCenter.updateInterfaces}) {
                if (add) center.addObserver(this, event); else center.removeObserver(this, event);
            }
        }
    }

    @Override public void didReceivedNotification(int id, int account, Object... args) {
        if (folders.isEmpty() || !requested || refreshScheduled) return;
        refreshScheduled = true;
        AndroidUtilities.runOnUIThread(refreshSources, 750);
    }

    private void changed() {
        AndroidUtilities.cancelRunOnUIThread(next);
        if (scanner != null && !scanner.isLoading() && !scanner.hasError()) {
            if (scanner.hasMore()) AndroidUtilities.runOnUIThread(next, 500);
            else {
                // Folder jobs remain subscribed after catching up, so new members
                // can start work without rescanning already-committed history.
                if (folders.isEmpty()) { requested = false; observe(false); }
                pause();
            }
        }
        notifyListeners();
    }
}
