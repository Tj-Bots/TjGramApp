package org.telegram.ui.Components;

import android.content.Context;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.tj.TjMediaLibrary;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TjMediaCardCell;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import android.os.Parcelable;
import org.telegram.messenger.tj.TjMediaCatalog;

/** Home shelves backed by the same filtered library as the full-grid views. */
public final class TjMediaHomeView extends ScrollView {
    public interface Delegate {
        void open(TjMediaLibrary.Entry entry);
        void view(int view);
    }
    private final LinearLayout content;
    private final Delegate delegate;
    private final HashMap<Integer, RecyclerListView> shelves = new HashMap<>();
    private final HashMap<Integer, Parcelable> shelfPositions = new HashMap<>();
    public TjMediaHomeView(Context context, Delegate delegate) {
        super(context);
        this.delegate = delegate;
        setFillViewport(true);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(24));
        addView(content, new ScrollView.LayoutParams(-1, -2));
    }

    public void bind(List<TjMediaLibrary.Entry> entries, Map<String, TjMediaStore.Record> records, boolean loading) {
        int scroll = getScrollY();
        for (Map.Entry<Integer, RecyclerListView> shelf : shelves.entrySet()) {
            shelfPositions.put(shelf.getKey(), shelf.getValue().getLayoutManager().onSaveInstanceState());
        }
        shelves.clear();
        content.removeAllViews();
        TextView heading = heading(TjLocale.getString(R.string.TjMediaYourLibrary));
        heading.setTextSize(27);
        content.addView(heading);
        if (entries.isEmpty()) {
            TextView empty = heading(TjLocale.getString(loading ? R.string.TjMediaLoading : R.string.TjMediaHomeEmpty));
            empty.setTextSize(16);
            content.addView(empty);
            return;
        }
        ArrayList<TjMediaLibrary.Entry> resume = new ArrayList<>(), favorites = new ArrayList<>(), movies = new ArrayList<>(), series = new ArrayList<>();
        HashSet<String> titles = new HashSet<>();
        for (TjMediaLibrary.Entry entry : entries) {
            TjMediaStore.Record record = records.get(entry.key);
            if (record == null) continue;
            if (record.position > 0 && record.duration > 0 && record.position < record.duration * .98 && !record.watched) resume.add(entry);
            if (record.favorite) favorites.add(entry);
            if (record.metadata != null && titles.add(TjMediaCatalog.key(record.metadata.id, record.metadata.series))) {
                (record.metadata.series ? series : movies).add(entry);
            }
        }
        resume.sort((a, b) -> Long.compare(records.get(b.key).playedAt, records.get(a.key).playedAt));
        TjMediaLibrary.Entry featured = !resume.isEmpty() ? resume.get(0) : !movies.isEmpty() ? movies.get(0) : entries.get(0);
        TjMediaCardCell hero = new TjMediaCardCell(getContext());
        bindCard(hero, featured, records.get(featured.key));
        hero.setOnClickListener(v -> delegate.open(featured));
        content.addView(hero, LayoutHelper.createLinear(-1, -2, 10, 0, 10, 8));
        shelf(R.string.TjMediaContinue, 1, resume, records);
        shelf(R.string.TjMediaRecentlyAdded, 0, entries, records);
        shelf(R.string.TjMediaFavorites, 3, favorites, records);
        shelf(R.string.TjMediaMovies, 7, movies, records);
        shelf(R.string.TjMediaSeries, 8, series, records);
        post(() -> scrollTo(0, scroll));
    }

    private TextView heading(String title) {
        TextView text = new TextView(getContext());
        text.setText(title);
        text.setTextSize(19);
        text.setMinHeight(AndroidUtilities.dp(48));
        androidx.core.view.ViewCompat.setAccessibilityHeading(text, true);
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        text.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        return text;
    }

    private void shelf(int title, int view, List<TjMediaLibrary.Entry> entries, Map<String, TjMediaStore.Record> records) {
        if (entries.isEmpty()) return;
        TextView heading = heading(TjLocale.getString(title) + "  ·  " + TjLocale.getString(R.string.TjMediaSeeAll));
        heading.setOnClickListener(v -> delegate.view(view));
        content.addView(heading);
        ArrayList<TjMediaLibrary.Entry> shown = new ArrayList<>(entries.subList(0, Math.min(20, entries.size())));
        RecyclerListView shelf = new RecyclerListView(getContext());
        shelf.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, LocaleController.isRTL));
        shelf.setClipToPadding(false);
        shelf.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        shelf.setAdapter(new RecyclerListView.SelectionAdapter() {
            @Override public int getItemCount() { return shown.size(); }
            @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
            @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
                TjMediaCardCell cell = new TjMediaCardCell(parent.getContext());
                cell.setLayoutParams(new RecyclerView.LayoutParams(AndroidUtilities.dp(220), -1));
                return new RecyclerListView.Holder(cell);
            }
            @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                TjMediaLibrary.Entry entry = shown.get(position);
                bindCard((TjMediaCardCell) holder.itemView, entry, records.get(entry.key));
            }
        });
        shelf.setOnItemClickListener((v, position) -> { if (position >= 0 && position < shown.size()) delegate.open(shown.get(position)); });
        Parcelable position = shelfPositions.get(view);
        if (position != null) shelf.getLayoutManager().onRestoreInstanceState(position);
        shelves.put(view, shelf);
        int shelfHeight = Math.max(220, 140 + (int) Math.ceil(65 * getResources().getConfiguration().fontScale));
        content.addView(shelf, LayoutHelper.createLinear(-1, shelfHeight));
    }

    private void bindCard(TjMediaCardCell cell, TjMediaLibrary.Entry entry, TjMediaStore.Record record) {
        String name = record != null && record.metadata != null ? record.metadata.name : entry.message.getDocumentName();
        if (name == null || name.isEmpty()) name = TjMediaStore.displayCaption(entry.message);
        if (name == null || name.isEmpty()) name = TjLocale.getString(R.string.TjMediaCenter);
        cell.bind(entry.message, name, UserObject.getUserName(UserConfig.getInstance(entry.account).getCurrentUser()),
                record == null ? 0 : record.position, record == null ? 0 : record.duration,
                record == null || record.metadata == null ? null : record.metadata.backdropUrl());
    }
}
