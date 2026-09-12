package org.telegram.messenger.tj;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local search/title hints only: a parsed name is not a verified metadata match. */
public final class TjMediaTitle {
    private static final Pattern EPISODE = Pattern.compile("(?i)(?<![\\p{L}\\d])s(\\d{1,3})[ ._-]*e(\\d{1,4})(?!\\d)|(?<!\\d)(\\d{1,3})x(\\d{1,4})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final Pattern HEBREW_EPISODE = Pattern.compile("עונה\\s*(\\d{1,3})[\\s,._:-]*פרק\\s*(\\d{1,4})(?!\\d)");
    private static final Pattern QUALITY = Pattern.compile("(?i)(?<![\\p{L}\\d])(2160p|1080p|720p|480p|4k)(?![\\p{L}\\d])");
    private static final Pattern MULTI_EPISODE = Pattern.compile("(?i)s\\d{1,3}[ ._-]*e\\d{1,4}(?:e|[-+]e?)\\d{1,4}");
    private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(mkv|mp4|avi|mov|webm|m4v|ts|mp3|flac|pdf|zip)$");
    public final String title;
    public final int year;
    public final int season;
    public final int episode;
    public final String quality;
    public final boolean episodeConflict;

    private TjMediaTitle(String title, int year, int season, int episode, String quality, boolean episodeConflict) {
        this.title = title;
        this.year = year;
        this.season = season;
        this.episode = episode;
        this.quality = quality;
        this.episodeConflict = episodeConflict;
    }

    public static TjMediaTitle parse(String filename, String caption) {
        String file = filename == null ? "" : filename.trim();
        String text = caption == null ? "" : caption.trim();
        // Captions often carry the real title while filenames contain upload IDs.
        String firstLine = text.isEmpty() ? "" : text.split("\\r?\\n", 2)[0].trim();
        String candidate = firstLine.isEmpty() || firstLine.startsWith("http://")
                || firstLine.startsWith("https://") ? file : firstLine;
        candidate = EXTENSION.matcher(candidate).replaceFirst("");
        String combined = text + "\n" + file;
        Matcher episodeMatch = EPISODE.matcher(combined);
        int season = -1;
        int episode = -1;
        if (episodeMatch.find()) {
            season = Integer.parseInt(episodeMatch.group(1) != null ? episodeMatch.group(1) : episodeMatch.group(3));
            episode = Integer.parseInt(episodeMatch.group(2) != null ? episodeMatch.group(2) : episodeMatch.group(4));
        } else {
            Matcher hebrewEpisode = HEBREW_EPISODE.matcher(combined);
            if (hebrewEpisode.find()) {
                season = Integer.parseInt(hebrewEpisode.group(1));
                episode = Integer.parseInt(hebrewEpisode.group(2));
            }
        }
        boolean conflict = false;
        // Repeated identical labels are harmless. Conflicting filename/caption
        // labels or multiple episode labels must not silently pick the first.
        for (Pattern pattern : new Pattern[]{EPISODE, HEBREW_EPISODE}) {
            Matcher labels = pattern.matcher(combined);
            while (labels.find()) {
                int s = Integer.parseInt(pattern == HEBREW_EPISODE ? labels.group(1)
                        : labels.group(1) != null ? labels.group(1) : labels.group(3));
                int e = Integer.parseInt(pattern == HEBREW_EPISODE ? labels.group(2)
                        : labels.group(2) != null ? labels.group(2) : labels.group(4));
                if (s != season || e != episode) conflict = true;
            }
        }
        if (MULTI_EPISODE.matcher(combined).find()) conflict = true;
        if (conflict) { season = -1; episode = -1; }
        Matcher yearMatch = YEAR.matcher(combined);
        int year = 0;
        while (yearMatch.find()) {
            int lineStart = combined.lastIndexOf('\n', yearMatch.start()) + 1;
            // A leading number can itself be the title (e.g. 1917), not a release year.
            if (!combined.substring(lineStart, yearMatch.start()).replaceAll("[\\s\\[\\](){}._-]", "").isEmpty()) {
                year = Integer.parseInt(yearMatch.group(1));
                break;
            }
        }
        Matcher qualityMatch = QUALITY.matcher(combined);
        String quality = qualityMatch.find() ? qualityMatch.group(1).toUpperCase(Locale.ROOT) : "";
        int end = candidate.length();
        for (Pattern pattern : new Pattern[]{EPISODE, HEBREW_EPISODE, YEAR, QUALITY}) {
            Matcher boundary = pattern.matcher(candidate);
            while (boundary.find()) {
                if (boundary.start() > 0) {
                    end = Math.min(end, boundary.start());
                    break;
                }
            }
        }
        String title = candidate.substring(0, end).replace('_', ' ').replace('.', ' ')
                .replaceAll("[\\s\\[\\](){}-]+$", "").replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) {
            title = candidate;
        }
        return new TjMediaTitle(title, year, season, episode, quality, conflict);
    }

    public static boolean matches(String filename, String caption, String query) {
        String normalized = normalizeSearch(query).trim();
        if (normalized.isEmpty()) {
            return true;
        }
        String haystack = normalizeSearch(filename) + " " + normalizeSearch(caption);
        for (String token : normalized.split("\\s+")) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeSearch(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT)
                .replace('_', ' ').replace('.', ' ').replace('-', ' ').replaceAll("\\s+", " ").trim();
    }
}
