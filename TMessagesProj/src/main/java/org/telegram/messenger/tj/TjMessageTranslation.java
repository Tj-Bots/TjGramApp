package org.telegram.messenger.tj;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.TranslateController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TranslateAlert2;

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
    /** What language each message turned out to be written in, once someone asked. */
    private static final ConcurrentHashMap<String, String> languages = new ConcurrentHashMap<>();
    private static final Set<String> detecting = ConcurrentHashMap.newKeySet();

    private static final String UNKNOWN = "und";
    /** The detector could not be reached at all - then the offer stands, we simply do not know. */
    private static final String UNDETECTABLE = "?";

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

    /**
     * Whether it is worth offering to translate this message: only if it is not already written in
     * the language the app itself is in. The first time a message is asked about the answer is not
     * there yet - the detector is sent after it and {@code onDetected} runs once it comes back.
     */
    public static boolean worthTranslating(MessageObject message, Runnable onDetected) {
        if (message == null || message.messageOwner == null) {
            return false;
        }
        if (has(message) || isLoading(message)) {
            return true;
        }
        if (!hasWords(message.messageOwner.message)) {
            return false;
        }
        String language = message.messageOwner.originalLanguage;
        if (TextUtils.isEmpty(language)) {
            language = languages.get(key(message));
        }
        if (TextUtils.isEmpty(language)) {
            detect(message, onDetected);
            return false;
        }
        return !spokenHere(language);
    }

    /** Emoji, numbers and punctuation are in no language - there is nothing there to translate. */
    private static boolean hasWords(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        int letters = 0;
        for (int i = 0; i < text.length(); ) {
            final int codePoint = text.codePointAt(i);
            if (Character.isLetter(codePoint) && ++letters >= 2) {
                return true;
            }
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private static boolean spokenHere(String language) {
        if (UNDETECTABLE.equals(language)) {
            return false;
        }
        if (TextUtils.isEmpty(language) || UNKNOWN.equals(language)) {
            return true;
        }
        final String lang = root(language);
        return lang.equals(root(TranslateController.currentLanguage()))
                || lang.equals(root(TranslateAlert2.getToLanguage()));
    }

    private static String root(String language) {
        if (TextUtils.isEmpty(language)) {
            return "";
        }
        return language.split("[-_]")[0].toLowerCase();
    }

    private static void detect(MessageObject message, Runnable onDetected) {
        final String key = key(message);
        final String text = message.messageOwner.message;
        if (TextUtils.isEmpty(text) || !detecting.add(key)) {
            return;
        }
        if (!LanguageDetector.hasSupport()) {
            detected(key, UNDETECTABLE, onDetected);
            return;
        }
        LanguageDetector.detectLanguage(text,
                language -> detected(key, language, onDetected),
                error -> detected(key, UNDETECTABLE, onDetected));
    }

    private static void detected(String key, String language, Runnable onDetected) {
        languages.put(key, TextUtils.isEmpty(language) ? UNKNOWN : language);
        detecting.remove(key);
        if (onDetected != null) {
            AndroidUtilities.runOnUIThread(onDetected);
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
