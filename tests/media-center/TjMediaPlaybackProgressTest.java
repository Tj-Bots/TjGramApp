import org.telegram.messenger.tj.TjMediaPlaybackProgress;

public final class TjMediaPlaybackProgressTest {
    private static int checks;
    private static void equal(long expected, long actual) {
        checks++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        TjMediaPlaybackProgress progress = new TjMediaPlaybackProgress();
        equal(3500, progress.position(3500));
        progress.complete(4000);
        equal(4000, progress.position(3999));
        progress.userPositionChanged(); // Telegram's automatic rewind emits seek/play callbacks.
        equal(4000, progress.position(0));
        progress.finishRewind();
        equal(4000, progress.position(0)); // Closing after rewind preserves completion.
        progress.userPositionChanged(); // Explicit replay or seek starts a new position.
        equal(0, progress.position(0));
        equal(500, progress.position(500));
        progress.complete(4000);
        progress.finishRewind();
        progress.userPositionChanged();
        equal(2000, progress.position(2000));
        progress.complete(4000);
        progress.reset(); // A different message/player cannot inherit completion.
        equal(100, progress.position(100));
        progress.complete(-1);
        equal(100, progress.position(100));
        System.out.println(checks + " playback completion/replay checks passed");
    }
}
