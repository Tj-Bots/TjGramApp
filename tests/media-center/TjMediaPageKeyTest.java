import org.telegram.messenger.tj.TjMediaPageKey;

public final class TjMediaPageKeyTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Page-key contract check " + checks);
    }

    public static void main(String[] args) {
        long owner = 9_000_000_001L, time = 1_790_000_000_001L, dialog = -9_000_000_002L;
        TjMediaPageKey played = new TjMediaPageKey(owner, true, time, dialog, Integer.MAX_VALUE);
        check(played.matches(owner, true));
        check(!played.matches(owner + 1, true));
        check(!played.matches(owner, false));
        check(played.selection().equals(TjMediaPageKey.PLAYED_AFTER));
        String[] values = played.arguments();
        check(values.length == 5);
        check(values[0].equals("1790000000001") && values[1].equals(values[0]));
        check(values[2].equals("-9000000002") && values[3].equals(values[2]));
        check(values[4].equals("2147483647"));
        values[0] = "changed";
        check(played.arguments()[0].equals("1790000000001"));
        TjMediaPageKey date = new TjMediaPageKey(owner, false, 0, 5, 1);
        check(date.selection().equals(TjMediaPageKey.DATE_AFTER));
        check(date.arguments()[0].equals("0"));
        check(date.matches(owner, false));
        TjMediaPageKey global = TjMediaPageKey.forAccount(owner + 1, played, false);
        check(global.matches(owner + 1, true) && !global.matches(owner, true));
        check(global.boundaryOwner == owner && global.owner == owner + 1);
        check(global.arguments().length == 7 && global.arguments()[2].equals(Long.toString(owner)));
        check(global.selection().contains("owner>?") && global.previous().selection().contains("owner<?"));
        check(global.previous().before && global.previous().boundaryOwner == owner);
        check(TjMediaPageKey.compare(played, new TjMediaPageKey(owner + 1, true, time, dialog, Integer.MAX_VALUE)) < 0);
        check(TjMediaPageKey.compare(played, new TjMediaPageKey(owner, true, time - 1, dialog, Integer.MAX_VALUE)) < 0);
        check(TjMediaPageKey.compare(played, new TjMediaPageKey(owner, true, time, dialog - 1, Integer.MAX_VALUE)) > 0);
        check(TjMediaPageKey.compare(played, new TjMediaPageKey(owner, true, time, dialog, Integer.MAX_VALUE - 1)) > 0);
        System.out.println(checks + " page-key owner/order/64-bit/immutability checks passed");
    }
}
