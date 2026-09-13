package org.telegram.ui.Components;

import android.view.Gravity;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/** Shared, visible input styling for media dialogs and editors. */
public final class TjMediaInputStyle {
    private TjMediaInputStyle() { }
    public static void addLabel(android.widget.LinearLayout parent, EditTextBoldCursor field) {
        android.widget.TextView label = new android.widget.TextView(parent.getContext());
        label.setText(field.getContentDescription()); label.setTextSize(13);
        label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        label.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        label.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), 0);
        if (field.getId() == android.view.View.NO_ID) field.setId(android.view.View.generateViewId());
        label.setLabelFor(field.getId());
        parent.addView(label, LayoutHelper.createLinear(-1, -2));
    }
    public static void apply(EditTextBoldCursor field, CharSequence hint) {
        field.setTextSize(16); field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        field.setHint(hint); field.setContentDescription(hint);
        field.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        field.setMinHeight(AndroidUtilities.dp(52));
        field.setBackground(Theme.createEditTextDrawable(field.getContext(), false));
        field.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
    }
}
