package org.telegram.messenger.tj;

import java.util.Locale;

/**
 * Non-mutating container recognition shared by file playback and media indexing.
 * Recognizing a container does not guarantee device support for every codec inside it.
 * This policy deliberately has no account, preference, message or Android dependency.
 */
public final class TjVideoFormat {
    private static final String[] EXTENSIONS = {
            "mp4", "m4v", "mov", "mkv", "webm", "ts", "m2ts", "mts", "flv", "avi", "3gp", "3gpp"
    };

    private TjVideoFormat() {
    }

    /** Preserve the existing player policy: an explicit unsupported suffix wins over MIME. */
    public static boolean isSupportedContainer(String mimeType, String fileName) {
        String extension = extensionOf(fileName);
        if (extension != null) {
            for (String supported : EXTENSIONS) {
                if (supported.equals(extension)) return true;
            }
            return false;
        }
        if (mimeType == null || mimeType.isEmpty()) return false;
        String mime = mimeType.toLowerCase(Locale.US);
        return mime.equals("video/mp4") || mime.equals("video/x-matroska") || mime.equals("video/webm")
                || mime.equals("video/quicktime") || mime.equals("video/mp2t") || mime.equals("video/avi")
                || mime.equals("video/x-msvideo") || mime.equals("video/x-flv") || mime.equals("video/3gpp");
    }

    private static String extensionOf(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.US);
    }
}
