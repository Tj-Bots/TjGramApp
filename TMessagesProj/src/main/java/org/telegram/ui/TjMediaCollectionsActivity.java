package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.view.Gravity;
import android.view.ViewGroup;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.messenger.*;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.ui.ActionBar.*;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Owner-scoped list directory. Empty lists do not depend on a media row. */
public final class TjMediaCollectionsActivity extends BaseFragment {
    public interface Open { void list(int account, String name); }
    private final ArrayList<Integer> accounts;
    private final HashMap<Integer, Long> owners = new HashMap<>();
    private final Open open;
    private final ArrayList<ListRow> rows = new ArrayList<>();
    private RecyclerListView list;
    private TextView empty;
    private int pending;
    private boolean failed;
    private static final class ListRow {
        final int account;
        final TjMediaStore.CollectionSummary summary;
        ListRow(int account, TjMediaStore.CollectionSummary summary) { this.account = account; this.summary = summary; }
    }
    private int generation;
    private boolean destroyed;

    public TjMediaCollectionsActivity(int account, List<Integer> accounts, Open open) {
        setCurrentAccount(account); this.accounts = new ArrayList<>(accounts); this.open = open;
        for (int id : accounts) owners.put(id, UserConfig.getInstance(id).getClientUserId());
    }
    private static String text(int id) { return TjLocale.getString(id); }
    private boolean active(int id) { return !destroyed && owners.containsKey(id)
            && UserConfig.getInstance(id).isClientActivated() && owners.get(id) == UserConfig.getInstance(id).getClientUserId(); }
    @Override public View createView(Context context) {
        actionBar.setTitle(text(R.string.TjMediaListsTab)); actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        fragmentView = root;
        list = new RecyclerListView(context);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setPadding(0, 0, 0, AndroidUtilities.dp(88));
        list.setClipToPadding(false);
        list.setAdapter(new DirectoryAdapter());
        list.setOnItemClickListener((view, position) -> {
            if (position == rows.size() && failed && pending == 0) { load(); return; }
            if (position < 0 || position >= rows.size()) return;
            ListRow row = rows.get(position);
            if (!active(row.account)) return;
            open.list(row.account, row.summary.name);
        });
        list.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return false;
            ListRow row = rows.get(position);
            if (!active(row.account)) return false;
            manage(row.account, row.summary.name); return true;
        });
        root.addView(list, LayoutHelper.createFrame(-1, -1));
        empty = new TextView(context);
        empty.setTextSize(16); empty.setGravity(Gravity.CENTER);
        empty.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), AndroidUtilities.dp(80));
        empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        empty.setOnClickListener(v -> { if (failed) load(); });
        root.addView(empty, LayoutHelper.createFrame(-1, -1));
        ImageView add = new ImageView(context);
        add.setImageResource(R.drawable.msg_add);
        add.setScaleType(ImageView.ScaleType.CENTER);
        add.setColorFilter(Theme.getColor(Theme.key_chats_actionIcon));
        add.setBackground(Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56),
                Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground)));
        add.setElevation(AndroidUtilities.dp(4));
        add.setContentDescription(text(R.string.TjMediaNewCollection));
        add.setOnClickListener(v -> create());
        root.addView(add, LayoutHelper.createFrame(56, 56,
                Gravity.BOTTOM | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 16, 16, 16, 16));
        load(); return root;
    }
    private void load() {
        int request = ++generation;
        rows.clear(); pending = 0; failed = false;
        for (int account : accounts) if (active(account)) pending++;
        updateDirectory();
        for (int account : accounts) if (active(account)) {
            TjMediaStore.getInstance().collectionSummaries(account, summaries -> {
                if (request != generation || destroyed) return;
                pending--;
                if (active(account)) {
                    if (summaries == null) failed = true;
                    else for (TjMediaStore.CollectionSummary summary : summaries) rows.add(new ListRow(account, summary));
                }
                rows.sort((a, b) -> {
                    int result = a.summary.name.compareToIgnoreCase(b.summary.name);
                    return result != 0 ? result : Integer.compare(accounts.indexOf(a.account), accounts.indexOf(b.account));
                });
                updateDirectory();
            });
        }
    }
    private void updateDirectory() {
        list.getAdapter().notifyDataSetChanged();
        empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(text(pending > 0 ? R.string.Loading : failed ? R.string.TjMediaLookupError : R.string.TjMediaListsEmpty));
    }

    private class DirectoryAdapter extends RecyclerListView.SelectionAdapter {
        @Override public int getItemCount() { return rows.size() + (!rows.isEmpty() && failed && pending == 0 ? 1 : 0); }
        @Override public int getItemViewType(int position) { return position == rows.size() ? 1 : 0; }
        @Override public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int type) {
            if (type == 1) return new RecyclerListView.Holder(new TextSettingsCell(parent.getContext()));
            return new RecyclerListView.Holder(new org.telegram.ui.Cells.TjMediaCollectionCell(parent.getContext()));
        }
        @Override public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.itemView instanceof TextSettingsCell) {
                ((TextSettingsCell) holder.itemView).setText(text(R.string.TjMediaLookupError), false);
                return;
            }
            ListRow row = rows.get(position);
            String account = accounts.size() > 1 ? UserObject.getUserName(UserConfig.getInstance(row.account).getCurrentUser()) : "";
            ((org.telegram.ui.Cells.TjMediaCollectionCell) holder.itemView).bind(row.summary, account);
            ((org.telegram.ui.Cells.TjMediaCollectionCell) holder.itemView).setOptionsAction(() -> {
                if (active(row.account)) manage(row.account, row.summary.name);
            });
        }
    }
    private void create() {
        ArrayList<Integer> ids = new ArrayList<>(); ArrayList<CharSequence> names = new ArrayList<>();
        for (int id : accounts) if (active(id)) { ids.add(id); names.add(UserObject.getUserName(UserConfig.getInstance(id).getCurrentUser())); }
        if (ids.size() == 1) edit(ids.get(0), null);
        else if (!ids.isEmpty()) showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaAccounts))
                .setItems(names.toArray(new CharSequence[0]), (d, which) -> edit(ids.get(which), null)).create());
    }
    private void manage(int account, String name) {
        showDialog(new AlertDialog.Builder(getContext()).setTitle(name)
                .setItems(new CharSequence[]{LocaleController.getString(R.string.Edit), LocaleController.getString(R.string.Delete)}, (d, which) -> {
                    if (which == 0) edit(account, name);
                    else showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaDeleteCollection))
                            .setMessage(text(R.string.TjMediaDeleteCollectionInfo))
                            .setPositiveButton(LocaleController.getString(R.string.Delete), (dialog, button) -> save(account, name, null))
                            .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
                }).create());
    }
    private void edit(int account, String old) {
        if (!active(account)) return;
        String icon = "";
        for (ListRow row : rows) if (row.account == account && row.summary.name.equals(old)) icon = row.summary.icon;
        presentFragment(new TjMediaCollectionEditActivity(account, old, icon, ignored -> { if (active(account)) load(); }));
    }
    private void save(int account, String old, String name) {
        if (!active(account)) return;
        TjMediaStore.getInstance().editCollection(account, old, name, success -> {
            if (!active(account)) return;
            if (success) load();
            else showDialog(new AlertDialog.Builder(getContext()).setMessage(text(R.string.TjMediaSaveError))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null).create());
        });
    }
    @Override public void onFragmentDestroy() { destroyed = true; generation++; super.onFragmentDestroy(); }
}
