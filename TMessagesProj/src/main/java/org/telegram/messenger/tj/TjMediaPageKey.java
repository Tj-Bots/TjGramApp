package org.telegram.messenger.tj;

/** Immutable local page boundary: descending time, ascending dialog and message ID. */
public final class TjMediaPageKey {
    public static final String DATE_ORDER = "date DESC, dialog, mid";
    public static final String PLAYED_ORDER = "played DESC, dialog, mid";
    public static final String DATE_AFTER = " AND date<=? AND (date<? OR dialog>? OR (dialog=? AND mid>?))";
    public static final String PLAYED_AFTER = " AND played<=? AND (played<? OR dialog>? OR (dialog=? AND mid>?))";

    public final long owner, time, dialog;
    public final int messageId;
    public final boolean byPlayed;
    public final boolean before;
    public final long boundaryOwner;
    private final boolean crossOwner;

    public TjMediaPageKey(long owner, boolean byPlayed, long time, long dialog, int messageId) {
        this(owner, byPlayed, time, dialog, messageId, false);
    }

    private TjMediaPageKey(long owner, boolean byPlayed, long time, long dialog, int messageId, boolean before) {
        this(owner, byPlayed, time, dialog, messageId, before, owner, false);
    }

    private TjMediaPageKey(long owner, boolean byPlayed, long time, long dialog, int messageId,
                          boolean before, long boundaryOwner, boolean crossOwner) {
        this.owner = owner;
        this.byPlayed = byPlayed;
        this.time = time;
        this.dialog = dialog;
        this.messageId = messageId;
        this.before = before;
        this.boundaryOwner = boundaryOwner;
        this.crossOwner = crossOwner;
    }

    public TjMediaPageKey previous() { return new TjMediaPageKey(owner, byPlayed, time, dialog, messageId, true, boundaryOwner, crossOwner); }

    public static TjMediaPageKey forAccount(long owner, TjMediaPageKey boundary, boolean before) {
        return new TjMediaPageKey(owner, boundary.byPlayed, boundary.time, boundary.dialog,
                boundary.messageId, before, boundary.boundaryOwner, true);
    }

    /** Stable global display order, including timestamp ties across accounts. */
    public static int compare(TjMediaPageKey a, TjMediaPageKey b) {
        int c = Long.compare(b.time, a.time);
        if (c == 0) c = Long.compare(a.boundaryOwner, b.boundaryOwner);
        if (c == 0) c = Long.compare(a.dialog, b.dialog);
        if (c == 0) c = Integer.compare(a.messageId, b.messageId);
        return c;
    }

    public String selection() {
        if (crossOwner) {
            String column = byPlayed ? "played" : "date";
            return before ? " AND " + column + ">=? AND (" + column + ">? OR owner<? OR (owner=? AND (dialog<? OR (dialog=? AND mid<?))))"
                    : " AND " + column + "<=? AND (" + column + "<? OR owner>? OR (owner=? AND (dialog>? OR (dialog=? AND mid>?))))";
        }
        if (before) return byPlayed
                ? " AND played>=? AND (played>? OR dialog<? OR (dialog=? AND mid<?))"
                : " AND date>=? AND (date>? OR dialog<? OR (dialog=? AND mid<?))";
        return byPlayed ? PLAYED_AFTER : DATE_AFTER;
    }

    public String[] arguments() {
        if (crossOwner) return new String[]{Long.toString(time), Long.toString(time), Long.toString(boundaryOwner),
                Long.toString(boundaryOwner), Long.toString(dialog), Long.toString(dialog), Integer.toString(messageId)};
        return new String[]{Long.toString(time), Long.toString(time), Long.toString(dialog),
                Long.toString(dialog), Integer.toString(messageId)};
    }

    public boolean matches(long expectedOwner, boolean expectedOrder) {
        return owner == expectedOwner && byPlayed == expectedOrder;
    }
}
