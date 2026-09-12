package org.telegram.messenger.tj;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local naming evidence, not externally verified identity or file-content analysis. */
public final class TjMediaLocalIdentity {
    public final String key, name;
    public final int season, episode, year;
    public final boolean series, uncertain;

    private TjMediaLocalIdentity(String key, String name, int season, int episode, boolean series, boolean uncertain, int year) {
        this.key = key; this.name = name; this.season = season; this.episode = episode;
        this.series = series; this.uncertain = uncertain;
        this.year = year;
    }

    public static TjMediaLocalIdentity parse(String filename, String caption) {
        TjMediaTitle hint = TjMediaTitle.parse(filename, caption);
        TjMediaTitle file = TjMediaTitle.parse(filename, "");
        TjMediaTitle text = TjMediaTitle.parse("", caption);
        String name = hint.title.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
        String normalized = TjMediaTitle.normalizeSearch(name);
        boolean conflict = hint.episodeConflict;
        // If both sources name numbered episodes, different names require a
        // manual alias decision (e.g. translations), not an automatic merge.
        if (file.season >= 0 && text.season >= 0 && !TjMediaTitle.normalizeSearch(file.title)
                .equals(TjMediaTitle.normalizeSearch(text.title))) conflict = true;
        boolean series = hint.season >= 0 || hint.episodeConflict;
        boolean usable = normalized.length() >= 2 && normalized.length() <= 250
                && normalized.matches(".*\\p{L}.*") && !conflict
                && !normalized.matches("(?i)(video|upload|file|movie|episode|פרק|סרטון)(?:\\s*\\d*)?");
        // Unnumbered unknown-year files stay individual until explicitly identified.
        boolean group = usable && (series || hint.year > 0);
        String key = group ? key(name, series, hint.year) : "";
        return new TjMediaLocalIdentity(key, name, hint.season, hint.episode, series, !group, hint.year);
    }

    public static String key(String name, boolean series, int year) {
        String value = (series ? "tv:" : "movie:") + year + ":" + TjMediaTitle.normalizeSearch(name);
        return "local:" + UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
