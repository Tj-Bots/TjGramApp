package org.telegram.messenger.tj;

import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Links opened as a network stream in Watch, the way a player like VLC keeps them: the last few,
 * to open again with one tap, and where each one stopped. Kept in app-private storage only.
 */
public final class TjWatchStreams {

    public static final class Stream {
        public String url = "", title = "";
        public long position, duration, at;
    }

    private static final String PREFS = "tjwatchstreams";
    private static final String KEY = "recent";
    private static final int LIMIT = 10;
    private static final float FINISHED = 0.97f;

    private TjWatchStreams() { }

    /** A web address a player can open, or null for anything else pasted in. */
    public static Uri parse(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.contains(" ")) return null;
        try {
            Uri uri = Uri.parse(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) return null;
            return TextUtils.isEmpty(uri.getHost()) ? null : uri;
        } catch (Exception e) {
            return null;
        }
    }

    /** What the player should expect behind the link: an HLS or DASH playlist, or a plain file. */
    public static String type(Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8") || path.endsWith(".m3u")) return "hls";
        if (path.endsWith(".mpd")) return "dash";
        if (path.endsWith(".ism") || path.endsWith(".isml") || path.contains(".ism/")) return "ss";
        return "other";
    }

    /** A name for the link: its file name without the extension, or the site it is on. */
    public static String title(Uri uri) {
        String name = uri.getLastPathSegment();
        if (!TextUtils.isEmpty(name)) {
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            name = name.replace('_', ' ').replace('.', ' ').trim();
            if (!name.isEmpty()) return name;
        }
        return uri.getHost() == null ? uri.toString() : uri.getHost();
    }

    public static synchronized ArrayList<Stream> recent() {
        ArrayList<Stream> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs().getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Stream stream = new Stream();
                stream.url = object.optString("u", "");
                stream.title = object.optString("t", "");
                stream.position = object.optLong("p");
                stream.duration = object.optLong("d");
                stream.at = object.optLong("a");
                if (!stream.url.isEmpty()) result.add(stream);
            }
        } catch (Exception e) {
            FileLog.e("Tj stream list read failed", e);
        }
        return result;
    }

    /** Puts the link at the top of the list, keeping where it had got to. */
    public static synchronized void remember(String url, String title) {
        ArrayList<Stream> streams = recent();
        Stream found = null;
        for (int i = streams.size() - 1; i >= 0; i--) {
            if (streams.get(i).url.equals(url)) found = streams.remove(i);
        }
        if (found == null) found = new Stream();
        found.url = url;
        found.title = title == null ? "" : title;
        found.at = System.currentTimeMillis();
        streams.add(0, found);
        while (streams.size() > LIMIT) streams.remove(streams.size() - 1);
        save(streams);
    }

    public static synchronized void progress(String url, long position, long duration) {
        if (duration <= 0 || position < 0) return;
        ArrayList<Stream> streams = recent();
        for (Stream stream : streams) {
            if (!stream.url.equals(url)) continue;
            stream.position = Math.min(position, duration);
            stream.duration = duration;
            save(streams);
            return;
        }
    }

    /** Where to carry on: 0 when never played or played to the end. */
    public static synchronized long positionFor(String url) {
        for (Stream stream : recent()) {
            if (!stream.url.equals(url)) continue;
            if (stream.duration > 0 && stream.position >= stream.duration * FINISHED) return 0;
            return stream.position;
        }
        return 0;
    }

    public static synchronized void clear() {
        prefs().edit().remove(KEY).apply();
    }

    private static void save(ArrayList<Stream> streams) {
        try {
            JSONArray array = new JSONArray();
            for (Stream stream : streams) {
                JSONObject object = new JSONObject();
                object.put("u", stream.url);
                object.put("t", stream.title);
                object.put("p", stream.position);
                object.put("d", stream.duration);
                object.put("a", stream.at);
                array.put(object);
            }
            prefs().edit().putString(KEY, array.toString()).apply();
        } catch (Exception e) {
            FileLog.e("Tj stream list write failed", e);
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }
}
