import org.telegram.messenger.tj.TjMediaLocalIdentity;
public class TjMediaLocalIdentityTest {
    private static int checks;
    private static void check(boolean value) { if (!value) throw new AssertionError("identity " + checks); checks++; }
    public static void main(String[] args) {
        TjMediaLocalIdentity a = TjMediaLocalIdentity.parse("Show.S01E01.mkv", "");
        TjMediaLocalIdentity b = TjMediaLocalIdentity.parse("Show.S02E08.mp4", "");
        check(a.key.equals(b.key) && !a.key.isEmpty());
        check(a.series && a.season == 1 && b.episode == 8);
        check(TjMediaLocalIdentity.parse("upload.mkv", "שם הסדרה עונה 2 פרק 3").series);
        check(!TjMediaLocalIdentity.parse("upload.mkv", "שם הסדרה עונה 2 פרק 3").key.isEmpty());
        check(TjMediaLocalIdentity.parse("Show.S01E01.mkv", "Show S01E02").key.isEmpty());
        check(TjMediaLocalIdentity.parse("Show.S01E01.mkv", "Other S01E01").uncertain);
        check(TjMediaLocalIdentity.parse("Show.S01E01E02.mkv", "").uncertain);
        check(TjMediaLocalIdentity.parse("video.mp4", "").key.isEmpty());
        check(TjMediaLocalIdentity.parse("Movie.2024.mp4", "").key.isEmpty()); // generic name
        check(!TjMediaLocalIdentity.parse("Arrival.2016.mkv", "").key.equals(TjMediaLocalIdentity.parse("Arrival.2024.mkv", "").key));
        check(!TjMediaLocalIdentity.key("Name", false, 0).equals(TjMediaLocalIdentity.key("Name", true, 0)));
        check(TjMediaLocalIdentity.key("שָׁלוֹם", true, 0).equals(TjMediaLocalIdentity.key("שלום", true, 0)));
        check(TjMediaLocalIdentity.parse("file123.mp4", "").uncertain);
        check(TjMediaLocalIdentity.parse(null, null).uncertain);
        System.out.println(checks + " credential-free local identity checks passed");
    }
}
