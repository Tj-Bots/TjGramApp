package org.telegram.messenger.tj;

/** Network retry timing only; storage failures must never advance the scan. */
public final class TjMediaRetryPolicy {
    private TjMediaRetryPolicy() { }

    public static long serverWait(String code) {
        if (code == null || !(code.startsWith("FLOOD_WAIT_") || code.startsWith("FLOOD_PREMIUM_WAIT_"))) return 0;
        try {
            long seconds = Long.parseLong(code.substring(code.lastIndexOf('_') + 1));
            return seconds <= 0 ? 1000 : Math.min(seconds, Integer.MAX_VALUE) * 1000L;
        } catch (NumberFormatException invalid) { return 60_000; }
    }

    public static long delay(String code, int attempt) {
        long wait = serverWait(code);
        if (wait > 0) return wait;
        if (attempt > 6) return -1;
        if (code == null || code.equals("TIMEOUT") || code.equals("UNEXPECTED_RESPONSE")
                || code.equals("INTERNAL") || code.equals("RPC_CALL_FAIL")
                || code.equals("RPC_MCGET_FAIL") || code.equals("MSG_WAIT_FAILED")
                || code.equals("-1") || code.matches("5[0-9][0-9]")) {
            return Math.min(300_000L, 5_000L << Math.max(0, Math.min(6, attempt - 1)));
        }
        return -1;
    }
}
