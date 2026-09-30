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
 * are drawables instead: they take the timestamp's own colour - or the colour picked for them - and
 * line up with it.
 *
 * The preference holds "icon:name". Installs from before still hold the old emoji, which is read
 * as the icon it used to stand for, so nobody's choice changes under them.
 */
public final class TjMessageMarks {

    /** The deleted marks on offer, in the order the picker shows them. */
    public static final String[] DELETED = {"trash", "trash_filled", "trash_x", "eye_off",
            "cross_circle", "cross", "block", "minus_circle"};
    /** The edited marks on offer; "text" is Telegram's own word. */
    public static final String[] EDITED = {"text", "pencil", "pencil_outline", "edit_note", "history"};
    /** 0 follows the timestamp's colour; the rest are fixed. */
    public static final int[] COLORS = {0, 0xFFE53935, 0xFFC62828, 0xFFD81B60, 0xFFB43FD3,
            0xFF8E44E0, 0xFF5B4FE0, 0xFF3B7BE8};

    private TjMessageMarks() {
    }

    public static int iconRes(String name) {
        switch (name == null ? "" : name) {
            case "trash_filled": return R.drawable.tj_mark_trash_filled;
            case "trash_x": return R.drawable.tj_mark_trash_x;
            case "eye_off": return R.drawable.tj_mark_eye_off;
            case "cross_circle": return R.drawable.tj_mark_cross_circle;
            case "cross": return R.drawable.tj_mark_cross;
            case "block": return R.drawable.tj_mark_block;
            case "minus_circle": return R.drawable.tj_mark_minus_circle;
            case "pencil": return R.drawable.tj_mark_pencil;
            case "pencil_outline": return R.drawable.tj_mark_pencil_outline;
            case "edit_note": return R.drawable.tj_mark_edit_note;
            case "history": return R.drawable.tj_mark_history;
            default: return R.drawable.tj_mark_trash;
        }
    }

    /** Which deleted mark is chosen, old emoji settings included. */
    public static String deletedKey() {
        String mark = TjConfig.deletedMark();
        if (mark == null) return "trash";
        if (mark.startsWith("icon:")) return known(DELETED, mark.substring(5), "trash");
        // The two X options have to differ by shape: both may be tinted with the timestamp colour.
        if (mark.startsWith("❌") || mark.startsWith("❎")) return "cross_circle";
        if (mark.startsWith("✖") || mark.startsWith("✗")) return "cross";
        return "trash";
    }

    /** Which edited mark is chosen: "text" for the plain word, or an icon. */
    public static String editedKey() {
        String mark = TjConfig.editedMark();
        if (TextUtils.isEmpty(mark)) return "text";
        if (mark.startsWith("icon:")) return known(EDITED, mark.substring(5), "pencil");
        return "pencil";
    }

    /** A mark that is no longer offered falls back to the plain one. */
    private static String known(String[] offered, String key, String fallback) {
        for (String option : offered) if (option.equals(key)) return key;
        return fallback;
    }

    public static void setDeleted(String key) {
        TjConfig.put("deleted_mark", "icon:" + key);
    }

    public static void setEdited(String key) {
        TjConfig.put("edited_mark", "text".equals(key) ? "" : "icon:" + key);
    }

    public static int color() {
        return TjConfig.marksColor();
    }

    /** The mark for a message someone deleted but this device kept. */
    public static CharSequence deleted() {
        return icon(iconRes(deletedKey()));
    }

    /** The mark for an edited message. The plain-word choice keeps the caller's own text. */
    public static CharSequence edited(CharSequence fallback) {
        String key = editedKey();
        if ("text".equals(key)) {
            return fallback;
        }
        return icon(iconRes(key));
    }

    private static CharSequence icon(int drawableRes) {
        SpannableStringBuilder builder = new SpannableStringBuilder(" ");
        try {
            ColoredImageSpan span = new ColoredImageSpan(drawableRes, ColoredImageSpan.ALIGN_CENTER);
            // Sized to the timestamp rather than the icon's own 24dp, so it reads as punctuation
            // next to the time instead of as a button - but large enough to be recognisable.
            span.setSize(dp(14));
            int color = color();
            if (color != 0) span.setOverrideColor(color);
            builder.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } catch (Throwable ignore) {
            return "";
        }
        return builder;
    }
}
