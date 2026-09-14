package org.telegram.messenger.tj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deciding whether a file really is the thing that was asked for.
 *
 * A search for a name brings back everything that merely contains it, and the containing ones are
 * usually a different work: "Vikings" brings "Vikings: Valhalla", and a Hebrew title brings the
 * sequel that starts with the same word. Those are not the film that was chosen, and offering them
 * in the same list as the real one is how somebody ends up watching the wrong thing.
 *
 * So a candidate has to carry the whole name and nothing else of substance, and its year - when it
 * names one - has to be the year the catalogue gave.
 */
public final class TjTitleMatch {

    public static final int REJECT = -1;

    /**
     * Words that describe a copy rather than a work. Anything left over after these is a real word
     * that the title being looked for did not have, which means this is something else.
     */
    private static final Set<String> NOISE = new HashSet<>(Arrays.asList(
            "1080p", "2160p", "720p", "480p", "4k", "uhd", "hd", "fhd", "sd", "hdr", "hdr10", "dv",
            "web", "webrip", "webdl", "web-dl", "bluray", "brrip", "bdrip", "hdtv", "dvdrip", "dvd",
            "remux", "proper", "repack", "extended", "unrated", "final", "complete", "full",
            "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx", "10bit", "8bit",
            "aac", "ac3", "dts", "ddp", "dd", "eac3", "atmos", "truehd", "mp3", "5", "1", "2", "0",
            "yts", "yify", "rarbg", "evo", "ntb", "galaxytv", "galaxyrg", "psa", "amzn", "nf",
            "netflix", "disney", "hulu", "hbo", "max", "apple", "atvp", "dsnp", "hmax",
            "mkv", "mp4", "avi", "mov", "webm", "part", "season", "episode", "ep",
            "מתורגם", "מדובב", "מדובלל", "לצפייה", "ישירה", "סרט", "סרטים", "פרק", "עונה", "המלא", "איכות",
            "תרגום", "מובנה", "גבוהה", "גבוה", "מאוד", "חדש", "חדשה",
            "مترجم", "مدبلج", "فيلم", "حلقة", "موسم"
    ));

    private TjTitleMatch() {
    }

    /**
     * How well this file answers to one of these names, or {@link #REJECT} when it is something
     * else wearing a similar name.
     *
     * @param targets    the names the catalogue gave for this title - what it is called here, and
     *                   what it was called originally, because files are named either way
     * @param targetYear the year the catalogue gave, or 0 when it gave none
     */
    public static int score(String filename, String caption, List<String> targets, int targetYear,
                            int season, int episode) {
        TjMediaTitle parsed = TjMediaTitle.parse(filename, caption);
        // The name may be written anywhere in the message, so the whole of it is searched, but the
        // parsed title is what decides - it is the part that claims to be a name.
        List<String> haystacks = new ArrayList<>();
        haystacks.add(parsed.title);
        if (filename != null && !filename.isEmpty()) haystacks.add(TjMediaTitle.parse(filename, "").title);
        if (caption != null && !caption.isEmpty()) haystacks.add(TjMediaTitle.parse("", caption).title);

        int best = REJECT;
        for (String target : targets) {
            Set<String> wanted = tokens(target);
            if (wanted.isEmpty()) continue;
            for (String haystack : haystacks) {
                Set<String> candidate = tokens(haystack);
                if (candidate.isEmpty() || !candidate.containsAll(wanted)) continue;
                Set<String> extra = new HashSet<>(candidate);
                extra.removeAll(wanted);
                if (hasRealWord(extra)) continue;
                int score = 100 - extra.size();
                best = Math.max(best, score);
            }
        }
        if (best == REJECT) return REJECT;

        if (parsed.year > 0 && targetYear > 0) {
            // Release dates move by a year between countries; two years apart is another film.
            int distance = Math.abs(parsed.year - targetYear);
            if (distance > 1) return REJECT;
            best += distance == 0 ? 40 : 10;
        }
        if (episode >= 0) {
            if (parsed.episode != episode) return REJECT;
            if (parsed.season >= 0 && season >= 0 && parsed.season != season) return REJECT;
            best += parsed.season == season ? 30 : 10;
        }
        if (!parsed.quality.isEmpty()) best += 5;
        return best;
    }

    /** What this file says it is, for a chooser that has to be read before something is picked. */
    public static String describe(String filename, String caption) {
        TjMediaTitle parsed = TjMediaTitle.parse(filename, caption);
        String title = parsed.title == null ? "" : parsed.title.trim();
        if (title.isEmpty()) return filename == null ? "" : filename;
        return parsed.year > 0 ? title + " (" + parsed.year + ")" : title;
    }

    /** A leftover word with letters in it is a different work; punctuation and codecs are not. */
    private static boolean hasRealWord(Set<String> extra) {
        for (String token : extra) {
            if (token.length() < 3) continue;
            if (NOISE.contains(token)) continue;
            if (!token.matches(".*\\p{L}.*")) continue;
            // A year on its own is handled separately, as a year rather than as a word.
            if (token.matches("(19|20)\\d{2}")) continue;
            return true;
        }
        return false;
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        if (value == null) return result;
        String normalized = TjMediaTitle.normalizeSearch(TjMediaTitle.normalizeDigits(value))
                .replaceAll("[\\p{Punct}־،؛؟]+", " ")
                .toLowerCase(Locale.ROOT);
        for (String token : normalized.split("\\s+")) {
            token = token.trim();
            // "The" and "a" are dropped rather than demanded: half the files leave them out.
            if (token.isEmpty() || token.equals("the") || token.equals("a") || token.equals("an")) continue;
            result.add(token);
        }
        return result;
    }
}
