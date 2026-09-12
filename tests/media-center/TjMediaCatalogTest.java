import org.telegram.messenger.tj.TjMediaCatalog;
import java.util.ArrayList;
import java.util.Arrays;

public class TjMediaCatalogTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Check " + checks); }
    public static void main(String[] args) {
        TjMediaCatalog<String> catalog = new TjMediaCatalog<>();
        catalog.add(1, false, new TjMediaCatalog.Source<>("owner1:7:1", "1080p", -1, -1));
        catalog.add(1, false, new TjMediaCatalog.Source<>("owner2:7:1", "4K", -1, -1));
        catalog.add(1, false, new TjMediaCatalog.Source<>("owner1:7:1", "duplicate", -1, -1));
        catalog.add(1, true, new TjMediaCatalog.Source<>("owner1:7:2", "episode", 2, 3));
        catalog.add(1, true, new TjMediaCatalog.Source<>("owner1:7:3", "unknown", -1, -1));
        catalog.add(1, true, new TjMediaCatalog.Source<>("owner1:7:4", "special", 0, 1));
        catalog.add(1, true, new TjMediaCatalog.Source<>("owner1:7:5", "variant", 2, 3));
        check(catalog.groups().size() == 2);
        check(catalog.get(1, false).sources.size() == 2);
        check(catalog.get(1, true).sources.size() == 4);
        check(new ArrayList<>(catalog.get(1, true).seasons().keySet()).equals(Arrays.asList(0, 2, -1)));
        check(catalog.get(1, true).seasons().get(2).get(3).size() == 2);
        check(catalog.get(1, false).sources.get(1).identity.equals("owner2:7:1"));
        catalog.add(0, false, new TjMediaCatalog.Source<>("unmatched", "unknown", -1, -1));
        check(catalog.groups().size() == 2);
        check(catalog.get(1, true).nextEpisode(0, 1).size() == 2);
        check(catalog.get(1, true).nextEpisode(2, 3).isEmpty());
        check(catalog.get(1, true).nextEpisode(-1, 3).isEmpty());
        check(catalog.get(1, false).nextEpisode(0, 0).isEmpty());
        catalog.add(1, true, new TjMediaCatalog.Source<>("next-season", "S03E01", 3, 1));
        catalog.add(1, true, new TjMediaCatalog.Source<>("episode-gap", "S02E07", 2, 7));
        check(catalog.get(1, true).nextEpisode(2, 3).get(0).episode == 7);
        check(catalog.get(1, true).nextEpisode(2, 7).get(0).season == 3);
        System.out.println(checks + " catalog grouping/next-episode checks passed");
    }
}
