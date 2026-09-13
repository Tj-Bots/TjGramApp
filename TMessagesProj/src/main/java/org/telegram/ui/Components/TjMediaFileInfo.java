package org.telegram.ui.Components;

import java.util.ArrayList;
import java.util.Locale;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Cheap, reported file attributes only. Never probe remote bytes while binding a list row. */
public final class TjMediaFileInfo {
    private TjMediaFileInfo() { }

    public static String summary(MessageObject message) {
        ArrayList<String> fields = new ArrayList<>();
        TLRPC.Document document = message.getDocument();
        if (document != null) {
            int width = MessageObject.getVideoWidth(document), height = MessageObject.getVideoHeight(document);
            if (width > 0 && height > 0) fields.add(width + " × " + height);
        }
        double duration = message.getDuration();
        if (duration > 0 && Double.isFinite(duration)) {
            long seconds = (long) duration;
            fields.add(seconds >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                    : String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60));
        }
        if (document != null && document.size > 0) fields.add(AndroidUtilities.formatFileSize(document.size));
        // Isolate numeric/Latin values in RTL UI without changing the surrounding text direction.
        return androidx.core.text.BidiFormatter.getInstance().unicodeWrap(android.text.TextUtils.join(" · ", fields));
    }
}
