package org.telegram.messenger.tj;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/** Library classification only: never adds video attributes or changes playback settings. */
public final class TjMediaKind {
    public static final int PHOTO = 1, VIDEO = 2, DOCUMENT = 3, MUSIC = 4, VOICE = 5, GIF = 6;
    public static final int ALL_MASK = 126;

    /** Negative selections encode a type mask; positive selections are one type. */
    public static boolean matches(int selection, int kind) {
        return selection == 0 || selection > 0 && selection == kind
                || selection < 0 && ((-selection) & (1 << kind)) != 0;
    }

    private TjMediaKind() {}

    public static int of(MessageObject message) {
        if (message.isPhoto()) return PHOTO;
        if (message.isVoice() || message.isRoundVideo()) return VOICE;
        if (message.isMusic()) return MUSIC;
        if (message.isGif()) return GIF;
        if (message.isVideo()) return VIDEO;
        TLRPC.Document document = message.getDocument();
        if (document == null || document instanceof TLRPC.TL_documentEncrypted) return DOCUMENT;
        if (document.attributes != null) for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeAudio
                    || attribute instanceof TLRPC.TL_documentAttributeSticker
                    || attribute instanceof TLRPC.TL_documentAttributeImageSize
                    || attribute instanceof TLRPC.TL_documentAttributeAnimated) return DOCUMENT;
        }
        return TjVideoFormat.isSupportedContainer(document.mime_type, message.getDocumentName())
                ? VIDEO : DOCUMENT;
    }
}
