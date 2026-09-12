import org.telegram.messenger.tj.TjLoginRules;

/** Run with javac/java; uses synthetic credentials only and never connects to Telegram. */
public final class TjLoginRulesTest {
    private static int assertions;
    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("Login rule assertion " + assertions);
    }
    public static void main(String[] args) {
        String secret = "ABCDEFGHIJKLMNOPQRST_uv-wxyz";
        check(TjLoginRules.botId("123456789:" + secret) == 123456789L);
        check(TjLoginRules.botId("9223372036854775807:" + secret) == Long.MAX_VALUE);
        for (String invalid : new String[]{null, "", "12345", ":" + secret,
                "0:" + secret, "-1:" + secret, "01:" + secret, "1:short",
                "1:" + secret + " ", "1:" + secret + "\n", "1:" + secret + ":x",
                "9223372036854775808:" + secret, "1:" + secret.repeat(20)}) {
            check(TjLoginRules.botId(invalid) == 0);
        }
        check(TjLoginRules.qrRefreshDelay(130, 100) == 30_000);
        check(TjLoginRules.qrRefreshDelay(100, 100) == 1_000);
        check(TjLoginRules.qrRefreshDelay(90, 100) == 1_000);
        check(TjLoginRules.qrRefreshDelay(Integer.MAX_VALUE, Integer.MIN_VALUE) == 60_000);
        check(TjLoginRules.qrRefreshDelay(Integer.MIN_VALUE, Integer.MAX_VALUE) == 1_000);
        System.out.println("PASS: " + assertions + " login rule assertions");
    }
}
