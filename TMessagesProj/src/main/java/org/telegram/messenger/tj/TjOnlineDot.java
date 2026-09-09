package org.telegram.messenger.tj;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

/**
 * The presence dot TjGram draws next to a person.
 *
 * Telegram only marks people who are online, and only in the chat list. Showing the dot for
 * everyone - filled while they are connected, neutral while they are not - makes presence
 * readable at a glance next to any profile, in groups as well as in the chat list.
 */
public final class TjOnlineDot {

    private TjOnlineDot() {
    }

    public static boolean isEnabled() {
        return TjConfig.showOnlineIndicator();
    }

    /** Mirrors Telegram's own presence test: an explicit expiry, or a recent hidden-status hint. */
    public static boolean isOnline(int account, TLRPC.User user) {
        if (user == null || user.self || user.bot || MessagesController.isSupportUser(user)) {
            return false;
        }
        if (user.status == null) {
            return false;
        }
        if (user.status.expires <= 0) {
            return MessagesController.getInstance(account).onlinePrivacy.containsKey(user.id);
        }
        return user.status.expires > ConnectionsManager.getInstance(account).getCurrentTime();
    }

    /** People who get a dot at all: real users other than yourself, bots and support accounts. */
    public static boolean showsFor(TLRPC.User user) {
        return isEnabled() && user != null && !user.self && !user.bot && !MessagesController.isSupportUser(user);
    }

    public static int color(boolean online, Theme.ResourcesProvider resourcesProvider) {
        if (online) {
            return Theme.getColor(Theme.key_chats_onlineCircle, resourcesProvider);
        }
        // Deliberately not the plain background colour: on a light theme a white dot on a white
        // ring would be invisible, so this reads as white on dark and as a soft grey on light.
        return Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider), 0.55f);
    }

    /**
     * Draws the dot in the bottom-trailing corner of a square avatar whose top-left corner is at
     * (avatarLeft, avatarTop).
     */
    public static void draw(Canvas canvas, float avatarLeft, float avatarTop, float avatarSize,
                            boolean online, boolean rtl, Theme.ResourcesProvider resourcesProvider) {
        float cx = rtl ? avatarLeft + dp(6) : avatarLeft + avatarSize - dp(6);
        float cy = avatarTop + avatarSize - dp(6);
        Theme.dialogs_onlineCirclePaint.setColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider));
        canvas.drawCircle(cx, cy, dp(6.5f), Theme.dialogs_onlineCirclePaint);
        Theme.dialogs_onlineCirclePaint.setColor(color(online, resourcesProvider));
        canvas.drawCircle(cx, cy, dp(4.5f), Theme.dialogs_onlineCirclePaint);
    }
}
