package org.telegram.messenger.tj;

import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * What was watched, and where it stopped.
 *
 * The media library only remembers a position for a file it has already indexed, which is the file
 * in a chat this device happened to scan. Watching starts from the catalogue instead, and the copy
 * it finds usually lives in a channel nobody scanned - so nothing was remembered, and the next time
 * the same episode started from the beginning.
 *
 * This keeps its own short list, one row per title rather than one per file, so a series shows the
 * episode that was last left unfinished. The message itself is kept alongside it: the row has to be
 * able to open the same copy again without searching for it a second time, and a chat may be gone
 * from the index (or never have been in it) by then. Everything is app-private, and a row is only
 * ever read back for the account that wrote it.
 */
public final class TjWatchHistory {

    private static final String PREFS = "tjwatchhistory";
    private static final String KEY = "entries";
    /** Enough to cover what anyone is part-way through, short enough to stay a preference. */
    private static final int LIMIT = 40;
    /** Past this much of the running time it is finished, not interrupted. */
    private static final float FINISHED = 0.97f;

    private static ArrayList<Entry> cache;

    private TjWatchHistory() { }

    /** One title, and the copy of it that was last played. */
    public static final class Entry {
        public String key = "", name = "", poster = "";
        public boolean series;
        public int year, season = -1, episode = -1;
        public int account, messageId;
        public long owner, documentId, position, duration, updatedAt;
        private byte[] data;
        private MessageObject rebuilt;

        public boolean finished() {
            return duration > 0 && position >= duration * FINISHED;
        }

        /** How far in, as a fraction, for the bar drawn across the artwork. */
        public float progress() {
            if (duration <= 0) return 0;
            return Math.max(0f, Math.min(1f, position / (float) duration));
        }

        /** The message this row plays, rebuilt on demand, or null once the account is gone. */
        public MessageObject message() {
            if (rebuilt != null) return rebuilt;
            if (data == null || UserConfig.getInstance(account).getClientUserId() != owner) return null;
            TLRPC.Message raw = deserialize(data);
            if (raw == null) return null;
            rebuilt = new MessageObject(account, raw, false, false);
            return rebuilt;
        }
    }

    /** The catalogue's own name for a title, which is what a row is keyed by. */
    public static String key(long id, boolean series) {
        return (series ? "tv:" : "movie:") + id;
    }

    /**
     * Records that this copy was opened. One row per title: starting a new episode replaces the
     * one before it, the way a list of what to carry on with should behave.
     */
    public static void remember(Entry entry) {
        if (entry == null || entry.key.isEmpty() || entry.message() == null) return;
        synchronized (TjWatchHistory.class) {
            ArrayList<Entry> entries = load();
            for (int a = entries.size() - 1; a >= 0; a--) {
                Entry existing = entries.get(a);
                if (existing.key.equals(entry.key) && existing.owner == entry.owner) entries.remove(a);
            }
            entry.updatedAt = System.currentTimeMillis();
            entries.add(0, entry);
            while (entries.size() > LIMIT) entries.remove(entries.size() - 1);
            save(entries);
        }
    }

    /** Where this copy stopped last time, or 0 if it was never played. */
    public static long positionFor(int account, long documentId) {
        if (documentId == 0) return 0;
        long owner = UserConfig.getInstance(account).getClientUserId();
        synchronized (TjWatchHistory.class) {
            for (Entry entry : load()) {
                if (entry.owner == owner && entry.documentId == documentId) {
                    return entry.finished() ? 0 : entry.position;
                }
            }
        }
        return 0;
    }

    /** Called while a video plays, for whichever row is about that file. */
    public static void progress(MessageObject message, long position, long duration) {
        if (message == null || message.getDocument() == null || duration <= 0 || position < 0) return;
        long owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
        long document = message.getDocument().id;
        synchronized (TjWatchHistory.class) {
            ArrayList<Entry> entries = load();
            boolean changed = false;
            for (Entry entry : entries) {
                if (entry.owner != owner || entry.documentId != document) continue;
                entry.position = Math.min(position, duration);
                entry.duration = duration;
                entry.updatedAt = System.currentTimeMillis();
                changed = true;
            }
            if (changed) save(entries);
        }
    }

    /** Everything still part-way through, most recent first. */
    public static ArrayList<Entry> unfinished() {
        return newestPerTitle(true);
    }

    /** Everything that was watched, most recent first, finished or not. */
    public static ArrayList<Entry> all() {
        return newestPerTitle(false);
    }

    /**
     * One row per title, however many accounts it was watched through. Each account keeps its own
     * rows, but the copy of the next episode is often found through a different account than the
     * one before it - and the list showed episode 1 and episode 2 side by side.
     */
    private static ArrayList<Entry> newestPerTitle(boolean unfinishedOnly) {
        ArrayList<Entry> candidates = new ArrayList<>();
        synchronized (TjWatchHistory.class) {
            for (Entry entry : load()) {
                if (UserConfig.getInstance(entry.account).getClientUserId() != entry.owner) continue;
                candidates.add(entry);
            }
        }
        java.util.Collections.sort(candidates, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        ArrayList<Entry> result = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (Entry entry : candidates) {
            if (!seen.add(entry.key)) continue;
            if (unfinishedOnly && entry.finished()) continue;
            result.add(entry);
        }
        return result;
    }

    /**
     * Forgets a title. The list shows one row for it whichever account watched which episode, so
     * forgetting that row forgets it for every account signed in here, episodes' progress included.
     */
    public static void forget(String key, long owner) {
        if (key == null || key.isEmpty()) return;
        synchronized (TjWatchHistory.class) {
            ArrayList<Entry> entries = load();
            boolean changed = false;
            for (int a = entries.size() - 1; a >= 0; a--) {
                Entry entry = entries.get(a);
                if (entry.key.equals(key) && (entry.owner == owner || belongsToSignedInAccount(entry.owner))) {
                    entries.remove(a);
                    changed = true;
                }
            }
            if (changed) save(entries);
            JSONObject episodes = loadEpisodes();
            java.util.Iterator<String> names = episodes.keys();
            ArrayList<String> drop = new ArrayList<>();
            while (names.hasNext()) {
                String name = names.next();
                if (name.contains(":" + key + ":")) drop.add(name);
            }
            for (String name : drop) episodes.remove(name);
            if (!drop.isEmpty()) saveEpisodes(episodes);
            JSONObject copies = loadCopies();
            ArrayList<String> dropCopies = new ArrayList<>();
            java.util.Iterator<String> copyNames = copies.keys();
            while (copyNames.hasNext()) {
                String name = copyNames.next();
                if (name.contains(":" + key + ":")) dropCopies.add(name);
            }
            for (String name : dropCopies) copies.remove(name);
            if (!dropCopies.isEmpty()) {
                copiesCache = copies;
                prefs().edit().putString(COPIES_KEY, copies.toString()).apply();
            }
        }
    }

    // ------------------------------------------------------------ progress of each episode

    private static final String EPISODES_KEY = "episodes";
    private static final int EPISODES_LIMIT = 600;
    private static JSONObject episodesCache;

    private static String episodeKey(long owner, long id, boolean series, int season, int episode) {
        return owner + ":" + key(id, series) + ":" + season + ":" + episode;
    }

    /**
     * How far into one episode (or one film) playback got, so the title screen can draw it under
     * each episode. Kept per account, like the rest of the history.
     */
    public static void episodeProgress(MessageObject message, long id, boolean series, int season, int episode,
                                       long position, long duration) {
        if (message == null || id == 0 || duration <= 0 || position < 0) return;
        long owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
        if (owner == 0) return;
        synchronized (TjWatchHistory.class) {
            JSONObject episodes = loadEpisodes();
            try {
                JSONArray value = new JSONArray();
                value.put(Math.min(position, duration));
                value.put(duration);
                value.put(System.currentTimeMillis());
                episodes.put(episodeKey(owner, id, series, season, episode), value);
                trimEpisodes(episodes);
                saveEpisodes(episodes);
            } catch (Exception e) {
                FileLog.e("Tj episode progress write failed", e);
            }
        }
    }

    /** Where to carry on in one episode: 0 when it was never started or was watched to the end. */
    public static long episodePosition(long id, boolean series, int season, int episode) {
        long best = 0;
        synchronized (TjWatchHistory.class) {
            JSONObject episodes = loadEpisodes();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                long owner = UserConfig.getInstance(account).getClientUserId();
                if (owner == 0) continue;
                JSONArray value = episodes.optJSONArray(episodeKey(owner, id, series, season, episode));
                if (value == null) continue;
                long position = value.optLong(0), duration = value.optLong(1);
                if (duration <= 0 || position >= duration * FINISHED) continue;
                best = Math.max(best, position);
            }
        }
        return best;
    }

    // ------------------------------------------------------------ which copy each episode was

    private static final String COPIES_KEY = "copies";
    private static final int COPIES_LIMIT = 150;
    private static JSONObject copiesCache;

    /**
     * Remembers the file an episode (or a film) was played from, so opening it again from the
     * title screen plays that same file - the one with the progress line under it - instead of
     * asking which copy all over again. Written once when playback starts, not while it runs.
     */
    public static void rememberCopy(MessageObject message, long id, boolean series, int season, int episode) {
        if (message == null || message.messageOwner == null || id == 0) return;
        long owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
        if (owner == 0) return;
        byte[] data = serialize(message.messageOwner);
        if (data == null) return;
        synchronized (TjWatchHistory.class) {
            JSONObject copies = loadCopies();
            try {
                JSONObject value = new JSONObject();
                value.put("a", message.currentAccount);
                value.put("d", Base64.encodeToString(data, Base64.NO_WRAP));
                value.put("t", System.currentTimeMillis());
                copies.put(episodeKey(owner, id, series, season, episode), value);
                if (copies.length() > COPIES_LIMIT) {
                    ArrayList<String> names = new ArrayList<>();
                    java.util.Iterator<String> iterator = copies.keys();
                    while (iterator.hasNext()) names.add(iterator.next());
                    java.util.Collections.sort(names, (a, b) -> Long.compare(
                            copies.optJSONObject(a) == null ? 0 : copies.optJSONObject(a).optLong("t"),
                            copies.optJSONObject(b) == null ? 0 : copies.optJSONObject(b).optLong("t")));
                    for (int i = 0; i < names.size() - COPIES_LIMIT; i++) copies.remove(names.get(i));
                }
                copiesCache = copies;
                prefs().edit().putString(COPIES_KEY, copies.toString()).apply();
            } catch (Exception e) {
                FileLog.e("Tj watch copy write failed", e);
            }
        }
    }

    /** The file this episode was last played from by an account signed in here, or null. */
    public static MessageObject copyFor(long id, boolean series, int season, int episode) {
        JSONObject best = null;
        synchronized (TjWatchHistory.class) {
            JSONObject copies = loadCopies();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                long owner = UserConfig.getInstance(account).getClientUserId();
                if (owner == 0) continue;
                JSONObject value = copies.optJSONObject(episodeKey(owner, id, series, season, episode));
                if (value == null || value.optInt("a", -1) != account) continue;
                if (best == null || value.optLong("t") > best.optLong("t")) best = value;
            }
        }
        if (best == null) return null;
        try {
            TLRPC.Message raw = deserialize(Base64.decode(best.optString("d", ""), Base64.NO_WRAP));
            return raw == null ? null : new MessageObject(best.optInt("a"), raw, false, false);
        } catch (Exception e) {
            FileLog.e("Tj watch copy read failed", e);
            return null;
        }
    }

    private static JSONObject loadCopies() {
        if (copiesCache != null) return copiesCache;
        try {
            copiesCache = new JSONObject(prefs().getString(COPIES_KEY, "{}"));
        } catch (Exception e) {
            copiesCache = new JSONObject();
        }
        return copiesCache;
    }

    /** 0 when never started, 1 when finished; the furthest any signed-in account got. */
    public static float episodeProgress(long id, boolean series, int season, int episode) {
        float best = 0;
        synchronized (TjWatchHistory.class) {
            JSONObject episodes = loadEpisodes();
            for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                long owner = UserConfig.getInstance(account).getClientUserId();
                if (owner == 0) continue;
                JSONArray value = episodes.optJSONArray(episodeKey(owner, id, series, season, episode));
                if (value == null) continue;
                long position = value.optLong(0), duration = value.optLong(1);
                if (duration <= 0) continue;
                float progress = position >= duration * FINISHED ? 1f : position / (float) duration;
                best = Math.max(best, progress);
            }
        }
        return best;
    }

    private static void trimEpisodes(JSONObject episodes) {
        if (episodes.length() <= EPISODES_LIMIT) return;
        ArrayList<String> names = new ArrayList<>();
        java.util.Iterator<String> iterator = episodes.keys();
        while (iterator.hasNext()) names.add(iterator.next());
        java.util.Collections.sort(names, (a, b) -> Long.compare(
                episodes.optJSONArray(a) == null ? 0 : episodes.optJSONArray(a).optLong(2),
                episodes.optJSONArray(b) == null ? 0 : episodes.optJSONArray(b).optLong(2)));
        for (int i = 0; i < names.size() - EPISODES_LIMIT; i++) episodes.remove(names.get(i));
    }

    private static JSONObject loadEpisodes() {
        if (episodesCache != null) return episodesCache;
        try {
            episodesCache = new JSONObject(prefs().getString(EPISODES_KEY, "{}"));
        } catch (Exception e) {
            episodesCache = new JSONObject();
        }
        return episodesCache;
    }

    private static void saveEpisodes(JSONObject episodes) {
        episodesCache = episodes;
        prefs().edit().putString(EPISODES_KEY, episodes.toString()).apply();
    }

    public static void clear() {
        synchronized (TjWatchHistory.class) {
            cache = new ArrayList<>();
            episodesCache = new JSONObject();
            copiesCache = new JSONObject();
            prefs().edit().remove(KEY).remove(EPISODES_KEY).remove(COPIES_KEY).apply();
        }
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
    }

    private static ArrayList<Entry> load() {
        if (cache != null) return cache;
        ArrayList<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs().getString(KEY, "[]"));
            for (int a = 0; a < array.length(); a++) {
                JSONObject object = array.optJSONObject(a);
                if (object == null) continue;
                Entry entry = new Entry();
                entry.key = object.optString("key", "");
                entry.name = object.optString("name", "");
                entry.poster = object.optString("poster", "");
                entry.series = object.optBoolean("series");
                entry.year = object.optInt("year");
                entry.season = object.optInt("season", -1);
                entry.episode = object.optInt("episode", -1);
                entry.account = object.optInt("account");
                entry.messageId = object.optInt("mid");
                entry.owner = object.optLong("owner");
                entry.documentId = object.optLong("document");
                entry.position = object.optLong("position");
                entry.duration = object.optLong("duration");
                entry.updatedAt = object.optLong("at");
                String data = object.optString("data", "");
                if (!data.isEmpty()) entry.data = Base64.decode(data, Base64.NO_WRAP);
                if (entry.key.isEmpty() || entry.data == null
                        || entry.account < 0 || entry.account >= UserConfig.MAX_ACCOUNT_COUNT) continue;
                if (!belongsToSignedInAccount(entry.owner)) continue;
                entries.add(entry);
            }
        } catch (Exception e) { FileLog.e("Tj watch history read failed", e); }
        cache = entries;
        return entries;
    }

    /**
     * True while the account that wrote this row is still signed in here. Signing out takes its
     * history with it - what someone watched is theirs, and the next person to use this slot must
     * not inherit it. Before any account has loaded nothing is dropped, so a read during start-up
     * cannot mistake "not ready yet" for "signed out".
     */
    private static boolean belongsToSignedInAccount(long owner) {
        boolean any = false;
        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            long id = UserConfig.getInstance(account).getClientUserId();
            if (id == 0) continue;
            any = true;
            if (id == owner) return true;
        }
        return !any;
    }

    private static void save(ArrayList<Entry> entries) {
        cache = entries;
        try {
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("key", entry.key);
                object.put("name", entry.name);
                object.put("poster", entry.poster);
                object.put("series", entry.series);
                object.put("year", entry.year);
                object.put("season", entry.season);
                object.put("episode", entry.episode);
                object.put("account", entry.account);
                object.put("mid", entry.messageId);
                object.put("owner", entry.owner);
                object.put("document", entry.documentId);
                object.put("position", entry.position);
                object.put("duration", entry.duration);
                object.put("at", entry.updatedAt);
                if (entry.data != null) object.put("data", Base64.encodeToString(entry.data, Base64.NO_WRAP));
                array.put(object);
            }
            prefs().edit().putString(KEY, array.toString()).apply();
        } catch (Exception e) { FileLog.e("Tj watch history write failed", e); }
    }

    /** Builds a row for a copy that is about to be played. */
    public static Entry entryFor(long id, boolean series, String name, String poster, int year,
                                int season, int episode, MessageObject message) {
        if (message == null || message.getDocument() == null) return null;
        Entry entry = new Entry();
        entry.key = key(id, series);
        entry.name = name == null ? "" : name;
        entry.poster = poster == null ? "" : poster;
        entry.series = series;
        entry.year = year;
        entry.season = season;
        entry.episode = episode;
        entry.account = message.currentAccount;
        entry.owner = UserConfig.getInstance(message.currentAccount).getClientUserId();
        entry.messageId = message.getId();
        entry.documentId = message.getDocument().id;
        entry.data = serialize(message.messageOwner);
        entry.rebuilt = message;
        return entry.data == null ? null : entry;
    }

    private static byte[] serialize(TLRPC.Message message) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(buffer);
            int length = buffer.position();
            buffer.position(0);
            return buffer.readData(length, true);
        } catch (Exception e) { FileLog.e("Tj watch history serialization failed", e); return null; }
        finally { if (buffer != null) buffer.reuse(); }
    }

    private static TLRPC.Message deserialize(byte[] data) {
        NativeByteBuffer buffer = null;
        try {
            buffer = new NativeByteBuffer(data.length);
            buffer.writeBytes(data);
            buffer.position(0);
            return TLRPC.Message.TLdeserialize(buffer, buffer.readInt32(false), false);
        } catch (Exception e) { FileLog.e("Tj watch history read failed", e); return null; }
        finally { if (buffer != null) buffer.reuse(); }
    }
}
