package org.telegram.messenger.tj;

import java.net.URI;
import java.net.URISyntaxException;

/** Read-only destinations published in the TjGram FAQ. No account IDs or mutations. */
public final class TjSettingsLinks {
    public enum Section {
        GHOST("ghost"), ARCHIVE("archive"), LOCAL_PREMIUM("local-premium"),
        FOLDERS("folders"), MEDIA_CENTER("media-center"), MEDIA_METADATA("media-metadata"),
        PLAYER("player"), MEDIA_LISTS("media-lists"), GENERAL("general");

        public final String value;

        Section(String value) {
            this.value = value;
        }
    }

    /** A setting name in a link: the preference key, and nothing that is not one. */
    private static final java.util.regex.Pattern ITEM = java.util.regex.Pattern.compile("[a-z0-9_]{1,64}");

    private TjSettingsLinks() { }

    /**
     * The link that points at one setting. Opening it walks to the setting and marks it - it never
     * changes anything. A link that could turn a switch on or off would mean anyone who can post a
     * message could flip somebody's ghost mode or their archive, and a forwarded message is not a
     * good enough reason to change what a person chose.
     */
    public static String build(Section section, String item) {
        String url = "tg://tjgram/settings?section=" + section.value;
        if (item != null && ITEM.matcher(item).matches()) url += "&item=" + item;
        return url;
    }

    /** Strictly match the published URI shape; reject ambiguous or extra parameters. */
    public static Section parse(String value) {
        String[] parts = split(value);
        if (parts == null) return null;
        for (Section section : Section.values()) {
            if (section.value.equals(parts[0])) return section;
        }
        return null;
    }

    /** The setting named by the link, when it names one at all. */
    public static String item(String value) {
        String[] parts = split(value);
        return parts == null || parts[1] == null || !ITEM.matcher(parts[1]).matches() ? null : parts[1];
    }

    /** {section, item} from a link of the published shape, or null when it is not one. */
    private static String[] split(String value) {
        if (value == null || value.length() > 256) return null;
        try {
            URI uri = new URI(value);
            if (!"tg".equals(uri.getScheme()) || !"tjgram".equals(uri.getRawAuthority())
                    || !"/settings".equals(uri.getRawPath()) || uri.getRawFragment() != null) {
                return null;
            }
            String query = uri.getRawQuery();
            if (query == null) return null;
            String section = null, item = null;
            for (String pair : query.split("&")) {
                if (pair.startsWith("section=")) {
                    if (section != null) return null;
                    section = pair.substring("section=".length());
                } else if (pair.startsWith("item=")) {
                    if (item != null) return null;
                    item = pair.substring("item=".length());
                } else {
                    // An unknown parameter means this is not a link this build published.
                    return null;
                }
            }
            return section == null ? null : new String[]{section, item};
        } catch (URISyntaxException ignored) {
            // External links are untrusted input, not a reason to crash the activity.
        }
        return null;
    }
}
