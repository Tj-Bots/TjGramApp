package org.telegram.messenger.tj;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.util.ArrayList;

/**
 * The films and series marked with the heart on their page in Watch. Kept on this device only,
 * newest first, with just enough of the catalogue entry - name, poster, year, rating - to draw
 * them in a row on the Watch home screen without asking the catalogue again.
 */
public final class TjWatchFavorites {

    private static final String KEY = "list";

    private TjWatchFavorites() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("tjwatchfavorites", Context.MODE_PRIVATE);
    }

    private static JSONArray load() {
        try {
            return new JSONArray(prefs().getString(KEY, "[]"));
        } catch (Exception e) {
            FileLog.e(e);
            return new JSONArray();
        }
    }

    private static boolean same(JSONObject entry, long id, boolean series) {
        return entry != null && entry.optLong("id") == id && entry.optBoolean("series") == series;
    }

    public static boolean contains(long id, boolean series) {
        JSONArray list = load();
        for (int i = 0; i < list.length(); i++) {
            if (same(list.optJSONObject(i), id, series)) return true;
        }
        return false;
    }

    /**
     * Marks or unmarks a title; returns whether it is a favourite now. The entry is written in the
     * catalogue's own shape, so the Watch screen reads it back like any other catalogue item.
     */
    public static boolean toggle(long id, boolean series, String name, String poster, String date, double rating) {
        JSONArray list = load();
        JSONArray next = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (same(entry, id, series)) {
                removed = true;
            } else if (entry != null) {
                next.put(entry);
            }
        }
        if (!removed) {
            try {
                JSONObject entry = new JSONObject();
                entry.put("id", id);
                entry.put("series", series);
                entry.put(series ? "name" : "title", name == null ? "" : name);
                entry.put("poster_path", poster == null ? "" : poster);
                entry.put(series ? "first_air_date" : "release_date", date == null ? "" : date);
                entry.put("vote_average", rating);
                JSONArray withNew = new JSONArray();
                withNew.put(entry);
                for (int i = 0; i < next.length(); i++) withNew.put(next.get(i));
                next = withNew;
            } catch (Exception e) {
                FileLog.e(e);
                return false;
            }
        }
        prefs().edit().putString(KEY, next.toString()).apply();
        return !removed;
    }

    /** Fills in what was not known yet when the heart was pressed, keeping the entry in its place. */
    public static void refresh(long id, boolean series, String poster, String date, double rating) {
        JSONArray list = load();
        boolean changed = false;
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (!same(entry, id, series)) continue;
            try {
                String dateKey = series ? "first_air_date" : "release_date";
                if (poster != null && !poster.isEmpty() && !poster.equals(entry.optString("poster_path"))) {
                    entry.put("poster_path", poster);
                    changed = true;
                }
                if (date != null && !date.isEmpty() && !date.equals(entry.optString(dateKey))) {
                    entry.put(dateKey, date);
                    changed = true;
                }
                if (rating > 0 && rating != entry.optDouble("vote_average", 0)) {
                    entry.put("vote_average", rating);
                    changed = true;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (changed) prefs().edit().putString(KEY, list.toString()).apply();
    }

    /** Newest first. */
    public static ArrayList<JSONObject> all() {
        ArrayList<JSONObject> result = new ArrayList<>();
        JSONArray list = load();
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (entry != null && entry.optLong("id") > 0) result.add(entry);
        }
        return result;
    }
}
