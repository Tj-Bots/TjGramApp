package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.messenger.tj.TjMessageMarks;
import org.telegram.ui.ActionBar.Theme;

/**
 * The deleted and edited marks, chosen by looking at them: a message bubble showing the marks as
 * they will appear, a grid of icons for each mark, and the colours they can take. A tap chooses.
 */
public class TjMarksPickerView extends LinearLayout {

    private final TextView previewTime;
    private final LinearLayout previewBubble;
    private final LinearLayout deletedGrid, editedGrid, colorRow;

    public TjMarksPickerView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(0, 0, 0, dp(12));

        // A chat-coloured strip with one incoming bubble on it, as the settings of the big clients do.
        FrameLayout preview = new FrameLayout(context);
        preview.setBackgroundColor(ColorUtils.blendARGB(Theme.getColor(Theme.key_windowBackgroundWhite),
                Theme.getColor(Theme.key_windowBackgroundGray), 0.6f));
        previewBubble = new LinearLayout(context);
        previewBubble.setOrientation(VERTICAL);
        previewBubble.setPadding(dp(12), dp(8), dp(12), dp(6));
        previewBubble.setBackground(Theme.createRoundRectDrawable(dp(16), Theme.getColor(Theme.key_chat_inBubble)));
        TextView previewText = new TextView(context);
        previewText.setTextSize(15);
        previewText.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
        previewText.setText(TjLocale.getString(R.string.TjMarksPreviewText));
        previewBubble.addView(previewText, LayoutHelper.createLinear(-2, -2));
        previewTime = new TextView(context);
        previewTime.setTextSize(12);
        previewTime.setTextColor(Theme.getColor(Theme.key_chat_inTimeText));
        previewBubble.addView(previewTime, LayoutHelper.createLinear(-2, -2, Gravity.RIGHT, 0, 2, 0, 0));
        preview.addView(previewBubble, LayoutHelper.createFrame(-2, -2,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 14, 16, 14));
        addView(preview, LayoutHelper.createLinear(-1, -2));

        addView(label(context, R.string.TjDeletedMarker), LayoutHelper.createLinear(-1, -2, 20, 14, 20, 6));
        deletedGrid = row(context);
        for (String key : TjMessageMarks.DELETED) {
            deletedGrid.addView(new MarkOption(context, key, null, () -> {
                TjMessageMarks.setDeleted(key);
                refresh();
            }), LayoutHelper.createLinear(-2, 40, 0, 0, 8, 0));
        }
        addView(scroller(context, deletedGrid), LayoutHelper.createLinear(-1, -2));

        addView(label(context, R.string.TjEditedMarker), LayoutHelper.createLinear(-1, -2, 20, 14, 20, 6));
        editedGrid = row(context);
        for (String key : TjMessageMarks.EDITED) {
            String text = "text".equals(key) ? LocaleController.getString(R.string.EditedMessage) : null;
            editedGrid.addView(new MarkOption(context, key, text, () -> {
                TjMessageMarks.setEdited(key);
                refresh();
            }), LayoutHelper.createLinear(-2, 40, 0, 0, 8, 0));
        }
        addView(scroller(context, editedGrid), LayoutHelper.createLinear(-1, -2));

        addView(label(context, R.string.TjMarksColor), LayoutHelper.createLinear(-1, -2, 20, 14, 20, 6));
        colorRow = row(context);
        for (int color : TjMessageMarks.COLORS) {
            colorRow.addView(new ColorOption(context, color, () -> {
                TjConfig.put("marks_color", color);
                refresh();
            }), LayoutHelper.createLinear(34, 34, 0, 0, 10, 0));
        }
        addView(scroller(context, colorRow), LayoutHelper.createLinear(-1, -2));

        refresh();
    }

    private static TextView label(Context context, int text) {
        TextView view = new TextView(context);
        view.setTextSize(14);
        view.setTypeface(AndroidUtilities.bold());
        view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        view.setText(TjLocale.getString(text));
        return view;
    }

    public void refresh() {
        String deleted = TjMessageMarks.deletedKey();
        String edited = TjMessageMarks.editedKey();
        int color = TjMessageMarks.color();
        for (int i = 0; i < deletedGrid.getChildCount(); i++) {
            MarkOption option = (MarkOption) deletedGrid.getChildAt(i);
            option.setSelectedMark(option.key.equals(deleted));
        }
        for (int i = 0; i < editedGrid.getChildCount(); i++) {
            MarkOption option = (MarkOption) editedGrid.getChildAt(i);
            option.setSelectedMark(option.key.equals(edited));
        }
        for (int i = 0; i < colorRow.getChildCount(); i++) {
            ColorOption option = (ColorOption) colorRow.getChildAt(i);
            option.setSelectedColor(option.color == color);
        }
        // The same line the chat builds for an edited message that was then deleted.
        CharSequence time = TextUtils.concat(TjMessageMarks.edited(LocaleController.getString(R.string.EditedMessage)),
                " (", TjMessageMarks.deleted(), ") ", "03:06");
        previewTime.setText(time);
        previewBubble.setAlpha(TjConfig.dimDeletedMessages() ? 0.6f : 1f);
    }

    /** One line of choices, read from the reading side. */
    private static LinearLayout row(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        row.setPadding(dp(20), dp(2), dp(12), dp(4));
        return row;
    }

    /** A row that slides sideways when it is wider than the screen, instead of breaking in two. */
    private static View scroller(Context context, LinearLayout row) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        scroll.addView(row, new android.widget.HorizontalScrollView.LayoutParams(-2, -2));
        return scroll;
    }

    /** One mark: its icon (or the word) on a soft rounded chip that takes the accent when chosen. */
    private static class MarkOption extends View {
        final String key;
        private final String text;
        private final Drawable icon;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private boolean chosen;

        MarkOption(Context context, String key, String text, Runnable onPick) {
            super(context);
            this.key = key;
            this.text = text;
            icon = text == null ? ContextCompat.getDrawable(context, TjMessageMarks.iconRes(key)).mutate() : null;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(1.5f));
            textPaint.setTextSize(dp(13));
            textPaint.setTypeface(AndroidUtilities.bold());
            setOnClickListener(v -> onPick.run());
            setContentDescription(text != null ? text : key);
            ScaleStateListAnimator.apply(this, 0.1f, 1.5f);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = text != null ? (int) Math.ceil(textPaint.measureText(text)) + dp(28) : dp(48);
            setMeasuredDimension(width, dp(40));
        }

        void setSelectedMark(boolean value) {
            chosen = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            int plain = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy);
            android.graphics.RectF rect = AndroidUtilities.rectTmp;
            rect.set(dp(1), dp(1), getWidth() - dp(1), getHeight() - dp(1));
            fill.setColor(chosen ? ColorUtils.setAlphaComponent(accent, 0x2E)
                    : ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0x0F));
            canvas.drawRoundRect(rect, dp(12), dp(12), fill);
            if (chosen) {
                ring.setColor(accent);
                canvas.drawRoundRect(rect, dp(12), dp(12), ring);
            }
            int color = chosen ? accent : plain;
            if (icon != null) {
                int size = dp(20);
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                icon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
                icon.draw(canvas);
            } else {
                textPaint.setColor(color);
                String shown = text;
                float w = textPaint.measureText(shown);
                canvas.drawText(shown, cx - w / 2f, cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
            }
        }
    }

    /** A colour circle; the chosen one is ringed with a gap, the first one follows the time text. */
    private static class ColorOption extends View {
        final int color;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean chosen;

        ColorOption(Context context, int color, Runnable onPick) {
            super(context);
            this.color = color;
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dp(2));
            setOnClickListener(v -> onPick.run());
            ScaleStateListAnimator.apply(this, 0.1f, 1.5f);
        }

        void setSelectedColor(boolean value) {
            chosen = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int shown = color != 0 ? color : Theme.getColor(Theme.key_chat_inTimeText);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(1);
            fill.setColor(shown);
            canvas.drawCircle(cx, cy, radius, fill);
            if (chosen) {
                // A white tick on the chosen colour, as a colour picker marks it.
                ring.setColor(0xFFFFFFFF);
                ring.setStrokeCap(Paint.Cap.ROUND);
                ring.setStrokeJoin(Paint.Join.ROUND);
                android.graphics.Path tick = new android.graphics.Path();
                tick.moveTo(cx - radius * 0.38f, cy + radius * 0.02f);
                tick.lineTo(cx - radius * 0.08f, cy + radius * 0.32f);
                tick.lineTo(cx + radius * 0.42f, cy - radius * 0.28f);
                canvas.drawPath(tick, ring);
            }
        }
    }
}
