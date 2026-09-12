package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.*;
import java.util.*;

/** Bounded episode summaries and source pages, scoped to captured account owners. */
public final class TjMediaEpisodesView extends LinearLayout {
    public interface Open { void source(TjMediaLibrary.Entry entry); }
    private final BaseFragment host;
    private final TjMediaStore.Record title;
    private final ArrayList<Integer> accounts;
    private final Map<Integer, Long> owners = new HashMap<>();
    private final Map<Long, Set<Long>> sources;
    private final int sourceType;
    private final Open open;
    private int season, generation, sourceRequest;
    private boolean closed;

    public TjMediaEpisodesView(Context context, BaseFragment host, TjMediaStore.Record title,
            List<Integer> accounts, Map<Long, Set<Long>> sources, int sourceType, Open open) {
        super(context); setOrientation(VERTICAL);
        this.host = host; this.title = title; this.accounts = new ArrayList<>(accounts);
        this.sources = TjMediaSources.copy(sources); this.sourceType = sourceType; this.open = open;
        for (int account : accounts) owners.put(account, UserConfig.getInstance(account).getClientUserId());
        season = title.season();
        if (title.isSeries()) episodes(-1); else showMovieSources();
    }

    public void close() { closed = true; generation++; }
    private boolean active(int account) { return !closed && UserConfig.getInstance(account).isClientActivated()
            && owners.get(account) == UserConfig.getInstance(account).getClientUserId(); }
    private static String text(int id) { return TjLocale.getString(id); }
    private static int order(int number) { return number < 0 ? Integer.MAX_VALUE : number; }

    private TextView row(String value, Runnable action) {
        TextView row = new TextView(getContext());
        row.setText(value); row.setTextSize(16); row.setMinHeight(AndroidUtilities.dp(56));
        row.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(14), AndroidUtilities.dp(20), AndroidUtilities.dp(14));
        row.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (action != null) { row.setBackground(Theme.getSelectorDrawable(false)); row.setOnClickListener(v -> action.run()); }
        addView(row, LayoutHelper.createLinear(-1, -2)); return row;
    }

    private static final class NumberState {
        long sources;
        boolean watched, inProgress;
        void add(TjMediaStore.CatalogNumber value) {
            sources += value.sources; watched |= value.watched; inProgress |= value.inProgress;
        }
    }
    private interface Numbers { void done(TreeMap<Integer, NumberState> rows, boolean more); }
    private void numbers(int selectedSeason, int after, Numbers callback) {
        numbers(selectedSeason, after, false, callback);
    }
    private void numbers(int selectedSeason, int after, boolean numberedOnly, Numbers callback) {
        int request = generation;
        int[] pending = {accounts.size()};
        boolean[] failed = {false}, more = {false};
        TreeMap<Integer, NumberState> merged = new TreeMap<>();
        if (accounts.isEmpty()) { callback.done(merged, false); return; }
        for (int account : accounts) TjMediaStore.getInstance().catalogNumbers(account, title, selectedSeason, after,
                sources.get(owners.get(account)), sourceType, numberedOnly, values -> {
                    if (closed || request != generation) return;
                    if (values == null || !active(account)) failed[0] = true;
                    else {
                        if (values.size() > 20) more[0] = true;
                        for (TjMediaStore.CatalogNumber value : values)
                            merged.computeIfAbsent(order(value.number), ignored -> new NumberState()).add(value);
                    }
                    if (--pending[0] != 0) return;
                    if (failed[0]) { callback.done(null, false); return; }
                    // Each owner supplies >20 numbers. The first 20 of their union
                    // is therefore a complete global prefix, even for sparse owners.
                    while (merged.size() > 20) { merged.pollLastEntry(); more[0] = true; }
                    callback.done(merged, more[0]);
                });
    }

    private void seasons(int after) {
        numbers(TjMediaStore.ANY_EPISODE, after, (values, more) -> {
            if (values == null) { error(() -> seasons(after)); return; }
            ArrayList<Integer> keys = new ArrayList<>(values.keySet());
            ArrayList<CharSequence> labels = new ArrayList<>();
            for (int key : keys) labels.add(key == Integer.MAX_VALUE ? text(R.string.TjMediaUnknownEpisode) : text(R.string.TjMediaSeason) + " " + key);
            if (more) labels.add(text(R.string.TjMediaLoadMore));
            host.showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaSeasons))
                    .setItems(labels.toArray(new CharSequence[0]), (d, index) -> {
                        if (index == keys.size()) seasons(keys.get(keys.size() - 1));
                        else { season = keys.get(index) == Integer.MAX_VALUE ? -1 : keys.get(index); episodes(-1); }
                    }).setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
        });
    }

    private void episodes(int after) {
        generation++;
        removeAllViews();
        row((season < 0 ? text(R.string.TjMediaUnknownEpisode) : text(R.string.TjMediaSeason) + " " + season) + " ▾", () -> seasons(-1));
        TextView state = row(text(R.string.TjMediaLoading), null);
        numbers(season, after, (values, more) -> {
            removeView(state);
            if (values == null) { row(text(R.string.TjMediaRetry), () -> episodes(after)); return; }
            if (values.isEmpty()) row(text(R.string.TjMediaNoIndexedEpisodes), () -> seasons(-1));
            for (Map.Entry<Integer, NumberState> value : values.entrySet()) {
                int episode = value.getKey() == Integer.MAX_VALUE ? -1 : value.getKey();
                String label = episode < 0 ? text(R.string.TjMediaUnknownEpisode) : text(R.string.TjMediaEpisode) + " " + episode;
                NumberState stateValue = value.getValue();
                String progress = stateValue.inProgress ? " · " + text(R.string.TjMediaContinue)
                        : stateValue.watched ? " · " + text(R.string.TjMediaWatched) : "";
                row(label + "\n" + stateValue.sources + " " + text(R.string.TjMediaSources) + progress, () -> sourceAccount(episode));
            }
            if (after >= 0) row(text(R.string.TjMediaFirstEpisodes), () -> episodes(-1));
            if (more && !values.isEmpty()) row(text(R.string.TjMediaLoadMore), () -> episodes(values.lastKey()));
        });
    }

    private void showMovieSources() { row(text(R.string.TjMediaVersions), () -> sourceAccount(TjMediaStore.ANY_EPISODE)); }

    private void sourceAccount(int episode) { sourceAccount(episode, title.isSeries() ? season : TjMediaStore.ANY_EPISODE); }

    private void sourceAccount(int episode, int selectedSeason) {
        if (accounts.size() == 1) { sources(accounts.get(0), selectedSeason, episode, null); return; }
        ArrayList<CharSequence> labels = new ArrayList<>();
        for (int account : accounts) labels.add(UserObject.getUserName(UserConfig.getInstance(account).getCurrentUser()));
        host.showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaAccounts))
                .setItems(labels.toArray(new CharSequence[0]), (d, index) -> sources(accounts.get(index), selectedSeason, episode, null)).create());
    }

    public void openNext() {
        if (closed || title.season() < 0 || title.episode() < 0) return;
        numbers(title.season(), order(title.episode()), (values, more) -> {
            if (values == null) { error(this::openNext); return; }
            if (!values.isEmpty() && values.firstKey() != Integer.MAX_VALUE) {
                sourceAccount(values.firstKey(), title.season()); return;
            }
            numbers(TjMediaStore.ANY_EPISODE, order(title.season()), true, (seasons, hasMore) -> {
                if (seasons == null) { error(this::openNext); return; }
                if (seasons.isEmpty() || seasons.firstKey() == Integer.MAX_VALUE) { noNext(); return; }
                int nextSeason = seasons.firstKey();
                numbers(nextSeason, -1, (episodes, ignored) -> {
                    if (episodes == null) { error(this::openNext); return; }
                    if (episodes.isEmpty() || episodes.firstKey() == Integer.MAX_VALUE) { noNext(); return; }
                    sourceAccount(episodes.firstKey(), nextSeason);
                });
            });
        });
    }

    private void noNext() {
        if (!closed) host.showDialog(new AlertDialog.Builder(getContext()).setMessage(text(R.string.TjMediaNoNextEpisode))
                .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
    }

    private void sources(int account, int selectedSeason, int episode, TjMediaPageKey after) {
        if (!active(account)) return;
        int request = generation, sourceToken = ++sourceRequest;
        TjMediaStore.getInstance().loadCatalogSources(account, title, selectedSeason,
                episode, after, sources.get(owners.get(account)), sourceType, page -> {
                    if (request != generation || sourceToken != sourceRequest || !active(account)) return;
                    if (page == null) { error(() -> sources(account, selectedSeason, episode, after)); return; }
                    ArrayList<CharSequence> names = new ArrayList<>();
                    for (TjMediaStore.Record record : page) {
                        MessageObject message = record.message;
                        long did = message.getDialogId();
                        MessagesController controller = MessagesController.getInstance(account);
                        TLRPC.Chat chat = did < 0 ? controller.getChat(-did) : null;
                        String peer = did > 0 ? UserObject.getUserName(controller.getUser(did)) : chat == null ? Long.toString(did) : chat.title;
                        String sender = "";
                        if (message.messageOwner.from_id instanceof TLRPC.TL_peerUser)
                            sender = UserObject.getUserName(controller.getUser(message.messageOwner.from_id.user_id));
                        names.add(message.getDocumentName() + "\n" + peer + (sender.isEmpty() ? "" : " · " + sender)
                                + (message.getDocument() == null ? "" : " · " + AndroidUtilities.formatFileSize(message.getDocument().size))
                                + (record.watched || record.duration > 0 && record.position >= record.duration * .98 ? " · " + text(R.string.TjMediaWatched)
                                : record.position > 0 && record.duration > 0 ? " · " + text(R.string.TjMediaContinue) : ""));
                    }
                    if (page.hasMore) names.add(text(R.string.TjMediaLoadMore));
                    AlertDialog.Builder dialog = new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaVersions));
                    if (names.isEmpty()) dialog.setMessage(text(R.string.TjMediaEmpty));
                    else dialog.setItems(names.toArray(new CharSequence[0]), (d, index) -> {
                        if (index == page.size()) sources(account, selectedSeason, episode, page.nextKey);
                        else open.source(new TjMediaLibrary.Entry(account, owners.get(account), page.get(index).message));
                    });
                    host.showDialog(dialog.setNegativeButton(LocaleController.getString(R.string.Close), null).create());
                });
    }

    private void error(Runnable retry) {
        host.showDialog(new AlertDialog.Builder(getContext()).setMessage(text(R.string.TjMediaLookupError))
                .setPositiveButton(LocaleController.getString(R.string.Retry), (d, w) -> retry.run())
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
    }
}
