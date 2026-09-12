import java.util.*;
import org.telegram.messenger.tj.TjMediaPageKey;
import org.telegram.messenger.tj.TjMediaPageMerge;

public final class TjMediaPageMergeTest {
    public static void main(String[] args) {
        Random random = new Random(71842);
        int windows = 0;
        for (int scenario = 0; scenario < 100; scenario++) {
            int owners = 1 + random.nextInt(8), limit = 1 + random.nextInt(25);
            ArrayList<TjMediaPageKey> all = new ArrayList<>();
            for (int owner = 1; owner <= owners; owner++) {
                for (int mid = 0, size = random.nextInt(300); mid < size; mid++)
                    all.add(new TjMediaPageKey(owner, false, random.nextInt(12), random.nextInt(20) - 10, mid));
            }
            all.sort(TjMediaPageKey::compare);
            for (boolean backwards : new boolean[]{false, true}) {
                ArrayList<TjMediaPageKey> expected = new ArrayList<>(all);
                if (backwards) Collections.reverse(expected);
                ArrayList<TjMediaPageKey> seen = new ArrayList<>();
                TjMediaPageKey boundary = null;
                while (true) {
                    TjMediaPageMerge merge = new TjMediaPageMerge(backwards);
                    ArrayList<TjMediaPageKey> candidates = new ArrayList<>();
                    for (int owner = 1; owner <= owners; owner++) {
                        ArrayList<TjMediaPageKey> page = new ArrayList<>();
                        for (TjMediaPageKey key : expected) {
                            if (key.owner == owner && (boundary == null || (backwards ? -1 : 1) * TjMediaPageKey.compare(key, boundary) > 0)) page.add(key);
                        }
                        boolean more = page.size() > limit;
                        if (more) page.subList(limit, page.size()).clear();
                        merge.add(page.isEmpty() ? null : page.get(page.size() - 1), more);
                        candidates.addAll(page);
                    }
                    candidates.removeIf(key -> !merge.includes(key));
                    candidates.sort((a, b) -> (backwards ? -1 : 1) * TjMediaPageKey.compare(a, b));
                    seen.addAll(candidates); windows++;
                    if (!merge.hasMore()) break;
                    if (candidates.isEmpty() || boundary == merge.edge()) throw new AssertionError("No progress");
                    boundary = merge.edge();
                }
                if (!seen.equals(expected)) throw new AssertionError("Skipped/duplicated owner row in scenario " + scenario);
            }
        }
        System.out.println(windows + " production cross-owner merge windows passed (ties, uneven owners, both directions)");
    }
}
