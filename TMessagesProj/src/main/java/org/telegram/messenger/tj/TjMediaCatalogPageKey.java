package org.telegram.messenger.tj;

/** Alphabetical title boundary, independent of any one source message. */
public final class TjMediaCatalogPageKey {
    public final String title, key;
    public final boolean before;

    public TjMediaCatalogPageKey(String title, String key, boolean before) {
        this.title = title; this.key = key; this.before = before;
    }

    public String selection() {
        return before ? " AND t.title<=? AND (t.title<? OR t.title_key<?)"
                : " AND t.title>=? AND (t.title>? OR t.title_key>?)";
    }

    public String[] arguments() { return new String[]{title, title, key}; }
}
