package org.telegram.messenger.tj;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/** Bounded, versioned scan position. No messages or downloaded media are retained here. */
public final class TjMediaScanState {
    private static final int VERSION = 1;
    public final Cursor history, recent;
    public final int refreshedThrough;

    public static final class Cursor {
        public final int minDate, maxDate, offsetId, offsetRate, peerKind;
        public final long peerId, accessHash;
        public final boolean complete;

        public Cursor(int minDate, int maxDate, int offsetId, int offsetRate, int peerKind,
                      long peerId, long accessHash, boolean complete) {
            if (minDate < 0 || maxDate <= minDate || offsetId < 0 || peerKind < 0 || peerKind > 4
                    || (peerKind >= 2 && peerId <= 0)) throw new IllegalArgumentException("Invalid scan cursor");
            this.minDate = minDate;
            this.maxDate = maxDate;
            this.offsetId = offsetId;
            this.offsetRate = offsetRate;
            this.peerKind = peerKind;
            this.peerId = peerId;
            this.accessHash = accessHash;
            this.complete = complete;
        }

        public Cursor finish() {
            return new Cursor(minDate, maxDate, offsetId, offsetRate, peerKind, peerId, accessHash, true);
        }
    }

    private TjMediaScanState(Cursor history, Cursor recent, int refreshedThrough) {
        if (refreshedThrough < 0) throw new IllegalArgumentException("Invalid scan watermark");
        this.history = history;
        this.recent = recent;
        this.refreshedThrough = refreshedThrough;
    }

    public static TjMediaScanState initial(int now) {
        int ceiling = ceiling(now);
        Cursor history = new Cursor(0, ceiling, 0, 0, 0, 0, 0, false);
        // History covers the initial time boundary; refresh begins on a subsequent run.
        return new TjMediaScanState(history, new Cursor(Math.max(0, now - 1), ceiling,
                0, 0, 0, 0, 0, true), now);
    }

    private static int ceiling(int now) {
        if (now <= 0 || now == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid server time");
        return now + 1;
    }

    /** Never discard a partially completed refresh when reopening the library. */
    public TjMediaScanState refresh(int now) {
        if (!recent.complete || now < refreshedThrough) return this;
        return new TjMediaScanState(history, new Cursor(Math.max(0, refreshedThrough - 1),
                ceiling(now), 0, 0, 0, 0, 0, false), refreshedThrough);
    }

    public TjMediaScanState advance(boolean refresh, Cursor next) {
        Cursor previous = refresh ? recent : history;
        if (next.minDate != previous.minDate || next.maxDate != previous.maxDate)
            throw new IllegalArgumentException("Scan window changed within a page");
        return new TjMediaScanState(refresh ? history : next, refresh ? next : recent,
                refresh && next.complete ? Math.max(refreshedThrough, next.maxDate - 1) : refreshedThrough);
    }

    public byte[] encode() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(VERSION);
            out.writeInt(refreshedThrough);
            write(out, history);
            write(out, recent);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }

    public static TjMediaScanState decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length != 82) throw new IOException("Invalid scan position size");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
        if (in.readInt() != VERSION) throw new IOException("Unsupported scan position version");
        int through = in.readInt();
        try { return new TjMediaScanState(read(in), read(in), through); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid scan position", e); }
    }

    private static void write(DataOutputStream out, Cursor c) throws IOException {
        out.writeInt(c.minDate); out.writeInt(c.maxDate); out.writeInt(c.offsetId);
        out.writeInt(c.offsetRate); out.writeInt(c.peerKind); out.writeLong(c.peerId);
        out.writeLong(c.accessHash); out.writeBoolean(c.complete);
    }

    private static Cursor read(DataInputStream in) throws IOException {
        return new Cursor(in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(),
                in.readLong(), in.readLong(), in.readBoolean());
    }
}
