package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private LinearLayout body;
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
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body); fragmentView = scroll; load(); return scroll;
    }
    private TextSettingsCell row(String name, Runnable action) {
        TextSettingsCell cell = new TextSettingsCell(getContext()); cell.setText(name, true);
        cell.setBackground(Theme.getSelectorDrawable(false)); cell.setOnClickListener(v -> action.run());
        body.addView(cell, LayoutHelper.createLinear(-1, 56)); return cell;
    }
    private void load() {
        int request = ++generation; body.removeAllViews();
        row(text(R.string.TjMediaNewCollection), this::create);
        for (int account : accounts) if (active(account)) {
            TjMediaStore.getInstance().collections(account, names -> {
                if (request != generation || !active(account)) return;
                if (names == null) { row(text(R.string.TjMediaLookupError), this::load); return; }
                for (String name : names) {
                    String label = accounts.size() > 1 ? name + " · " + UserObject.getUserName(UserConfig.getInstance(account).getCurrentUser()) : name;
                    TextSettingsCell cell = row(label, () -> {
                        if (!active(account)) return;
                        finishFragment(); open.list(account, name);
                    });
                    cell.setOnLongClickListener(v -> { manage(account, name); return true; });
                }
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
        EditTextBoldCursor input = new EditTextBoldCursor(getContext()); input.setSingleLine(true);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint(text(R.string.TjMediaCollection)); input.setText(old == null ? "" : old);
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(128)});
        input.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));
        showDialog(new AlertDialog.Builder(getContext()).setTitle(text(R.string.TjMediaCollection)).setView(input)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> save(account, old, input.getText().toString()))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null).create());
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
