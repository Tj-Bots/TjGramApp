import java.util.Locale;
import org.telegram.messenger.tj.TjVideoFormat;

public final class TjVideoFormatTest {
    private static int checks;

    private static void expect(boolean expected, String mime, String filename) {
        checks++;
        if (TjVideoFormat.isSupportedContainer(mime, filename) != expected) {
            throw new AssertionError("Unexpected classification: " + mime + " / " + filename);
        }
    }

    public static void main(String[] args) {
        for (String extension : new String[]{"mp4", "m4v", "mov", "mkv", "webm", "ts", "m2ts", "mts", "flv", "avi", "3gp", "3gpp"}) {
            expect(true, "application/octet-stream", "הרצאה." + extension);
            expect(true, null, "Movie." + extension.toUpperCase(Locale.ROOT));
        }
        for (String mime : new String[]{"video/mp4", "video/x-matroska", "video/webm", "video/quicktime", "video/mp2t", "video/avi", "video/x-msvideo", "video/x-flv", "video/3gpp"}) {
            expect(true, mime, null);
        }
        for (String filename : new String[]{"photo.jpg", "document.pdf", "movie.wmv", "movie.rmvb", "file.zip", "video.mp4.exe"}) {
            expect(false, "video/mp4", filename);
        }
        expect(false, null, null);
        expect(false, "", "");
        expect(false, "application/octet-stream", "without-extension");
        expect(false, "video/unknown", null);
        expect(true, "VIDEO/MP4", "without-extension");
        // Keep the prior trailing-dot fallback, rather than changing player behavior.
        expect(true, "video/mp4", "video.");
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            expect(true, null, "MOVIE.AVI");
        } finally {
            Locale.setDefault(previous);
        }
        System.out.println(checks + " shared video-container recognition checks passed");
    }
}
