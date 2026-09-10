package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.TjSettingsStyle;

/**
 * A category on the TjGram settings home: a coloured icon badge, a name, and a line saying what is
 * inside. The subtitle is the point - the old list was five words with no hint of what each screen
 * held, so everything had to be opened to be found.
 */
public class TjCategoryCell extends FrameLayout {

    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView arrowView;
    private boolean needDivider;

    public TjCategoryCell(Context context) {
        super(context);
        boolean rtl = LocaleController.isRTL;
        int start = rtl ? Gravity.RIGHT : Gravity.LEFT;

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        addView(iconView, LayoutHelper.createFrame(34, 34, start | Gravity.CENTER_VERTICAL,
                rtl ? 0 : 17, 0, rtl ? 17 : 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(16);
        titleView.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(start | Gravity.CENTER_VERTICAL);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                start | Gravity.TOP, rtl ? 44 : 65, 11, rtl ? 65 : 44, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitleView.setLines(1);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setGravity(start | Gravity.CENTER_VERTICAL);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                start | Gravity.TOP, rtl ? 44 : 65, 34, rtl ? 65 : 44, 0));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.msg_arrowright);
        arrowView.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.MULTIPLY));
        arrowView.setRotation(rtl ? 180 : 0);
        arrowView.setAlpha(0.6f);
        addView(arrowView, LayoutHelper.createFrame(24, 24,
                (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL,
                rtl ? 14 : 0, 0, rtl ? 0 : 14, 0));
    }

    public void set(int iconResource, int badgeColor, CharSequence title, CharSequence subtitle, boolean divider) {
        iconView.setImageResource(iconResource);
        iconView.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        iconView.setBackground(TjSettingsStyle.badge(badgeColor));
        iconView.setPadding(dp(6), dp(6), dp(6), dp(6));
        titleView.setText(title);
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        titleView.setTranslationY(TextUtils.isEmpty(subtitle) ? dp(8) : 0);
        needDivider = divider;
        setWillNotDraw(!divider);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(subtitleView.getVisibility() == VISIBLE ? 64 : 52), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!needDivider) {
            return;
        }
        boolean rtl = LocaleController.isRTL;
        canvas.drawLine(rtl ? 0 : dp(65), getMeasuredHeight() - 1,
                getMeasuredWidth() - (rtl ? dp(65) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Button");
        info.setText(titleView.getText() + ", " + subtitleView.getText());
    }
}
