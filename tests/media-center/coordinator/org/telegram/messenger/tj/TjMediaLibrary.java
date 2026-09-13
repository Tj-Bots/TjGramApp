package org.telegram.messenger.tj;
import java.util.*;
public class TjMediaLibrary {
    private final Runnable listener;
    public boolean closed, more=true, loading=true, error;
    public Map<Long, Set<Long>> sources;
    public TjMediaLibrary(Runnable listener, boolean indexOnly) { this.listener=listener; if (!indexOnly) throw new AssertionError(); }
    public void reset(List<Integer> accounts, Map<Long, Set<Long>> sources, int type, String query) { this.sources=TjMediaSources.copy(sources); }
    public boolean isLoading() { return loading; }
    public boolean hasError() { return error; }
    public boolean hasWriteError() { return error; }
    public long nextAutomaticDelay() { return more ? 500 : -1; }
    public void loadMoreAutomatic() { loadMore(); }
    public boolean hasMore() { return more; }
    public void loadMore() { loading=true; }
    public void close() { closed=true; }
    public void finish() { loading=false; more=false; listener.run(); }
}
