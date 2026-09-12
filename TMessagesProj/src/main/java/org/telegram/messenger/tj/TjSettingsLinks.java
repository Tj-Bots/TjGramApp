package org.telegram.messenger.tj;

import java.net.URI;
import java.net.URISyntaxException;

/** Read-only destinations published in the TjGram FAQ. No account IDs or mutations. */
public final class TjSettingsLinks {
    public enum Section {
        GHOST("ghost"), ARCHIVE("archive"), LOCAL_PREMIUM("local-premium"),
        FOLDERS("folders"), MEDIA_CENTER("media-center"), MEDIA_METADATA("media-metadata"),
        PLAYER("player"), MEDIA_LISTS("media-lists");

        public final String value;

        Section(String value) {
            this.value = value;
        }
    }

    private TjSettingsLinks() { }

    /** Strictly match the published URI shape; reject ambiguous or extra parameters. */
    public static Section parse(String value) {
        if (value == null || value.length() > 256) return null;
        try {
            URI uri = new URI(value);
            if (!"tg".equals(uri.getScheme()) || !"tjgram".equals(uri.getRawAuthority())
                    || !"/settings".equals(uri.getRawPath()) || uri.getRawFragment() != null) {
                return null;
            }
            String query = uri.getRawQuery();
            for (Section section : Section.values()) {
                if (("section=" + section.value).equals(query)) return section;
            }
        } catch (URISyntaxException ignored) {
            // External links are untrusted input, not a reason to crash the activity.
        }
        return null;
    }
}
