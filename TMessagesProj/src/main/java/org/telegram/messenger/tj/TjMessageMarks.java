package org.telegram.messenger.tj;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.messenger.R;
import org.telegram.ui.Components.ColoredImageSpan;

/**
 * The little marks on a message that was deleted or edited.
 *
 * These used to be emoji dropped into the timestamp. Emoji are drawn by the system font, so they
 * ignored the theme, sat badly against the time text and looked different on every device. These
 * are drawables instead: they take the timestamp's own colour and line up with it.
 *
 * The stored preference is still the old emoji string, so existing installs keep whichever mark
 * they picked - the emoji simply chooses which icon to draw now.
 */
public final class TjMessageMarks {

    private TjMessageMarks() {
    }

    private static int deletedIcon(String mark) {
        if (mark == null) {
            return R.drawable.tj_mark_trash;
        }
        if (mark.startsWith("❌") || mark.startsWith("✖") || mark.startsWith("✗") || mark.startsWith("❎")) {
            return R.drawable.tj_mark_cross;
        }
        if (mark.startsWith("🧹") || mark.startsWith("🧽")) {
            return R.drawable.tj_mark_broom;
        }
        return R.drawable.tj_mark_trash;
    }

    /** The mark for a message someone deleted but this device kept. */
    public static CharSequence deleted() {
        return icon(deletedIcon(TjConfig.deletedMark()));
    }

    /**
     * The mark for an edited message. An empty preference means the user wants the plain word
     * Telegram normally shows, so nothing is drawn here and the caller keeps its own text.
     */
    public static CharSequence edited(CharSequence fallback) {
        if (TextUtils.isEmpty(TjConfig.editedMark())) {
            return fallback;
        }
        return icon(R.drawable.tj_mark_pencil);
    }

    private static CharSequence icon(int drawableRes) {
        SpannableStringBuilder builder = new SpannableStringBuilder(" ");
        try {
            ColoredImageSpan span = new ColoredImageSpan(drawableRes, ColoredImageSpan.ALIGN_CENTER);
            // Sized to the timestamp rather than the icon's own 24dp, so it reads as punctuation
            // next to the time instead of as a button.
            span.setSize(dp(11));
            builder.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignore) {
            return "";
        }
        return builder;
    }
}
