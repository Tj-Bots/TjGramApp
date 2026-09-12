package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/** Adaptive media artwork with a readable fallback and no spoiler thumbnail requests. */
public final class TjMediaCardCell extends LinearLayout {
    private final FrameLayout artwork;
    private final BackupImageView image;
    private final TextView fallback;
    private final TextView badge;
    private final TextView title;
    private final TextView subtitle;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress;

    public TjMediaCardCell(Context context) {
        super(context);
        setOrientation(VERTICAL);
        androidx.core.view.ViewCompat.setScreenReaderFocusable(this, true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setPadding(dp(6), dp(6), dp(6), dp(10));
        artwork = new FrameLayout(context);
        artwork.setBackground(Theme.createRoundRectDrawable(dp(12), Theme.getColor(Theme.key_windowBackgroundGray)));
        artwork.setClipToOutline(true);
        artwork.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(artwork, LayoutHelper.createLinear(-1, 120));
        fallback = new TextView(context);
        fallback.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        fallback.setTextSize(15);
        fallback.setGravity(Gravity.CENTER);
        artwork.addView(fallback, LayoutHelper.createFrame(-1, -1));
        image = new BackupImageView(context);
        artwork.addView(image, LayoutHelper.createFrame(-1, -1));
        badge = new TextView(context);
        badge.setTextSize(11);
        badge.setTextColor(0xffffffff);
        badge.setPadding(dp(7), dp(3), dp(7), dp(3));
        badge.setBackground(Theme.createRoundRectDrawable(dp(6), 0xb3000000));
        artwork.addView(badge, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 6, 6, 6, 6));
        title = line(context, 15, Theme.key_windowBackgroundWhiteBlackText);
        title.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setMinLines(2);
        title.setMaxLines(2);
        addView(title, LayoutHelper.createLinear(-1, -2, 0, 8, 0, 0));
        subtitle = line(context, 12, Theme.key_windowBackgroundWhiteGrayText);
        subtitle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        subtitle.setSingleLine(true);
        addView(subtitle, LayoutHelper.createLinear(-1, -2));
        setWillNotDraw(false);
    }

    private static int dp(float value) { return AndroidUtilities.dp(value); }

    private TextView line(Context context, int size, int colorKey) {
        TextView text = new TextView(context);
        text.setTextSize(size);
        text.setTextColor(Theme.getColor(colorKey));
        text.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return text;
    }

    public void bind(MessageObject message, String name, String source, long position, long duration, String posterUrl) {
        title.setText(name);
        subtitle.setText(source);
        image.getImageReceiver().cancelLoadImage();
        image.setImageDrawable(null);
        image.getImageReceiver().setCurrentAccount(message.currentAccount);
        String kind = TjLocale.getString(message.isPhoto() ? R.string.TjMediaPhotos
                : message.isVideo() || message.isGif() ? R.string.TjMediaVideos
                : message.isMusic() ? R.string.TjMediaMusic : message.isVoice() || message.isRoundVideo()
                ? R.string.TjMediaVoice : R.string.TjMediaFiles);
        fallback.setText(kind);
        badge.setText(message.getDocument() != null && message.getDocument().size > 0
                ? AndroidUtilities.formatFileSize(message.getDocument().size) : kind);
        boolean concealed = message.hasMediaSpoilers();
        if (!concealed && posterUrl != null && !posterUrl.isEmpty() && TjConfig.hasMediaMetadataCredential(message.currentAccount)) {
            image.setImage(posterUrl, "320_180", null);
        } else if (!concealed) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 320);
            ImageLocation location = thumb == null ? null : ImageLocation.getForObject(thumb, message.photoThumbsObject);
            if (location == null && message.getDocument() != null) {
                thumb = FileLoader.getClosestPhotoSizeWithSize(message.getDocument().thumbs, 320);
                if (thumb != null) location = ImageLocation.getForDocument(thumb, message.getDocument());
            }
            if (location != null) image.setImage(location, "320_180", (android.graphics.drawable.Drawable) null, message);
        }
        progress = duration <= 0 ? 0 : Math.max(0f, Math.min(1f, position / (float) duration));
        setContentDescription(name + ", " + kind + ", " + source
                + (duration > 0 ? ", " + java.text.NumberFormat.getPercentInstance().format(progress) : ""));
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec) - getPaddingLeft() - getPaddingRight();
        artwork.getLayoutParams().height = Math.max(dp(90), width * 9 / 16);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (progress <= 0) return;
        float left = artwork.getLeft() + dp(8);
        float right = artwork.getRight() - dp(8);
        float y = artwork.getBottom() - dp(3);
        paint.setColor(0x66000000);
        canvas.drawRoundRect(left, y, right, y + dp(3), dp(2), dp(2), paint);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        canvas.drawRoundRect(left, y, left + (right - left) * progress, y + dp(3), dp(2), dp(2), paint);
    }
}
