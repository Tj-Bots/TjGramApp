package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;

/**
 * A poster held down in Watch: the rest of the screen dims, the poster comes up larger with its
 * name under it, and a short menu below says what can be done with it - the way a phone shows an
 * app icon or a photo held down. A tap anywhere else puts it back.
 */
public final class TjPosterPreview {

    public static final class Option {
        final int icon;
        final CharSequence text;
        final Runnable action;
        final boolean accent;

        public Option(int icon, CharSequence text, boolean accent, Runnable action) {
            this.icon = icon;
            this.text = text;
            this.accent = accent;
            this.action = action;
        }
    }

    private TjPosterPreview() {
    }

    public static void show(Context context, String posterUrl, String title, String subtitle, ArrayList<Option> options) {
        if (context == null) return;
        Dialog dialog = new Dialog(context, R.style.TransparentDialogNoAnimation);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(0x00000000);

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClickable(true);

        int width = Math.min(dp(220), (int) (AndroidUtilities.displaySize.x * 0.55f));
        BackupImageView poster = new BackupImageView(context);
        poster.setRoundRadius(dp(14));
        if (!TextUtils.isEmpty(posterUrl)) {
            poster.setImage(posterUrl, "320_480", new ColorDrawable(0x33ffffff));
        }
        column.addView(poster, new LinearLayout.LayoutParams(width, width * 3 / 2));

        TextView name = new TextView(context);
        name.setText(title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(17);
        name.setTypeface(AndroidUtilities.bold());
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        column.addView(name, LayoutHelper.createLinear(-1, -2, 24, 12, 24, 0));
        if (!TextUtils.isEmpty(subtitle)) {
            TextView meta = new TextView(context);
            meta.setText(subtitle);
            meta.setTextColor(0xb3ffffff);
            meta.setTextSize(13);
            meta.setGravity(Gravity.CENTER);
            column.addView(meta, LayoutHelper.createLinear(-1, -2, 24, 2, 24, 0));
        }

        // The menu: a rounded card in the menu colours, one row per action.
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setBackground(Theme.createRoundRectDrawable(dp(14), Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground)));
        menu.setPadding(0, dp(6), 0, dp(6));
        for (Option option : options) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(LocaleController.isRTL ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
            row.setPadding(dp(18), 0, dp(18), 0);
            row.setBackground(Theme.getSelectorDrawable(false));
            int color = option.accent ? Theme.getColor(Theme.key_text_RedRegular)
                    : Theme.getColor(Theme.key_actionBarDefaultSubmenuItem);
            ImageView icon = new ImageView(context);
            icon.setImageResource(option.icon);
            icon.setColorFilter(new PorterDuffColorFilter(option.accent ? color
                    : Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.SRC_IN));
            row.addView(icon, LayoutHelper.createLinear(24, 24));
            TextView text = new TextView(context);
            text.setText(option.text);
            text.setTextSize(16);
            text.setTextColor(color);
            text.setSingleLine(true);
            row.addView(text, LayoutHelper.createLinear(-2, -2, 16, 0, 16, 0));
            row.setOnClickListener(v -> {
                dialog.dismiss();
                if (option.action != null) option.action.run();
            });
            menu.addView(row, LayoutHelper.createLinear(-1, 48));
        }
        column.addView(menu, new LinearLayout.LayoutParams(Math.max(width, dp(240)), ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) menu.getLayoutParams()).topMargin = dp(16);

        root.addView(column, LayoutHelper.createFrame(-1, -2, Gravity.CENTER));
        root.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(0));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }
        dialog.show();

        // In: the dim fades up and the poster grows into place; out the same way, faster.
        root.setBackgroundColor(0);
        column.setAlpha(0f);
        column.setScaleX(0.85f);
        column.setScaleY(0.85f);
        android.animation.ValueAnimator dim = android.animation.ValueAnimator.ofFloat(0f, 1f).setDuration(220);
        dim.addUpdateListener(a -> root.setBackgroundColor(Color.argb((int) (0xB8 * (float) a.getAnimatedValue()), 0, 0, 0)));
        dim.start();
        column.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();
        root.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
    }
}
