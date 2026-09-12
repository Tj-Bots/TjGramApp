import org.telegram.messenger.tj.TjSettingsLinks;

public final class SettingsLinksTest {
    public static void main(String[] args) {
        String[] published = {"ghost", "archive", "local-premium", "folders",
                "media-center", "media-metadata", "player", "media-lists"};
        TjSettingsLinks.Section[] sections = TjSettingsLinks.Section.values();
        if (published.length != sections.length) throw new AssertionError("Update published links");
        for (int i = 0; i < published.length; i++) {
            String uri = "tg://tjgram/settings?section=" + published[i];
            if (TjSettingsLinks.parse(uri) != sections[i]) throw new AssertionError(uri);
            reject(uri + "&account=1");
            reject(uri + "&enabled=true");
            reject(uri + "&section=ghost");
            reject(uri + "#fragment");
        }
        String[] invalid = {null, "", "tg://settings/folders", "tg://tjgram/settings",
                "tg://tjgram/settings?section=", "tg://tjgram/settings?section=unknown",
                "https://tjgram/settings?section=ghost", "tg://tjgram.evil/settings?section=ghost",
                "tg://user@tjgram/settings?section=ghost", "tg://tjgram:80/settings?section=ghost",
                "tg://tjgram/settings/?section=ghost", "tg://tjgram/other?section=ghost",
                "tg://tjgram/settings?section=%67host", "tg://tjgram/settings?section=%",
                " tg://tjgram/settings?section=ghost", "tg://tjgram/settings?section=ghost\n"};
        for (String uri : invalid) reject(uri);
        StringBuilder oversized = new StringBuilder("tg://tjgram/settings?section=");
        while (oversized.length() <= 256) oversized.append('a');
        reject(oversized.toString());
        System.out.println("Settings links: all 8 destinations and invalid-input checks passed");
    }

    private static void reject(String uri) {
        if (TjSettingsLinks.parse(uri) != null) throw new AssertionError("Accepted: " + uri);
    }
}
