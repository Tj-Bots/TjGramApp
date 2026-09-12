package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjMediaStore;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/** Compact chat-style directory row; no media fetches are needed to browse lists. */
public final class TjMediaCollectionCell extends LinearLayout {
    private final TextView name, details, preview;
    public TjMediaCollectionCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        setBackground(Theme.getSelectorDrawable(false));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_list);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(26), Theme.getColor(Theme.key_windowBackgroundGray)));
        addView(icon, LayoutHelper.createLinear(52, 52));
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(VERTICAL);
        labels.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(8), 0);
        name = label(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        name.setTypeface(AndroidUtilities.bold());
        details = label(context, 13, Theme.key_windowBackgroundWhiteBlueText);
        preview = label(context, 14, Theme.key_windowBackgroundWhiteGrayText);
        labels.addView(name); labels.addView(details); labels.addView(preview);
        addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private TextView label(Context context, int size, int color) {
        TextView text = new TextView(context); text.setTextSize(size);
        text.setTextColor(Theme.getColor(color)); text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setGravity(Gravity.START); return text;
    }
    public void bind(TjMediaStore.CollectionSummary summary, String account) {
        name.setText(summary.name);
        String count = String.format(java.util.Locale.getDefault(), TjLocale.getString(R.string.TjMediaListItemCount), summary.count);
        details.setText(account.isEmpty() ? count : count + " · " + account);
        preview.setText(summary.preview == null || summary.preview.isEmpty() ? TjLocale.getString(R.string.TjMediaListEmpty) : summary.preview.replace('\n', ' '));
    }
}
