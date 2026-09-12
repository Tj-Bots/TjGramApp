package org.telegram.ui.Components;

import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjLocalFolders;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.FilterCreateActivity;

public final class TjLocalFoldersUi {
    private TjLocalFoldersUi() {}

    public static void showPicker(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
        int account = fragment.getCurrentAccount();
        LinearLayout rows = new LinearLayout(fragment.getParentActivity());
        rows.setOrientation(LinearLayout.VERTICAL);
        CheckBoxCell[] cells = new CheckBoxCell[TjLocalFolders.IDS.length];
        for (int i = 0; i < cells.length; i++) {
            int id = TjLocalFolders.IDS[i];
            CheckBoxCell cell = new CheckBoxCell(fragment.getParentActivity(), 1);
            cell.setText(TjLocalFolders.title(id), "", TjLocalFolders.enabled(account, id), i + 1 < cells.length);
            cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked(), true));
            rows.addView(cell, new LinearLayout.LayoutParams(-1, AndroidUtilities.dp(48)));
            cells[i] = cell;
        }
        ScrollView scroll = new ScrollView(fragment.getParentActivity());
        scroll.addView(rows);
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(TjLocale.getString(R.string.TjLocalFolders));
        builder.setMessage(TjLocale.getString(R.string.TjLocalFoldersInfo));
        builder.setView(scroll);
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.Save), (dialog, which) -> {
            for (int i = 0; i < cells.length; i++) TjLocalFolders.setEnabled(account, TjLocalFolders.IDS[i], cells[i].isChecked());
            TjLocalFolders.refresh(account);
        });
        if (fragment.showDialog(builder.create()) != null) TjLocalFolders.markOffered(account);
    }

    public static void edit(BaseFragment fragment, MessagesController.DialogFilter filter) {
        if (filter.id == TjLocalFolders.FAVORITES) {
            fragment.presentFragment(new FilterCreateActivity(filter));
        } else if (fragment.getParentActivity() != null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
            builder.setTitle(TjLocalFolders.title(filter.id));
            builder.setMessage(TjLocalFolders.description(filter.id));
            builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
            fragment.showDialog(builder.create());
        }
    }

    public static void reset(BaseFragment fragment) {
        if (fragment.getParentActivity() == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(TjLocale.getString(R.string.TjLocalFoldersReset));
        builder.setMessage(TjLocale.getString(R.string.TjLocalFoldersResetInfo));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialog, which) -> {
            TjLocalFolders.reset(fragment.getCurrentAccount());
            showPicker(fragment);
        });
        fragment.showDialog(builder.create());
    }
}
