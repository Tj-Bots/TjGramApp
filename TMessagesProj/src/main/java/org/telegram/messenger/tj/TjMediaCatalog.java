package org.telegram.messenger.tj;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure catalog grouping: one title, many original sources, no destructive merging. */
public final class TjMediaCatalog<T> {
    public static final class Source<T> {
        public final String identity;
        public final T value;
        public final int season, episode;
        public Source(String identity, T value, int season, int episode) {
            this.identity = identity;
            this.value = value;
            this.season = season;
            this.episode = episode;
        }
    }

    public static final class Group<T> {
        public final String key;
        public final boolean series;
        public final ArrayList<Source<T>> sources = new ArrayList<>();
        private Group(String key, boolean series) { this.key = key; this.series = series; }

        public Map<Integer, Map<Integer, List<Source<T>>>> seasons() {
            // Unknown numbers follow numbered seasons/episodes, including season 0 specials.
            java.util.Comparator<Integer> order = (a, b) -> Integer.compare(a < 0 ? Integer.MAX_VALUE : a, b < 0 ? Integer.MAX_VALUE : b);
            TreeMap<Integer, Map<Integer, List<Source<T>>>> result = new TreeMap<>(order);
            for (Source<T> source : sources) {
                Map<Integer, List<Source<T>>> episodes = result.get(source.season);
                if (episodes == null) { episodes = new TreeMap<>(order); result.put(source.season, episodes); }
                List<Source<T>> variants = episodes.get(source.episode);
                if (variants == null) { variants = new ArrayList<>(); episodes.put(source.episode, variants); }
                variants.add(source);
            }
            return result;
        }

        /** Next numbered episode actually present, never an invented or unnumbered item. */
        public List<Source<T>> nextEpisode(int season, int episode) {
            ArrayList<Source<T>> result = new ArrayList<>();
            if (!series || season < 0 || episode < 0) return result;
            int nextSeason = Integer.MAX_VALUE, nextEpisode = Integer.MAX_VALUE;
            for (Source<T> source : sources) {
                if (source.season < 0 || source.episode < 0 || source.season < season
                        || source.season == season && source.episode <= episode) continue;
                if (source.season < nextSeason || source.season == nextSeason && source.episode < nextEpisode) {
                    result.clear();
                    nextSeason = source.season;
                    nextEpisode = source.episode;
                }
                if (source.season == nextSeason && source.episode == nextEpisode) result.add(source);
            }
            return result;
        }
    }

    private final LinkedHashMap<String, Group<T>> groups = new LinkedHashMap<>();
    private final java.util.HashSet<String> identities = new java.util.HashSet<>();

    public void add(long metadataId, boolean series, Source<T> source) {
        if (metadataId <= 0) return;
        add(key(metadataId, series), series, source);
    }

    public void add(String key, boolean series, Source<T> source) {
        if (key == null || key.isEmpty() || source == null || !identities.add(source.identity)) return;
        Group<T> group = groups.get(key);
        if (group == null) { group = new Group<>(key, series); groups.put(key, group); }
        group.sources.add(source);
    }

    public static String key(long metadataId, boolean series) { return (series ? "tv:" : "movie:") + metadataId; }
    public Group<T> get(long metadataId, boolean series) { return groups.get(key(metadataId, series)); }
    public Group<T> get(String key) { return groups.get(key); }
    public List<Group<T>> groups() { return new ArrayList<>(groups.values()); }
}
