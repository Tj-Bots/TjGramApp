package org.telegram.messenger.tj;

/** Pure input/time rules shared by the login UI and standalone regression tests. */
public final class TjLoginRules {
    private TjLoginRules() {}

    public static long botId(String token) {
        if (token == null || token.length() > 256 || !token.matches("[1-9][0-9]{0,18}:[A-Za-z0-9_-]{20,}")) return 0;
        try { return Long.parseLong(token.substring(0, token.indexOf(':'))); }
        catch (NumberFormatException e) { return 0; }
    }

    public static long qrRefreshDelay(int expires, int serverTime) {
        // Cast before subtracting; malformed timestamps must not overflow into a long delay.
        return Math.max(1L, Math.min(60L, (long) expires - serverTime)) * 1000L;
    }
}
