package org.telegram.messenger.tj;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Explicit screen-owned batch matching; no background/automatic disclosure of captions. */
public final class TjMediaAutoMatcher {
    public interface Listener { void changed(); void finished(int error); }
    public static final int STORAGE = 5;
    private final TjMediaMetadata client = new TjMediaMetadata();
    private final Listener listener;
    private final ArrayList<Integer> accounts = new ArrayList<>();
    private final HashMap<Integer, Long> owners = new HashMap<>();
    private final HashMap<Long, Set<Long>> sources = new HashMap<>();
    private final LinkedHashMap<String, TjMediaMetadata.Title> cache = new LinkedHashMap<>();
    private final ArrayList<TjMediaStore.Record> page = new ArrayList<>();
    private final Runnable next = this::step;
    private String language;
    private int sourceType, accountIndex, position, nextOffset;
    private boolean hasMore, closed;
    private long checked, matched;

    public TjMediaAutoMatcher(Listener listener) { this.listener = listener; }
    public long checked() { return checked; }
    public long matched() { return matched; }

    public void start(List<Integer> selected, Map<Long, Set<Long>> sourceSelection, int sourceType, String language) {
        this.sourceType = sourceType;
        this.language = language;
        for (int account : selected) if (UserConfig.getInstance(account).isClientActivated()
                && TjConfig.hasMediaMetadataCredential(account)) {
            accounts.add(account);
            owners.put(account, UserConfig.getInstance(account).getClientUserId());
        }
        for (Map.Entry<Long, Set<Long>> entry : sourceSelection.entrySet()) sources.put(entry.getKey(), new HashSet<>(entry.getValue()));
        if (accounts.isEmpty()) finish(TjMediaMetadata.CREDENTIAL);
        else loadPage(0);
    }

    private boolean available(int account) {
        return UserConfig.getInstance(account).isClientActivated()
                && owners.get(account) == UserConfig.getInstance(account).getClientUserId();
    }

    private void loadPage(int offset) {
        if (closed) return;
        if (accountIndex >= accounts.size()) { finish(TjMediaMetadata.OK); return; }
        int account = accounts.get(accountIndex);
        if (!available(account)) { accountIndex++; loadPage(0); return; }
        // Page over all rows: adding metadata must not shift the pagination predicate.
        TjMediaStore.getInstance().load(account, 0, "", "", offset, sources.get(owners.get(account)), sourceType, 0, records -> {
            if (closed) return;
            if (!available(account)) { accountIndex++; loadPage(0); return; }
            if (records == null) { finish(STORAGE); return; }
            page.clear();
            page.addAll(records);
            position = 0;
            nextOffset = records.nextOffset;
            hasMore = records.hasMore;
            listener.changed();
            step();
        });
    }

    private void step() {
        if (closed) return;
        int account = accounts.get(accountIndex);
        if (!available(account)) { accountIndex++; loadPage(0); return; }
        while (position < page.size()) {
            TjMediaStore.Record record = page.get(position++);
            checked++;
            if (record.metadata != null || !video(record.message)) continue;
            TjMediaTitle hint = TjMediaTitle.parse(record.message.getDocumentName(), record.message.messageOwner.message);
            if (hint.title.length() < 2 || hint.title.length() > 250) continue;
            String key = owners.get(account) + "|" + TjMediaTitle.normalizeSearch(hint.title) + "|" + hint.year + "|" + (hint.season >= 0);
            if (cache.containsKey(key)) {
                save(record, cache.get(key));
                return;
            }
            listener.changed();
            ArrayList<TjMediaMetadata.Title> results = new ArrayList<>();
            if (hint.season >= 0) search(record, hint, key, true, results);
            else search(record, hint, key, false, results);
            return;
        }
        if (hasMore) loadPage(nextOffset);
        else { accountIndex++; loadPage(0); }
    }

    private void search(TjMediaStore.Record record, TjMediaTitle hint, String key,
                        boolean series, ArrayList<TjMediaMetadata.Title> results) {
        client.search(record.message.currentAccount, hint.title, series, language, (titles, error) -> {
            if (closed) return;
            if (error != TjMediaMetadata.OK) { finish(error); return; }
            for (TjMediaMetadata.Title title : titles) if (!title.searchComplete) {
                remember(key, null);
                save(record, null);
                return;
            }
            results.addAll(titles);
            // Without episode hints, check both types rather than assume every video is a movie.
            if (!series) { search(record, hint, key, true, results); return; }
            ArrayList<TjMediaMatch.Candidate> candidates = new ArrayList<>();
            for (TjMediaMetadata.Title title : results) {
                int year = title.date.matches("\\d{4}-.*") ? Integer.parseInt(title.date.substring(0, 4)) : 0;
                candidates.add(new TjMediaMatch.Candidate(title.id, title.series, title.name, title.originalName, year));
            }
            int selected = TjMediaMatch.unique(hint, candidates);
            TjMediaMetadata.Title match = selected < 0 ? null : results.get(selected);
            remember(key, match);
            save(record, match);
        });
    }

    private void remember(String key, TjMediaMetadata.Title match) {
        if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
        cache.put(key, match);
    }

    private void save(TjMediaStore.Record record, TjMediaMetadata.Title match) {
        if (closed) return;
        if (match == null) { AndroidUtilities.runOnUIThread(next, 50); return; }
        TjMediaStore.getInstance().setMetadataIfAbsent(record.message, match, result -> {
            if (closed) return;
            if (result < 0) { finish(STORAGE); return; }
            if (result == 1) matched++;
            listener.changed();
            AndroidUtilities.runOnUIThread(next, 100);
        });
    }

    private static boolean video(MessageObject message) {
        if (message.isVideo() && !message.isRoundVideo()) return true;
        String filename = message.getDocumentName();
        return filename != null && filename.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mkv|mp4|avi|mov|webm|m4v|ts)$");
    }

    private void finish(int error) {
        if (closed) return;
        cancel();
        listener.finished(error);
    }

    public void cancel() {
        closed = true;
        client.cancel();
        AndroidUtilities.cancelRunOnUIThread(next);
        page.clear();
        cache.clear();
    }
}
