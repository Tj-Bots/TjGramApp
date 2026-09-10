package org.telegram.messenger.tj;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Central, UI-independent configuration for TjGram features. */
public final class TjConfig {
    private static final String PREFS_NAME = "tjsettings";
    private static final String SYNC_TOKEN_LEGACY = "tj_sync_token";
    private static final String SYNC_TOKEN_CIPHERTEXT = "tj_sync_token_ciphertext";
    private static final String SYNC_TOKEN_IV = "tj_sync_token_iv";
    private static final String SYNC_TOKEN_KEY_ALIAS = "TjGramSyncTokenKey";

    private TjConfig() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean get(String key, boolean defaultValue) {
        return prefs().getBoolean(key, defaultValue);
    }

    private static int get(String key, int defaultValue) {
        return prefs().getInt(key, defaultValue);
    }

    public static void put(String key, boolean value) {
        prefs().edit().putBoolean(key, value).apply();
    }

    public static void put(String key, String value) {
        prefs().edit().putString(key, value == null ? "" : value).apply();
    }

    public static void put(String key, int value) {
        prefs().edit().putInt(key, value).apply();
    }

    public static boolean ghostEnabled() { return get("ghost_mode", false); }
    public static boolean hideTyping() { return ghostEnabled() && get("ghost_hide_typing", true); }
    public static boolean hideOnline() { return ghostEnabled() && get("ghost_hide_online", true); }
    public static boolean hideReads() { return ghostEnabled() && get("ghost_hide_read", true); }
    public static boolean hideStoryReads() { return ghostEnabled() && get("ghost_hide_story_reads", true); }
    public static boolean forceOffline() { return ghostEnabled() && get("ghost_force_offline", true); }
    public static boolean readAfterReply() { return ghostEnabled() && get("ghost_read_after_reply", false); }
    public static boolean scheduleMessages() { return ghostEnabled() && get("ghost_schedule_messages", false); }
    public static boolean sendWithoutSound() { return ghostEnabled() && get("ghost_send_without_sound", false); }

    private static String chatGhostKey(int account, long dialogId) {
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        return "chat_ghost_" + ownerId + "_" + dialogId;
    }

    public static boolean chatGhostEnabled(int account, long dialogId) {
        return dialogId != 0 && get(chatGhostKey(account, dialogId), false);
    }

    public static void setChatGhostEnabled(int account, long dialogId, boolean enabled) {
        if (dialogId != 0) {
            put(chatGhostKey(account, dialogId), enabled);
        }
    }

    private static String ghostReactionsKey(int account, long dialogId, long topicId) {
        long ownerId = UserConfig.getInstance(account).getClientUserId();
        return "ghost_reactions_read_" + ownerId + "_" + dialogId + "_" + topicId;
    }

    /** True when the user already viewed this dialog's reactions while Ghost suppressed the receipt. */
    public static boolean ghostReactionsReadLocally(int account, long dialogId, long topicId) {
        return dialogId != 0 && get(ghostReactionsKey(account, dialogId, topicId), false);
    }

    public static void setGhostReactionsReadLocally(int account, long dialogId, long topicId, boolean value) {
        if (dialogId == 0) {
            return;
        }
        String key = ghostReactionsKey(account, dialogId, topicId);
        if (value) {
            put(key, true);
        } else {
            prefs().edit().remove(key).apply();
        }
    }

    public static boolean saveDeletedMessages() { return get("archive_deleted_messages", true); }
    public static boolean dimDeletedMessages() { return get("dim_deleted_messages", true); }
    public static boolean saveEditedMessages() { return get("archive_edited_messages", true); }
    public static boolean saveMedia() { return get("archive_media", true); }
    public static boolean saveFormatting() { return get("archive_formatting", true); }
    public static boolean saveReactions() { return get("archive_reactions", true); }
    public static boolean saveBotMessages() { return get("archive_bots", true); }
    public static boolean savePrivateMedia() { return get("archive_media_private", true); }
    public static boolean savePublicGroupMedia() { return get("archive_media_public_groups", false); }
    public static boolean savePrivateGroupMedia() { return get("archive_media_private_groups", true); }
    public static boolean savePublicChannelMedia() { return get("archive_media_public_channels", false); }
    public static boolean savePrivateChannelMedia() { return get("archive_media_private_channels", true); }
    public static int archiveLimitGb() { return Math.max(1, Math.min(10, get("archive_limit_gb", 3))); }

    public static boolean protectedForwarding() { return get("protected_forwarding", true); }
    /** Lets the user capture the screen in chats that ask clients to block it. Secret chats are never affected. */
    public static boolean allowProtectedScreenshots() { return get("allow_protected_screenshots", true); }
    public static boolean messageFilters() { return get("message_filters", false); }
    public static boolean filtersInChats() { return get("message_filters_in_chats", false); }
    public static boolean filtersCaseInsensitive() { return get("message_filters_case_insensitive", true); }

    public static boolean syncEnabled() { return get("tj_sync_enabled", false); }
    public static boolean syncSecure() { return get("tj_sync_secure", true); }
    public static boolean localPremium() { return get("local_premium", false); }
    public static boolean hideSponsoredMessages() { return get("hide_sponsored_messages", true); }
    public static boolean crashReportsEnabled() { return get("crash_reports_enabled", false); }
    /** Plays video files straight from the network instead of waiting for the whole download. */
    public static boolean directFileStreaming() { return get("direct_file_streaming", true); }
    /** Draws a presence dot next to people in the chat list and in member lists. */
    public static boolean showOnlineIndicator() { return get("show_online_indicator", true); }
    /** Keeps the app connected while it is off screen instead of relying on push alone. */
    public static boolean backgroundConnection() { return get("background_connection", true); }
    public static boolean showGhostInDrawer() { return get("show_ghost_in_drawer", true); }
    public static boolean showKillInDrawer() { return get("show_kill_in_drawer", false); }

    public static String deletedMark() { return prefs().getString("deleted_mark", "🗑️"); }
    public static String editedMark() { return prefs().getString("edited_mark", ""); }
    public static String syncServer() { return prefs().getString("tj_sync_server", ""); }
    public static String syncToken() {
        SharedPreferences preferences = prefs();
        String ciphertext = preferences.getString(SYNC_TOKEN_CIPHERTEXT, "");
        String iv = preferences.getString(SYNC_TOKEN_IV, "");
        if (!ciphertext.isEmpty() && !iv.isEmpty()) {
            try {
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getSyncTokenKey(),
                        new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
                return new String(cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)), StandardCharsets.UTF_8);
            } catch (Throwable error) {
                FileLog.e("Tj sync token decryption failed", error);
                return "";
            }
        }
        String legacy = preferences.getString(SYNC_TOKEN_LEGACY, "");
        if (!legacy.isEmpty()) {
            setSyncToken(legacy);
            if (!preferences.contains(SYNC_TOKEN_LEGACY)) {
                return legacy;
            }
        }
        return "";
    }

    public static boolean setSyncToken(String value) {
        SharedPreferences preferences = prefs();
        if (value == null || value.isEmpty()) {
            preferences.edit()
                    .remove(SYNC_TOKEN_LEGACY)
                    .remove(SYNC_TOKEN_CIPHERTEXT)
                    .remove(SYNC_TOKEN_IV)
                    .apply();
            return true;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            FileLog.e("Tj sync token encryption requires Android 6.0 or newer");
            return false;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getSyncTokenKey());
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                    .putString(SYNC_TOKEN_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(SYNC_TOKEN_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .remove(SYNC_TOKEN_LEGACY)
                    .apply();
            return true;
        } catch (Throwable error) {
            FileLog.e("Tj sync token encryption failed", error);
            return false;
        }
    }

    private static SecretKey getSyncTokenKey() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            throw new IllegalStateException("Android Keystore AES requires API 23");
        }
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(SYNC_TOKEN_KEY_ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(SYNC_TOKEN_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(SYNC_TOKEN_KEY_ALIAS, null);
    }
    public static String filterExpressions() { return prefs().getString("message_filter_expressions", ""); }
}
