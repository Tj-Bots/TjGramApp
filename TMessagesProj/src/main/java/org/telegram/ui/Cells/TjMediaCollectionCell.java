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
public final class TjMediaCollectionCell extends android.widget.FrameLayout {
    private final TextView name, details, preview;
    private final ImageView icon;
    private final ImageView options;
    public TjMediaCollectionCell(Context context) {
        super(context);
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        setBackground(Theme.getSelectorDrawable(false));
        icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_list);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        icon.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(26), Theme.getColor(Theme.key_windowBackgroundGray)));
        addView(icon, LayoutHelper.createFrame(52, 52, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT)));
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL);
        name = label(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        name.setTypeface(AndroidUtilities.bold());
        details = label(context, 13, Theme.key_windowBackgroundWhiteBlueText);
        preview = label(context, 14, Theme.key_windowBackgroundWhiteGrayText);
        labels.addView(name, LayoutHelper.createLinear(-1, -2));
        labels.addView(details, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        labels.addView(preview, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        addView(labels, LayoutHelper.createFrame(-1, -2, Gravity.CENTER_VERTICAL,
                LocaleController.isRTL ? 36 : 66, 0, LocaleController.isRTL ? 66 : 36, 0));
        options = new ImageView(context);
        options.setImageResource(R.drawable.ic_ab_other);
        options.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        options.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        options.setContentDescription(LocaleController.getString(R.string.AccDescrMoreOptions));
        options.setBackground(Theme.getSelectorDrawable(false));
        addView(options, LayoutHelper.createFrame(36, 48, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT)));
        setMinimumHeight(AndroidUtilities.dp(84));
    }
    private TextView label(Context context, int size, int color) {
        TextView text = new TextView(context); text.setTextSize(size);
        text.setTextColor(Theme.getColor(color)); text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT); return text;
    }
    public void bind(TjMediaStore.CollectionSummary summary, String account) {
        name.setText(summary.name);
        icon.setImageResource(org.telegram.ui.Components.TjFolderIcons.getTabIcon(summary.icon));
        String count = String.format(java.util.Locale.getDefault(), TjLocale.getString(R.string.TjMediaListItemCount), summary.count);
        details.setText(account.isEmpty() ? count : count + " · " + account);
        preview.setText(summary.preview == null || summary.preview.isEmpty() ? TjLocale.getString(R.string.TjMediaListEmpty) : summary.preview.replace('\n', ' '));
    }
    public void setOptionsAction(Runnable action) { options.setOnClickListener(v -> action.run()); }
}
