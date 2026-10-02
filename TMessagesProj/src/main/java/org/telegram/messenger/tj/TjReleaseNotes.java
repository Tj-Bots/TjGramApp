package org.telegram.messenger.tj;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.ArrayList;

/**
 * "What's new" after an update, the way Telegram posts its own into its service chat: the first
 * time a new TjGram build runs, the post the APK came in on the updates channel - its text and its
 * formatting - is written into the TjGram chat.
 */
public final class TjReleaseNotes {

    private static boolean running;
    private static long lastAttempt;
    /** How long a new build keeps looking for its post - it is often posted after it was installed. */
    private static final long WAIT_FOR_POST_MS = 7L * 24 * 60 * 60 * 1000;

    private TjReleaseNotes() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("tjreleasenotes", Context.MODE_PRIVATE);
    }

    public static void check(int account) {
        String version = BuildConfig.TJ_VERSION;
        if (running || version == null || version.endsWith(".dev") || !UserConfig.getInstance(account).isClientActivated()) {
            return;
        }
        String shown = prefs().getString("version", null);
        if (shown == null) {
            // A fresh install: nothing changed for this person yet.
            prefs().edit().putString("version", version).apply();
            return;
        }
        if (shown.equals(version)) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastAttempt != 0 && now - lastAttempt < 10 * 60 * 1000L) return;
        lastAttempt = now;
        running = true;
        MessagesController controller = MessagesController.getInstance(account);
        controller.getUserNameResolver().resolve(TjCommunity.UPDATES_USERNAME, peerId -> {
            if (peerId == null || peerId >= 0) {
                running = false;
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = controller.getInputPeer(peerId);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            req.limit = 20;
            ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                running = false;
                if (!(response instanceof TLRPC.messages_Messages)) return;
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                TLRPC.Message post = pick(res.messages, version);
                if (post != null) {
                    if (post.message != null && !post.message.trim().isEmpty()) {
                        write(account, version, post, peerId);
                    }
                    prefs().edit().putString("version", version).apply();
                } else if (installedAt() > 0 && System.currentTimeMillis() - installedAt() > WAIT_FOR_POST_MS) {
                    // Never posted: this build simply gets no notes.
                    prefs().edit().putString("version", version).apply();
                }
                // Otherwise the post is not up yet - asked again on a later start, never an older one.
            }));
        });
    }

    private static long installedAt() {
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * The post of exactly this build: the APK of exactly this size, or a post that names this
     * version. Nothing else - the newest post from before the install is the previous build's, and
     * its notes are the wrong ones.
     */
    private static TLRPC.Message pick(ArrayList<TLRPC.Message> messages, String version) {
        long installedSize = 0;
        try {
            installedSize = new File(ApplicationLoader.applicationContext.getApplicationInfo().sourceDir).length();
        } catch (Exception ignore) {
        }
        java.util.regex.Pattern named = java.util.regex.Pattern.compile(
                "(?<![0-9.])" + java.util.regex.Pattern.quote(version) + "(?![0-9])");
        TLRPC.Message byName = null;
        for (TLRPC.Message message : messages) {
            TLRPC.Document document = MessageObject.getDocument(message);
            if (!TjUpdates.isApk(document)) continue;
            if (document.size == installedSize) return message;
            String fileName = org.telegram.messenger.FileLoader.getDocumentFileName(document);
            if (byName == null && (message.message != null && named.matcher(message.message).find()
                    || fileName != null && named.matcher(fileName).find())) {
                byName = message;
            }
        }
        return byName;
    }

    private static void write(int account, String version, TLRPC.Message post, long channelDialogId) {
        String header = "TjGram " + version;
        String text = header + "\n\n" + post.message.trim();
        int shift = header.length() + 2 - (post.message.length() - post.message.replaceAll("^\\s+", "").length());
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = header.length();
        entities.add(bold);
        if (post.entities != null) {
            for (TLRPC.MessageEntity entity : post.entities) {
                // The post's own formatting, moved past the heading.
                if (entity.offset + shift < header.length() + 2 || entity.offset + shift + entity.length > text.length()) continue;
                entity.offset += shift;
                entities.add(entity);
            }
        }
        TjAccountLogChat.send(account, text, entities, 0);
    }
}
