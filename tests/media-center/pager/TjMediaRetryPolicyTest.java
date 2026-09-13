import org.telegram.messenger.tj.TjMediaRetryPolicy;

public final class TjMediaRetryPolicyTest {
    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        equal(5000, TjMediaRetryPolicy.delay("TIMEOUT", 1));
        equal(10000, TjMediaRetryPolicy.delay("500", 2));
        equal(160000, TjMediaRetryPolicy.delay("RPC_CALL_FAIL", 6));
        equal(-1, TjMediaRetryPolicy.delay("TIMEOUT", 7));
        equal(-1, TjMediaRetryPolicy.delay("CHANNEL_PRIVATE", 1));
        equal(-1, TjMediaRetryPolicy.delay("AUTH_KEY_UNREGISTERED", 1));
        equal(60000, TjMediaRetryPolicy.delay("FLOOD_WAIT_60", 1));
        equal(120000, TjMediaRetryPolicy.delay("FLOOD_PREMIUM_WAIT_120", 9));
        equal(60000, TjMediaRetryPolicy.delay("FLOOD_WAIT_INVALID", 1));
        equal(2147483647000L, TjMediaRetryPolicy.serverWait("FLOOD_WAIT_9999999999"));
        System.out.println("10 retry timing and permanent-error checks passed");
    }
}
