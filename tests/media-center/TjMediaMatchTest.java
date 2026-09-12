import org.telegram.messenger.tj.TjMediaTitle;
import org.telegram.messenger.tj.TjMediaMatch;
import java.util.Arrays;

public class TjMediaMatchTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Match check " + checks); }
    public static void main(String[] args) {
        TjMediaMatch.Candidate movie = new TjMediaMatch.Candidate(1, false, "שם הסרט", "Example", 2024);
        TjMediaMatch.Candidate remake = new TjMediaMatch.Candidate(2, false, "Example", "Example", 1990);
        TjMediaMatch.Candidate tv = new TjMediaMatch.Candidate(1, true, "Example", "Example", 2024);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.2024.mkv", ""), Arrays.asList(movie, remake)) == 0);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.mkv", ""), Arrays.asList(movie, remake)) == -1);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.mkv", ""), Arrays.asList(movie, tv)) == -1);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.S01E02.mkv", ""), Arrays.asList(movie, tv)) == 1);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.2025.mkv", ""), Arrays.asList(movie)) == -1);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Other.mkv", ""), Arrays.asList(movie)) == -1);
        check(TjMediaMatch.unique(TjMediaTitle.parse("upload.mkv", "שם הסרט"), Arrays.asList(movie)) == 0);
        check(TjMediaMatch.unique(TjMediaTitle.parse("Example.mkv", ""), Arrays.asList(movie, movie)) == 0);
        check(TjMediaMatch.unique(TjMediaTitle.parse("E.mkv", ""), Arrays.asList(movie)) == -1);
        System.out.println(checks + " conservative metadata match checks passed");
    }
}
