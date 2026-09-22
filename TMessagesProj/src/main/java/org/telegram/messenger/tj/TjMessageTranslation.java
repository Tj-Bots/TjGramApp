package org.telegram.messenger.tj;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One message, shown translated, until it is asked to go back.
 *
 * The translated words take the place of the original ones on the message object, so everything
 * that draws a message draws this one the same way it draws any other - no second layout, no text
 * glued under the first. What was there before is kept here, and put back on the next press.
 *
 * Nothing is written to the database and nothing is sent; close the chat and the message is its
 * own again.
 */
public final class TjMessageTranslation {

    private static final class Original {
        String message;
        ArrayList<TLRPC.MessageEntity> entities;
    }

    private static final ConcurrentHashMap<String, Original> originals = new ConcurrentHashMap<>();
    /** The messages whose translation is on its way, so the button can say so. */
    private static final Set<String> loading = ConcurrentHashMap.newKeySet();

    private TjMessageTranslation() { }

    private static String key(MessageObject message) {
        return message.currentAccount + ":" + message.getDialogId() + ":" + message.getId();
    }

    public static boolean has(MessageObject message) {
        return message != null && message.messageOwner != null && originals.containsKey(key(message));
    }

    public static boolean isLoading(MessageObject message) {
        return message != null && message.messageOwner != null && loading.contains(key(message));
    }

    public static void setLoading(MessageObject message, boolean value) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        if (value) {
            loading.add(key(message));
        } else {
            loading.remove(key(message));
        }
    }

    public static void apply(MessageObject message, TLRPC.TL_textWithEntities translated) {
        if (message == null || message.messageOwner == null || translated == null || translated.text == null) {
            return;
        }
        final String key = key(message);
        if (!originals.containsKey(key)) {
            Original original = new Original();
            original.message = message.messageOwner.message;
            original.entities = new ArrayList<>(message.messageOwner.entities);
            originals.put(key, original);
        }
        message.messageOwner.message = translated.text;
        message.messageOwner.entities = new ArrayList<>(translated.entities);
        refresh(message);
    }

    public static void revert(MessageObject message) {
        if (message == null || message.messageOwner == null) {
            return;
        }
        Original original = originals.remove(key(message));
        if (original == null) {
            return;
        }
        message.messageOwner.message = original.message;
        message.messageOwner.entities = original.entities;
        refresh(message);
    }

    private static void refresh(MessageObject message) {
        message.caption = null;
        message.applyNewText();
        message.generateCaption();
        message.resetLayout();
    }
}
