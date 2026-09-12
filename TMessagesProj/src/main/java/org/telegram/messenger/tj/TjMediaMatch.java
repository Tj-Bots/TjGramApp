package org.telegram.messenger.tj;

import java.util.List;

/** Conservative automatic matching; ambiguity is a reason to leave media unmatched. */
public final class TjMediaMatch {
    public static final class Candidate {
        public final long id;
        public final boolean series;
        public final String name, originalName;
        public final int year;
        public Candidate(long id, boolean series, String name, String originalName, int year) {
            this.id = id;
            this.series = series;
            this.name = name;
            this.originalName = originalName;
            this.year = year;
        }
    }

    public static int unique(TjMediaTitle hint, List<Candidate> candidates) {
        String name = TjMediaTitle.normalizeSearch(hint.title);
        if (name.length() < 2) return -1;
        int match = -1;
        for (int i = 0; i < candidates.size(); i++) {
            Candidate candidate = candidates.get(i);
            if (candidate.id <= 0 || hint.season >= 0 && !candidate.series) continue;
            if (!name.equals(TjMediaTitle.normalizeSearch(candidate.name))
                    && !name.equals(TjMediaTitle.normalizeSearch(candidate.originalName))) continue;
            if (hint.year > 0 && hint.year != candidate.year) continue;
            if (match >= 0) {
                Candidate previous = candidates.get(match);
                if (previous.id != candidate.id || previous.series != candidate.series) return -1;
            } else match = i;
        }
        return match;
    }

    private TjMediaMatch() { }
}
