package org.telegram.messenger.tj;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads the catalogue of everything that exists, which is a different question from what happens
 * to be lying in a chat. Browsing starts here - a name, the titles that answer to it, the seasons
 * and episodes a series is known to have - and only the last step, actually watching, goes looking
 * for a file.
 *
 * Nothing from the account travels with a request: the credential is the user's own, and the only
 * thing sent is what they typed.
 */
public final class TjTmdb {

    public static final int OK = 0, CREDENTIAL = 1, NETWORK = 2, RATE_LIMIT = 3, INVALID = 4;

    public interface Callback {
        void complete(JSONObject body, int error);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).build();
    private static final DispatchQueue queue = new DispatchQueue("tj-tmdb");

    private volatile Call active;
    private volatile int generation;

    /** The language TMDB should answer in, taken from the language the app is being read in. */
    public static String language() {
        String code = LocaleController.getInstance().getCurrentLocaleInfo() == null ? null
                : LocaleController.getInstance().getCurrentLocaleInfo().shortName;
        if (code == null || code.isEmpty()) return "en-US";
        if (code.equals("he") || code.equals("iw")) return "he-IL";
        if (code.equals("ar")) return "ar-SA";
        if (code.equals("ru")) return "ru-RU";
        if (code.equals("en")) return "en-US";
        return code;
    }

    /**
     * The key the app is built with, used by anyone who has not added one of their own. It is
     * shared, so it is also readable by anyone holding the apk and its budget is shared by every
     * install; a personal key entered in the Watch settings always wins over it.
     */
    private static String sharedCredential() {
        String key = BuildConfig.TJ_TMDB_KEY;
        return key == null ? "" : key.trim();
    }

    /** True when there is a key to ask with at all, the user's own or the shared one. */
    public static boolean available(int account) {
        return TjConfig.hasMediaMetadataCredential(account) || !sharedCredential().isEmpty();
    }

    /** True when this account entered a key of its own. */
    public static boolean hasOwnCredential(int account) {
        return TjConfig.hasMediaMetadataCredential(account);
    }

    public static String imageUrl(String path, String size) {
        if (path == null || !path.matches("/[A-Za-z0-9_]+\\.(jpg|png|webp)")) return "";
        return "https://image.tmdb.org/t/p/" + size + path;
    }

    public static String posterUrl(String path) { return imageUrl(path, "w500"); }
    public static String backdropUrl(String path) { return imageUrl(path, "w780"); }
    public static String stillUrl(String path) { return imageUrl(path, "w300"); }

    public void search(int account, String query, boolean series, Callback callback) {
        search(account, query, series, 1, callback);
    }

    public void search(int account, String query, boolean series, int page, Callback callback) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("query", query == null ? "" : query.trim());
        params.put("include_adult", "false");
        if (page > 1) params.put("page", Integer.toString(page));
        request(account, "search/" + (series ? "tv" : "movie"), params, callback);
    }

    public void details(int account, long id, boolean series, Callback callback) {
        request(account, (series ? "tv/" : "movie/") + id, new LinkedHashMap<>(), callback);
    }

    public void season(int account, long seriesId, int season, Callback callback) {
        request(account, "tv/" + seriesId + "/season/" + season, new LinkedHashMap<>(), callback);
    }

    /** What TMDB suggests to people who liked this title. */
    public void recommendations(int account, long id, boolean series, Callback callback) {
        request(account, (series ? "tv/" : "movie/") + id + "/recommendations", new LinkedHashMap<>(), callback);
    }

    public void trending(int account, boolean series, Callback callback) {
        request(account, "trending/" + (series ? "tv" : "movie") + "/week", new LinkedHashMap<>(), callback);
    }

    /** The names the catalogue sorts by. Films and series keep separate lists of them. */
    public void genres(int account, boolean series, Callback callback) {
        request(account, "genre/" + (series ? "tv" : "movie") + "/list", new LinkedHashMap<>(), callback);
    }

    /** Everything filed under one of those names, best known first. */
    public void discover(int account, boolean series, int genre, Callback callback) {
        discover(account, series, genre, 1, callback);
    }

    public void discover(int account, boolean series, int genre, int page, Callback callback) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("with_genres", Integer.toString(genre));
        params.put("sort_by", "popularity.desc");
        params.put("include_adult", "false");
        params.put("vote_count.gte", "40");
        if (page > 1) params.put("page", Integer.toString(page));
        request(account, "discover/" + (series ? "tv" : "movie"), params, callback);
    }

    public interface Verification {
        void complete(int error);
    }

    /**
     * Tries a credential before anything is stored under it, so a half-copied key is caught where
     * it was typed rather than as an empty screen later. Asks for the one thing TMDB will answer
     * about itself, so nothing is searched for and nothing is sent but the credential.
     */
    public static void verify(String credential, Verification callback) {
        final String value = credential == null ? "" : credential.trim();
        queue.postRunnable(() -> {
            int error = OK;
            if (value.isEmpty()) {
                error = CREDENTIAL;
            } else {
                boolean apiKey = value.matches("[a-fA-F0-9]{32}");
                HttpUrl.Builder url = HttpUrl.parse("https://api.themoviedb.org/3/configuration").newBuilder();
                if (apiKey) url.addQueryParameter("api_key", value);
                try {
                    Request.Builder builder = new Request.Builder().url(url.build()).header("Accept", "application/json");
                    if (!apiKey) builder.header("Authorization", "Bearer " + value);
                    try (Response response = client.newCall(builder.build()).execute()) {
                        if (response.code() == 401 || response.code() == 403) error = CREDENTIAL;
                        else if (response.code() == 429) error = RATE_LIMIT;
                        else if (!response.isSuccessful()) error = NETWORK;
                    }
                } catch (Exception ignored) {
                    // A URL carries the credential. Never log one.
                    error = NETWORK;
                }
            }
            final int result = error;
            AndroidUtilities.runOnUIThread(() -> callback.complete(result));
        });
    }

    public void cancel() {
        generation++;
        Call call = active;
        if (call != null) call.cancel();
    }

    private void request(int account, String path, Map<String, String> params, Callback callback) {
        cancel();
        final int epoch = generation;
        final long owner = UserConfig.getInstance(account).getClientUserId();
        queue.postRunnable(() -> {
            JSONObject result = null;
            int error = OK;
            if (epoch != generation || !owned(account, owner)) return;
            String credential = TjConfig.mediaMetadataCredential(account, owner);
            if (credential.isEmpty()) credential = sharedCredential();
            if (credential.isEmpty()) {
                error = CREDENTIAL;
            } else if (params.containsKey("query") && params.get("query").isEmpty()) {
                error = INVALID;
            } else {
                HttpUrl.Builder url = HttpUrl.parse("https://api.themoviedb.org/3/" + path).newBuilder()
                        .addQueryParameter("language", language());
                for (Map.Entry<String, String> param : params.entrySet()) {
                    url.addQueryParameter(param.getKey(), param.getValue());
                }
                boolean apiKey = credential.matches("[a-fA-F0-9]{32}");
                if (apiKey) url.addQueryParameter("api_key", credential);
                try {
                    Request.Builder builder = new Request.Builder().url(url.build()).header("Accept", "application/json");
                    if (!apiKey) builder.header("Authorization", "Bearer " + credential);
                    Call call = client.newCall(builder.build());
                    active = call;
                    if (epoch != generation || !owned(account, owner)) { call.cancel(); return; }
                    try (Response response = call.execute()) {
                        if (response.code() == 401 || response.code() == 403) error = CREDENTIAL;
                        else if (response.code() == 429) error = RATE_LIMIT;
                        else if (!response.isSuccessful() || response.body() == null) error = NETWORK;
                        else result = new JSONObject(read(response));
                    }
                } catch (Exception ignored) {
                    // A URL carries the credential. Never log one.
                    error = NETWORK;
                } finally {
                    active = null;
                }
            }
            final JSONObject body = result;
            final int finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (epoch == generation && owned(account, owner)) callback.complete(body, finalError);
            });
        });
    }

    private static boolean owned(int account, long owner) {
        return UserConfig.getInstance(account).isClientActivated()
                && UserConfig.getInstance(account).getClientUserId() == owner;
    }

    private static String read(Response response) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (InputStream input = response.body().byteStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > 2 * 1024 * 1024) throw new java.io.IOException("Response too large");
                output.write(buffer, 0, count);
            }
        }
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
}
