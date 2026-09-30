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
                TLRPC.Message post = pick(res.messages);
                if (post != null && post.message != null && !post.message.trim().isEmpty()) {
                    write(account, version, post, peerId);
                }
                // Answered either way: a build with no post of its own gets no notes, not a retry.
                prefs().edit().putString("version", version).apply();
            }));
        });
    }

    /**
     * The post this build came in: the APK of exactly this size, or else the newest one posted
     * before this copy was installed.
     */
    private static TLRPC.Message pick(ArrayList<TLRPC.Message> messages) {
        long installedSize = 0, installedAt = 0;
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            installedSize = new File(context.getApplicationInfo().sourceDir).length();
            installedAt = info.lastUpdateTime / 1000L;
        } catch (Exception ignore) {
        }
        TLRPC.Message best = null;
        for (TLRPC.Message message : messages) {
            TLRPC.Document document = MessageObject.getDocument(message);
            if (!TjUpdates.isApk(document)) continue;
            if (document.size == installedSize) return message;
            if (message.date <= installedAt + 600 && (best == null || message.date > best.date)) best = message;
        }
        return best;
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
