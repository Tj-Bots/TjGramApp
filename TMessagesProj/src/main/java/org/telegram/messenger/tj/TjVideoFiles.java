package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.tgnet.TLRPC;

import java.util.Locale;

/**
 * Plays a video that was sent as a plain file.
 *
 * Telegram decides something is a video only when the server-side document carries a
 * {@code documentAttributeVideo}. Bots and other clients routinely send an mp4 or mkv as a raw
 * document without it, and those land in a chat as a file row: tapping downloads the whole thing
 * and hands it to an external player.
 *
 * The attribute is what the rest of the app keys off: the player and the streaming code read its
 * {@code supports_streaming} flag, so adding it is what makes the file playable at all. It also
 * decides how the message is drawn, which is not wanted here - the message should still look like
 * the file it is. So the attribute is written with a signature no genuine Telegram video carries
 * (streaming on, duration zero), {@link #isTjMarked} recognises it, and the three places that
 * decide presentation - the message type, the bubble's document layout and whether a file can be
 * previewed in-app - use it to keep the file row and send the tap to the viewer.
 *
 * Only containers ExoPlayer actually has an extractor for are accepted. Claiming a .wmv or .rmvb
 * is playable would replace a working "download and open elsewhere" with a black screen.
 */
public final class TjVideoFiles {

    /** Containers with an extractor compiled into this build. */
    private static final String[] PLAYABLE_EXTENSIONS = {
            "mp4", "m4v", "mov", "mkv", "webm", "ts", "m2ts", "mts", "flv", "avi", "3gp", "3gpp"
    };

    private static final int DEFAULT_WIDTH = 1280;
    private static final int DEFAULT_HEIGHT = 720;

    private TjVideoFiles() {
    }

    /**
     * Adds a local video attribute when this document is a playable video the server did not mark
     * as one. Does nothing unless the user turned direct streaming on. Safe to call repeatedly.
     */
    public static void markPlayableVideo(TLRPC.Document document) {
        if (document == null || !TjConfig.directFileStreaming()) {
            return;
        }
        if (document instanceof TLRPC.TL_documentEncrypted || document.attributes == null) {
            return;
        }
        String fileName = null;
        for (int a = 0, count = document.attributes.size(); a < count; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeVideo
                    || attribute instanceof TLRPC.TL_documentAttributeAnimated
                    || attribute instanceof TLRPC.TL_documentAttributeAudio
                    || attribute instanceof TLRPC.TL_documentAttributeSticker
                    || attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                // Already a video, or something that must not be treated as one.
                return;
            }
            if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                fileName = attribute.file_name;
            }
        }
        if (!isPlayableVideo(document.mime_type, fileName)) {
            return;
        }
        TLRPC.TL_documentAttributeVideo video = new TLRPC.TL_documentAttributeVideo();
        video.supports_streaming = true;
        video.duration = 0;
        int[] size = thumbSize(document);
        video.w = size[0];
        video.h = size[1];
        document.attributes.add(video);
    }

    /**
     * True for a video attribute this class wrote. The signature is deliberate: a video the server
     * knows about always carries a real duration, so streaming-with-zero-duration means ours. That
     * keeps the mark readable even after the message has been through the local database, which a
     * marker held in memory would not.
     */
    public static boolean isTjMarked(TLRPC.Document document) {
        if (document == null || document.attributes == null) {
            return false;
        }
        for (int a = 0, count = document.attributes.size(); a < count; a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                TLRPC.TL_documentAttributeVideo video = (TLRPC.TL_documentAttributeVideo) attribute;
                return video.supports_streaming && !video.round_message && video.duration <= 0;
            }
        }
        return false;
    }

    private static boolean isPlayableVideo(String mimeType, String fileName) {
        String extension = extensionOf(fileName);
        if (extension != null) {
            for (String playable : PLAYABLE_EXTENSIONS) {
                if (playable.equals(extension)) {
                    return true;
                }
            }
            // A known extension that is not in the list is a deliberate no.
            return false;
        }
        // No filename to go on: trust the mime type only for containers we can parse.
        if (TextUtils.isEmpty(mimeType)) {
            return false;
        }
        String mime = mimeType.toLowerCase(Locale.US);
        return mime.equals("video/mp4") || mime.equals("video/x-matroska") || mime.equals("video/webm")
                || mime.equals("video/quicktime") || mime.equals("video/mp2t") || mime.equals("video/avi")
                || mime.equals("video/x-msvideo") || mime.equals("video/x-flv") || mime.equals("video/3gpp");
    }

    private static String extensionOf(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.US);
    }

    /** The bubble needs real dimensions; the thumbnail is the only hint a raw document gives. */
    private static int[] thumbSize(TLRPC.Document document) {
        if (document.thumbs != null) {
            for (int a = 0, count = document.thumbs.size(); a < count; a++) {
                TLRPC.PhotoSize thumb = document.thumbs.get(a);
                if (thumb != null && thumb.w > 0 && thumb.h > 0) {
                    return new int[]{thumb.w, thumb.h};
                }
            }
        }
        return new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
    }
}
