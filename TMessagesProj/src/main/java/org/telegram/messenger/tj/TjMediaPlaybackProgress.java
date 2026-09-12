package org.telegram.messenger.tj;

/** Keeps natural completion distinct from the player's automatic rewind and user replay. */
public final class TjMediaPlaybackProgress {
    private long completedPosition;
    private boolean rewinding;

    public void complete(long duration) {
        completedPosition = Math.max(0, duration);
        rewinding = true;
    }

    public void finishRewind() { rewinding = false; }
    public void userPositionChanged() { if (!rewinding) completedPosition = 0; }
    public long position(long actual) { return completedPosition > 0 ? completedPosition : actual; }
    public void reset() { completedPosition = 0; rewinding = false; }
}
