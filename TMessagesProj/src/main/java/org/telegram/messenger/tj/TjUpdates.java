package org.telegram.messenger.tj;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.SystemClock;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.util.Locale;

/**
 * New TjGram builds posted as APK files in the public updates channel, the way Telegram's own beta
 * and Plus builds reach their users. A post counts as an update when it came out after this copy
 * was installed and is not the very file installed - the build number itself never changes.
 */
public final class TjUpdates {

    public static final int RESULT_NONE = 0;
    public static final int RESULT_FOUND = 1;
    public static final int RESULT_FAILED = -1;

    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final long CHECK_EVERY_MS = 30 * 60 * 1000L;

    private static TLRPC.Document document;
    private static MessageObject message;
    private static int account = -1;
    private static long lastCheckAt;
    private static boolean checking;

    private TjUpdates() {
    }

    public static boolean available() {
        return document != null;
    }

    public static TLRPC.Document document() {
        return document;
    }

    public static MessageObject message() {
        return message;
    }

    public static int account() {
        return account >= 0 ? account : UserConfig.selectedAccount;
    }

    public static String fileName() {
        return document == null ? null : FileLoader.getAttachFileName(document);
    }

    public static boolean isUpdateFile(String name) {
        return name != null && name.equals(fileName());
    }

    public static File file() {
        return document == null ? null : FileLoader.getInstance(account()).getPathToAttach(document, true);
    }

    public static boolean downloaded() {
        File file = file();
        return file != null && file.exists() && file.length() >= document.size;
    }

    public static boolean downloading() {
        return document != null && FileLoader.getInstance(account()).isLoadingFile(fileName());
    }

    public static void download() {
        if (document == null) return;
        FileLoader.getInstance(account()).loadFile(document, message, FileLoader.PRIORITY_HIGH, 1);
    }

    public static void cancelDownload() {
        if (document == null) return;
        FileLoader.getInstance(account()).cancelLoadFile(document);
    }

    /** At most every half hour on its own; {@code force} for the settings button. */
    public static void check(int currentAccount, boolean force, Utilities.Callback<Integer> done) {
        if (checking || !UserConfig.getInstance(currentAccount).isClientActivated()
                || !force && SystemClock.elapsedRealtime() - lastCheckAt < CHECK_EVERY_MS && lastCheckAt != 0) {
            if (done != null) done.run(available() ? RESULT_FOUND : RESULT_NONE);
            return;
        }
        String username = TjCommunity.UPDATES_USERNAME;
        if (TextUtils.isEmpty(username)) {
            if (done != null) done.run(RESULT_FAILED);
            return;
        }
        checking = true;
        lastCheckAt = SystemClock.elapsedRealtime();
        MessagesController controller = MessagesController.getInstance(currentAccount);
        controller.getUserNameResolver().resolve(username, peerId -> {
            if (peerId == null || peerId == 0 || peerId > 0) {
                finish(done, RESULT_FAILED);
                return;
            }
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.peer = controller.getInputPeer(peerId);
            req.q = "";
            req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            req.limit = 10;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (!(response instanceof TLRPC.messages_Messages)) {
                    finish(done, RESULT_FAILED);
                    return;
                }
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                controller.putUsers(res.users, false);
                controller.putChats(res.chats, false);
                TLRPC.Message newest = null;
                TLRPC.Document newestDocument = null;
                for (TLRPC.Message candidate : res.messages) {
                    TLRPC.Document doc = MessageObject.getDocument(candidate);
                    if (!isApk(doc)) continue;
                    if (newest == null || candidate.date > newest.date) {
                        newest = candidate;
                        newestDocument = doc;
                    }
                }
                if (newest != null && isNewer(newest, newestDocument)) {
                    boolean changed = document == null || document.id != newestDocument.id;
                    document = newestDocument;
                    message = new MessageObject(currentAccount, newest, false, false);
                    account = currentAccount;
                    if (changed) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    }
                    finish(done, RESULT_FOUND);
                } else {
                    boolean hadUpdate = document != null;
                    document = null;
                    message = null;
                    if (hadUpdate) {
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.appUpdateAvailable);
                    }
                    finish(done, RESULT_NONE);
                }
            }));
        });
    }

    private static void finish(Utilities.Callback<Integer> done, int result) {
        checking = false;
        if (done != null) done.run(result);
    }

    private static boolean isApk(TLRPC.Document doc) {
        if (doc == null || doc.size <= 0) return false;
        if (APK_MIME.equals(doc.mime_type)) return true;
        String name = FileLoader.getDocumentFileName(doc);
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".apk");
    }

    private static boolean isNewer(TLRPC.Message post, TLRPC.Document doc) {
        try {
            Context context = ApplicationLoader.applicationContext;
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long installedSize = new File(context.getApplicationInfo().sourceDir).length();
            // Posted before this copy went on means it is this build or an older one.
            return post.date * 1000L > info.lastUpdateTime && doc.size != installedSize;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }
}
