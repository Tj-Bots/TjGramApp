package org.telegram.messenger.tj;

import android.text.TextUtils;

/**
 * Remembers the styled form of whatever the app last put on the clipboard.
 *
 * A paste done through the text field's own menu reads the HTML flavour off the clipboard and
 * comes back styled. A paste done from the keyboard's clipboard strip does not: the keyboard
 * reads the plain flavour and hands the field bare characters, and the formatting is gone. So
 * the styled form is kept here, and the field puts it back when the characters it is handed are
 * exactly the ones that were copied.
 *
 * Nothing is read from the clipboard for this, which also means no clipboard-read notice for
 * something the user did not ask to be read.
 */
public final class TjClipboard {

    private static final int MAX = 64 * 1024;

    private static String plain;
    private static String html;

    private TjClipboard() { }

    public static void remember(CharSequence copied, String styled) {
        if (copied == null || styled == null || copied.length() == 0 || copied.length() > MAX) {
            forget();
            return;
        }
        plain = copied.toString();
        html = styled;
    }

    public static void forget() {
        plain = null;
        html = null;
    }

    /** The HTML for this text if it is what was copied, otherwise null. */
    public static String htmlFor(CharSequence text) {
        if (text == null || plain == null || text.length() < 2 || text.length() != plain.length()) {
            return null;
        }
        return TextUtils.equals(text, plain) ? html : null;
    }
}
