package org.telegram.messenger.tj;

import org.telegram.tgnet.TLRPC;

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
            if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                // One of ours from before the poster problem was understood: an upright shape,
                // copied from a poster thumbnail, which costs the player its rotate button for as
                // long as that attribute survives. Correct it in place rather than leaving old
                // files broken until something re-reads them.
                TLRPC.TL_documentAttributeVideo video = (TLRPC.TL_documentAttributeVideo) attribute;
                if (isTjMarked(document) && video.h > video.w) {
                    video.w = DEFAULT_WIDTH;
                    video.h = DEFAULT_HEIGHT;
                }
                return;
            }
            if (attribute instanceof TLRPC.TL_documentAttributeAnimated
                    || attribute instanceof TLRPC.TL_documentAttributeAudio
                    || attribute instanceof TLRPC.TL_documentAttributeSticker
                    || attribute instanceof TLRPC.TL_documentAttributeImageSize) {
                // Something that must not be treated as a video.
                return;
            }
            if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                fileName = attribute.file_name;
            }
        }
        if (!TjVideoFormat.isSupportedContainer(document.mime_type, fileName)) {
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

    /**
     * A guess at the shape of the picture. The thumbnail is the only hint a raw document gives,
     * and it is a good one right up until the thumbnail is the film's poster - which is upright,
     * while the film is not. Anything taller than it is wide is treated as a poster and ignored,
     * because a portrait shape here also costs the player its rotate-to-fullscreen button.
     */
    private static int[] thumbSize(TLRPC.Document document) {
        if (document.thumbs != null) {
            for (int a = 0, count = document.thumbs.size(); a < count; a++) {
                TLRPC.PhotoSize thumb = document.thumbs.get(a);
                if (thumb != null && thumb.w > 0 && thumb.h > 0 && thumb.w > thumb.h) {
                    return new int[]{thumb.w, thumb.h};
                }
            }
        }
        return new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
    }
}
