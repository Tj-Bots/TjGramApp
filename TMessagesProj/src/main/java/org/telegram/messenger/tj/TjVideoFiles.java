package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.Locale;

/**
 * Opens a video that was sent as a plain file in the built-in player.
 *
 * Telegram decides something is a video only when the server-side document carries a
 * {@code documentAttributeVideo}. Bots and other clients routinely send an mp4 or mkv as a raw
 * document without it, and those land in a chat as a file row: tapping downloads the whole thing
 * and hands it to an external player.
 *
 * The message keeps looking like a file on purpose - it is one, and turning the bubble into a
 * video would misrepresent what was sent. Only the tap changes: a throwaway copy of the message
 * carries the video attribute the player and the streaming code read, and that copy is what the
 * viewer gets. Nothing in the chat, the database or a forward is touched.
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
     * True when this document is a video the built-in player can handle but Telegram did not mark
     * as one. False unless the user turned direct streaming on.
     */
    public static boolean isPlayableVideoFile(TLRPC.Document document) {
        if (document == null || !TjConfig.directFileStreaming()) {
            return false;
        }
        if (document instanceof TLRPC.TL_documentEncrypted || document.attributes == null) {
            return false;
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
                return false;
            }
            if (attribute instanceof TLRPC.TL_documentAttributeFilename) {
                fileName = attribute.file_name;
            }
        }
        return isPlayableVideo(document.mime_type, fileName);
    }

    /**
     * A copy of the message that the player will accept, leaving the original message - and the
     * file row the user is looking at - exactly as it was.
     */
    public static MessageObject buildPlayableCopy(int accountId, MessageObject source) {
        if (source == null || source.messageOwner == null) {
            return null;
        }
        TLRPC.Message copiedMessage = copyMessage(source.messageOwner);
        if (copiedMessage == null) {
            return null;
        }
        TLRPC.Document document = MessageObject.getDocument(copiedMessage);
        if (document == null || document.attributes == null) {
            return null;
        }
        TLRPC.TL_documentAttributeVideo video = new TLRPC.TL_documentAttributeVideo();
        video.supports_streaming = true;
        video.duration = 0;
        int[] size = thumbSize(document);
        video.w = size[0];
        video.h = size[1];
        document.attributes.add(video);
        copiedMessage.attachPath = source.messageOwner.attachPath;
        return new MessageObject(accountId, copiedMessage, false, false);
    }

    private static TLRPC.Message copyMessage(TLRPC.Message message) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
            buffer.position(0);
            return TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
        } catch (Throwable error) {
            FileLog.e("Tj playable copy failed", error);
            return null;
        } finally {
            if (buffer != null) {
                buffer.reuse();
            }
        }
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
