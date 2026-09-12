import org.telegram.messenger.tj.TjMediaTitle;

public class TjMediaTitleTest {
    private static int checks;

    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Check " + checks);
    }

    public static void main(String[] args) {
        TjMediaTitle movie = TjMediaTitle.parse("upload_918.mkv", "שם הסרט (2024) 1080p\nתיאור נוסף");
        check(movie.title.equals("שם הסרט"));
        check(movie.year == 2024);
        check(movie.quality.equals("1080P"));
        check(movie.season == -1);
        TjMediaTitle series = TjMediaTitle.parse("Example.Show.S02E03.720p.mkv", null);
        check(series.title.equals("Example Show"));
        check(series.season == 2 && series.episode == 3);
        check(TjMediaTitle.parse("Series 1x04.mp4", "").episode == 4);
        check(TjMediaTitle.parse("lecture.pdf", "הרצאה על פיזיקה").title.equals("הרצאה על פיזיקה"));
        check(TjMediaTitle.matches("random.mkv", "שם הסרט", "שם הסרט"));
        check(TjMediaTitle.matches("My.Movie.mkv", "איכות גבוהה", "movie גבוהה"));
        check(!TjMediaTitle.matches("movie.mkv", "", "missing"));
        check(TjMediaTitle.matches(null, null, null));
        check(TjMediaTitle.parse(null, null).title.isEmpty());
        check(TjMediaTitle.parse("Actual.mp4", "https://example.org").title.equals("Actual"));
        check(TjMediaTitle.parse("1917.mkv", "").title.equals("1917"));
        check(TjMediaTitle.parse("1917.mkv", "").year == 0);
        check(TjMediaTitle.parse("1917.2019.1080p.mkv", "").year == 2019);
        check(TjMediaTitle.parse("1917.2019.1080p.mkv", "").title.equals("1917"));
        TjMediaTitle hebrew = TjMediaTitle.parse("upload.mp4", "שם הסדרה עונה 2 פרק 3 1080p");
        check(hebrew.title.equals("שם הסדרה"));
        check(hebrew.season == 2 && hebrew.episode == 3);
        check(TjMediaTitle.matches("CAFÉ.mkv", "", "cafe"));
        check(TjMediaTitle.matches("", "שָׁלוֹם", "שלום"));
        check(TjMediaTitle.matches("ФИЛЬМ.mkv", "", "фильм"));
        check(TjMediaTitle.matches("My-Movie_1080p.mkv", "", "my movie"));
        check(TjMediaTitle.parse("Show.S01E04.mkv", "Show S01E05").episodeConflict);
        check(TjMediaTitle.parse("Show.S01E04.mkv", "Show S01E05").episode == -1);
        check(!TjMediaTitle.parse("Show.S01E04.mkv", "Show עונה 1 פרק 4").episodeConflict);
        check(TjMediaTitle.parse("Show.S01E04E05.mkv", "").episodeConflict);
        check(TjMediaTitle.parse("Show.S01E04-E05.mkv", "").episodeConflict);
        System.out.println(checks + " media title checks passed");
    }
}
