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
    private final Flow deletedGrid, editedGrid, colorRow;

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
        deletedGrid = new Flow(context);
        for (String key : TjMessageMarks.DELETED) {
            deletedGrid.addView(new MarkOption(context, key, null, () -> {
                TjMessageMarks.setDeleted(key);
                refresh();
            }));
        }
        addView(deletedGrid, LayoutHelper.createLinear(-1, -2, 14, 0, 14, 0));

        addView(label(context, R.string.TjEditedMarker), LayoutHelper.createLinear(-1, -2, 20, 14, 20, 6));
        editedGrid = new Flow(context);
        for (String key : TjMessageMarks.EDITED) {
            String text = "text".equals(key) ? LocaleController.getString(R.string.EditedMessage) : null;
            editedGrid.addView(new MarkOption(context, key, text, () -> {
                TjMessageMarks.setEdited(key);
                refresh();
            }));
        }
        addView(editedGrid, LayoutHelper.createLinear(-1, -2, 14, 0, 14, 0));

        addView(label(context, R.string.TjMarksColor), LayoutHelper.createLinear(-1, -2, 20, 14, 20, 6));
        colorRow = new Flow(context);
        for (int color : TjMessageMarks.COLORS) {
            colorRow.addView(new ColorOption(context, color, () -> {
                TjConfig.put("marks_color", color);
                refresh();
            }));
        }
        addView(colorRow, LayoutHelper.createLinear(-1, -2, 14, 0, 14, 0));

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

    /** Lays its children out in rows of equal cells, from the reading side. */
    private static class Flow extends ViewGroup {
        private static final int CELL = 48;

        Flow(Context context) {
            super(context);
        }

        private int columns(int width) {
            return Math.max(1, width / dp(CELL + 4));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int columns = columns(width);
            int rows = (getChildCount() + columns - 1) / columns;
            int cell = dp(CELL);
            for (int i = 0; i < getChildCount(); i++) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY));
            }
            setMeasuredDimension(width, rows * (cell + dp(6)));
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int width = r - l;
            int columns = columns(width);
            int cell = dp(CELL);
            int gap = columns > 1 ? (width - columns * cell) / (columns - 1) : 0;
            gap = Math.min(gap, dp(14));
            for (int i = 0; i < getChildCount(); i++) {
                int column = i % columns;
                int row = i / columns;
                int x = column * (cell + gap);
                if (LocaleController.isRTL) x = width - x - cell;
                int y = row * (cell + dp(6));
                getChildAt(i).layout(x, y, x + cell, y + cell);
            }
        }
    }

    /** One mark: its icon (or the word) in a circle that fills in when chosen. */
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
            ring.setStrokeWidth(dp(2));
            textPaint.setTextSize(dp(11));
            textPaint.setTypeface(AndroidUtilities.bold());
            setOnClickListener(v -> onPick.run());
            setContentDescription(text != null ? text : key);
            ScaleStateListAnimator.apply(this, 0.1f, 1.5f);
        }

        void setSelectedMark(boolean value) {
            chosen = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            int plain = Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(2);
            fill.setColor(chosen ? ColorUtils.setAlphaComponent(accent, 0x33)
                    : ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0x0D));
            canvas.drawCircle(cx, cy, radius, fill);
            if (chosen) {
                ring.setColor(accent);
                canvas.drawCircle(cx, cy, radius - dp(1), ring);
            }
            int color = chosen ? accent : plain;
            if (icon != null) {
                int size = dp(22);
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
                icon.setBounds((int) (cx - size / 2f), (int) (cy - size / 2f), (int) (cx + size / 2f), (int) (cy + size / 2f));
                icon.draw(canvas);
            } else {
                textPaint.setColor(color);
                String shown = TextUtils.ellipsize(text, textPaint, radius * 2 - dp(4), TextUtils.TruncateAt.END).toString();
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
            float cx = getWidth() / 2f, cy = getHeight() / 2f, radius = Math.min(cx, cy) - dp(3);
            fill.setColor(shown);
            if (chosen) {
                ring.setColor(shown);
                canvas.drawCircle(cx, cy, radius, ring);
                canvas.drawCircle(cx, cy, radius - dp(5), fill);
            } else {
                canvas.drawCircle(cx, cy, radius, fill);
            }
        }
    }
}
