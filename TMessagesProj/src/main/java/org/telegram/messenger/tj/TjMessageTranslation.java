package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * A translation that stays under the message it belongs to, instead of a sheet over the chat.
 *
 * The translated text is glued to the original with a line saying which way it went, and the
 * whole thing is handed to the same drawing the message already uses - so it reads as part of the
 * message, can be selected and copied with it, and goes away when the translation is turned off.
 *
 * It lives on the message object only: nothing is written to the database and nothing is sent.
 */
public final class TjMessageTranslation {

    private TjMessageTranslation() { }

    public static boolean has(MessageObject message) {
        return message != null && message.messageOwner != null && message.messageOwner.tjTranslation != null;
    }

    public static void clear(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        message.messageOwner.tjTranslation = null;
        refresh(message);
    }

    /**
     * Builds "the message, then where it came from and where it went, then the translation".
     *
     * @param original what the message says now, entities and all
     * @param translated what came back
     */
    public static void apply(MessageObject message, CharSequence original, ArrayList<TLRPC.MessageEntity> original_entities,
                             String fromLang, String toLang, TLRPC.TL_textWithEntities translated) {
        if (message == null || message.messageOwner == null || translated == null || TextUtils.isEmpty(translated.text)) {
            return;
        }
        final String head = original == null ? "" : original.toString();
        final String label = "Translation: " + language(fromLang) + "-" + language(toLang);
        final StringBuilder text = new StringBuilder(head);
        if (text.length() > 0) {
            text.append("\n\n");
        }
        final int labelOffset = text.length();
        text.append(label).append("\n");
        final int bodyOffset = text.length();
        text.append(translated.text);

        TLRPC.TL_textWithEntities combined = new TLRPC.TL_textWithEntities();
        combined.text = text.toString();
        if (original_entities != null) {
            for (int a = 0; a < original_entities.size(); a++) {
                TLRPC.MessageEntity entity = copy(original_entities.get(a), 0);
                if (entity != null) {
                    combined.entities.add(entity);
                }
            }
        }
        // The line between the two is set apart, the way the original is set apart from it.
        TLRPC.TL_messageEntityItalic mark = new TLRPC.TL_messageEntityItalic();
        mark.offset = labelOffset;
        mark.length = label.length();
        combined.entities.add(mark);
        if (translated.entities != null) {
            for (int a = 0; a < translated.entities.size(); a++) {
                TLRPC.MessageEntity entity = copy(translated.entities.get(a), bodyOffset);
                if (entity != null) {
                    combined.entities.add(entity);
                }
            }
        }
        message.messageOwner.tjTranslation = combined;
        refresh(message);
    }

    /** The text the message shows, translation included. */
    public static CharSequence text(TLRPC.Message message) {
        return message != null && message.tjTranslation != null ? message.tjTranslation.text : null;
    }

    public static ArrayList<TLRPC.MessageEntity> entities(TLRPC.Message message) {
        return message != null && message.tjTranslation != null ? message.tjTranslation.entities : null;
    }

    private static void refresh(MessageObject message) {
        // The caption is rebuilt only when it thinks something changed, so it is cleared first.
        message.caption = null;
        message.applyNewText();
        message.generateCaption();
        message.resetLayout();
    }

    private static String language(String code) {
        return TextUtils.isEmpty(code) ? "auto" : code;
    }

    /** Entities are offsets into a particular string; moving the string means moving them. */
    private static TLRPC.MessageEntity copy(TLRPC.MessageEntity entity, int shift) {
        if (entity == null) {
            return null;
        }
        TLRPC.MessageEntity copy;
        try {
            copy = entity.getClass().newInstance();
        } catch (Exception ignored) {
            return null;
        }
        copy.flags = entity.flags;
        copy.collapsed = entity.collapsed;
        copy.offset = entity.offset + shift;
        copy.length = entity.length;
        copy.url = entity.url;
        copy.language = entity.language;
        if (entity instanceof TLRPC.TL_messageEntityCustomEmoji && copy instanceof TLRPC.TL_messageEntityCustomEmoji) {
            ((TLRPC.TL_messageEntityCustomEmoji) copy).document_id = ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id;
            ((TLRPC.TL_messageEntityCustomEmoji) copy).document = ((TLRPC.TL_messageEntityCustomEmoji) entity).document;
        } else if (entity instanceof TLRPC.TL_messageEntityMentionName && copy instanceof TLRPC.TL_messageEntityMentionName) {
            ((TLRPC.TL_messageEntityMentionName) copy).user_id = ((TLRPC.TL_messageEntityMentionName) entity).user_id;
        }
        return copy;
    }
}
