package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ImageView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.TjLocale;
import org.telegram.messenger.tj.TjMediaKind;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/** Recycled, thumbnail-only general library row; no full media download on bind. */
public final class TjMediaRowCell extends LinearLayout {
    private final BackupImageView image;
    private final TextView title, subtitle;
    private final ImageView fallback;

    public TjMediaRowCell(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LocaleController.isRTL ? LAYOUT_DIRECTION_RTL : LAYOUT_DIRECTION_LTR);
        setPadding(dp(10), dp(10), dp(10), dp(10));
        setMinimumHeight(dp(88));
        androidx.core.view.ViewCompat.setScreenReaderFocusable(this, true);
        FrameLayout thumbnail = new FrameLayout(context);
        thumbnail.setBackground(Theme.createRoundRectDrawable(dp(10), Theme.getColor(Theme.key_windowBackgroundGray)));
        fallback = new ImageView(context);
        fallback.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        fallback.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        thumbnail.addView(fallback, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        image = new BackupImageView(context);
        image.setRoundRadius(dp(10));
        thumbnail.addView(image, LayoutHelper.createFrame(-1, -1));
        thumbnail.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        labels.setPadding(dp(12), 0, dp(12), 0);
        title = text(context, 16, Theme.key_windowBackgroundWhiteBlackText);
        title.setMaxLines(2);
        labels.addView(title, LayoutHelper.createLinear(-1, -2));
        subtitle = text(context, 13, Theme.key_windowBackgroundWhiteGrayText);
        subtitle.setMaxLines(2);
        labels.addView(subtitle, LayoutHelper.createLinear(-1, -2, 0, 4, 0, 0));
        // Telegram opts out of platform RTL; child ordering must be explicit.
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        if (LocaleController.isRTL) {
            addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            addView(thumbnail, LayoutHelper.createLinear(68, 62));
        } else {
            addView(thumbnail, LayoutHelper.createLinear(68, 62));
            addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        }
    }

    private int dp(float value) { return AndroidUtilities.dp(value); }
    private TextView text(Context context, int size, int color) {
        TextView view = new TextView(context);
        view.setTextSize(size); view.setTextColor(Theme.getColor(color));
        view.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT); view.setTextDirection(TEXT_DIRECTION_FIRST_STRONG);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        return view;
    }

    public void bind(MessageObject message, String name, String source) {
        title.setText(name);
        String size = message.getDocument() == null ? "" : " · " + AndroidUtilities.formatFileSize(message.getDocument().size);
        String duration = TjMediaKind.of(message) == TjMediaKind.VIDEO && message.getDuration() > 0
                ? " · " + AndroidUtilities.formatDuration((int) message.getDuration(), false) : "";
        subtitle.setText(source + duration + size);
        int kind = TjMediaKind.of(message);
        int label = kind == TjMediaKind.PHOTO ? R.string.TjMediaPhotos : kind == TjMediaKind.VIDEO ? R.string.TjMediaVideos
                : kind == TjMediaKind.GIF ? R.string.TjMediaGifs : kind == TjMediaKind.MUSIC ? R.string.TjMediaMusic
                : kind == TjMediaKind.VOICE ? R.string.TjMediaVoice : R.string.TjMediaFiles;
        fallback.setImageResource(kind == TjMediaKind.PHOTO ? R.drawable.msg_media
                : kind == TjMediaKind.VIDEO || kind == TjMediaKind.GIF ? R.drawable.msg_played
                : kind == TjMediaKind.MUSIC ? R.drawable.search_music_filled
                : kind == TjMediaKind.VOICE ? R.drawable.msg_filled_data_voice : R.drawable.msg_view_file);
        image.getImageReceiver().cancelLoadImage(); image.setImageDrawable(null);
        image.getImageReceiver().setCurrentAccount(message.currentAccount);
        if (!message.hasMediaSpoilers()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 160, true, null, true);
            ImageLocation location = thumb == null ? null : ImageLocation.getForObject(thumb, message.photoThumbsObject);
            if (location == null && message.getDocument() != null) {
                thumb = FileLoader.getClosestPhotoSizeWithSize(message.getDocument().thumbs, 160, true, null, true);
                if (thumb != null) location = ImageLocation.getForDocument(thumb, message.getDocument());
            }
            if (location != null) image.setImage(location, "90_90", (android.graphics.drawable.Drawable) null, message);
        }
        setContentDescription(name + ", " + TjLocale.getString(label) + ", " + source + duration + size);
    }
}
