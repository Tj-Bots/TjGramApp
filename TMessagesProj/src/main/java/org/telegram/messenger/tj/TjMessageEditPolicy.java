package org.telegram.messenger.tj;

/** Only selects which edit-age policy applies; all message permission checks remain upstream. */
public final class TjMessageEditPolicy {
    private TjMessageEditPolicy() { }
    public static boolean usesBotEditWindow(boolean bot, long selfId, boolean outgoing, long senderId) {
        return bot && selfId > 0 && (outgoing || senderId == selfId);
    }
}
