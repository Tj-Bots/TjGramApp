package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import org.telegram.messenger.*;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

/** Static thumbnails only: scrolling never starts GIF playback or downloads full photos. */
public final class TjMediaGridCell extends FrameLayout {
    private final BackupImageView image;
    private final TextView badge;
    public TjMediaGridCell(Context context) {
        super(context);
        setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
        setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ImageView fallback = new ImageView(context);
        fallback.setImageResource(R.drawable.msg_media);
        fallback.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        addView(fallback, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
        image = new BackupImageView(context);
        addView(image, LayoutHelper.createFrame(-1, -1));
        badge = new TextView(context); badge.setTextSize(12); badge.setTextColor(0xffffffff);
        badge.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(4), 0x99000000));
        badge.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
        addView(badge, LayoutHelper.createFrame(-2, -2, Gravity.BOTTOM | Gravity.RIGHT, 4, 4, 4, 4));
    }
    @Override protected void onMeasure(int width, int height) {
        super.onMeasure(width, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(width), MeasureSpec.EXACTLY));
    }
    public void bind(MessageObject message) {
        image.getImageReceiver().cancelLoadImage(); image.setImageDrawable(null);
        image.getImageReceiver().setCurrentAccount(message.currentAccount);
        if (!message.hasMediaSpoilers()) {
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(message.photoThumbs, 320, true, null, true);
            ImageLocation location = thumb == null ? null : ImageLocation.getForObject(thumb, message.photoThumbsObject);
            if (location == null && message.getDocument() != null) {
                thumb = FileLoader.getClosestPhotoSizeWithSize(message.getDocument().thumbs, 320, true, null, true);
                if (thumb != null) location = ImageLocation.getForDocument(thumb, message.getDocument());
            }
            if (location != null) image.setImage(location, "160_160", (android.graphics.drawable.Drawable) null, message);
        }
        badge.setVisibility(message.isGif() ? VISIBLE : GONE);
        badge.setText(TjLocale.getString(R.string.TjMediaGifs));
        setContentDescription(TjLocale.getString(message.isGif() ? R.string.TjMediaGifs : R.string.TjMediaPhotos)
                + ", " + (message.messageOwner.message == null ? "" : message.messageOwner.message));
    }
}
