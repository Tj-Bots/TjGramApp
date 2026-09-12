package org.telegram.messenger.tj;

/** Finds the fully fetched prefix shared by bounded, independently paged owners. */
public final class TjMediaPageMerge {
    private final int direction;
    private TjMediaPageKey cutoff, furthest;

    public TjMediaPageMerge(boolean backwards) { direction = backwards ? -1 : 1; }

    public void add(TjMediaPageKey edge, boolean hasMore) {
        if (edge == null) return;
        if (furthest == null || direction * TjMediaPageKey.compare(edge, furthest) > 0) furthest = edge;
        if (hasMore && (cutoff == null || direction * TjMediaPageKey.compare(edge, cutoff) < 0)) cutoff = edge;
    }

    public boolean hasMore() { return cutoff != null; }
    public TjMediaPageKey edge() { return cutoff != null ? cutoff : furthest; }
    public boolean includes(TjMediaPageKey key) {
        return cutoff == null || direction * TjMediaPageKey.compare(key, cutoff) <= 0;
    }
}
