package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Dialog;
import android.content.Context;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.RadioColorCell;

/**
 * A choice between a few options, as Telegram's own settings ask it: a hollow circle by every
 * option and a filled one by the current choice, rather than a list with a tick glued to a label.
 */
public final class TjChoiceDialog {

    private TjChoiceDialog() {
    }

    public static void show(BaseFragment fragment, CharSequence title, CharSequence[] labels, int selected,
                            Utilities.Callback<Integer> onPick) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        Context context = fragment.getParentActivity();
        final Dialog[] dialog = new Dialog[1];
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        final RadioColorCell[] cells = new RadioColorCell[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            RadioColorCell cell = new RadioColorCell(context, fragment.getResourceProvider());
            cell.setPadding(dp(4), 0, dp(4), 0);
            cell.setCheckColor(Theme.getColor(Theme.key_radioBackground), Theme.getColor(Theme.key_dialogRadioBackgroundChecked));
            cell.setTextAndValue(labels[i], i == selected);
            cell.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), Theme.RIPPLE_MASK_ALL));
            cell.setOnClickListener(v -> {
                for (int a = 0; a < cells.length; a++) cells[a].setChecked(a == index, true);
                // A moment for the circle to fill before the dialog goes.
                AndroidUtilities.runOnUIThread(() -> {
                    if (dialog[0] != null) dialog[0].dismiss();
                    onPick.run(index);
                }, 160);
            });
            cells[i] = cell;
            column.addView(cell);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(column);
        dialog[0] = new AlertDialog.Builder(context, fragment.getResourceProvider())
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .create();
        fragment.showDialog(dialog[0]);
    }
}
