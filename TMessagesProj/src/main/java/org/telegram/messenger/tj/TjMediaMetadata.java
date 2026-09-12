package org.telegram.messenger.tj;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.UserConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Explicit cancellable TMDB lookup. Never sends Telegram/account data. */
public final class TjMediaMetadata {
    public static final int OK = 0, CREDENTIAL = 1, NETWORK = 2, RATE_LIMIT = 3, INVALID = 4;
    public interface Callback { void complete(ArrayList<Title> titles, int error); }
    public static final class Title {
        public final long id;
        public final boolean series;
        public final String name, originalName, overview, poster, backdrop, date, json;
        public final double rating;
        public final boolean searchComplete;
        public Title(JSONObject object, boolean series) {
            this(object, series, true);
        }
        private Title(JSONObject object, boolean series, boolean searchComplete) {
            this.searchComplete = searchComplete;
            id = object.optLong("id");
            this.series = series;
            name = object.optString(series ? "name" : "title", "");
            originalName = object.optString(series ? "original_name" : "original_title", "");
            overview = object.optString("overview", "");
            poster = imagePath(object.optString("poster_path", ""));
            backdrop = imagePath(object.optString("backdrop_path", ""));
            date = object.optString(series ? "first_air_date" : "release_date", "");
            rating = object.optDouble("vote_average", 0);
            json = object.toString();
        }
        public String posterUrl() { return poster.isEmpty() ? "" : "https://image.tmdb.org/t/p/w500" + poster; }
        public String backdropUrl() { return backdrop.isEmpty() ? posterUrl() : "https://image.tmdb.org/t/p/w780" + backdrop; }
        private static String imagePath(String value) {
            return value.matches("/[A-Za-z0-9_]+\\.(jpg|png|webp)") ? value : "";
        }
    }
    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build();
    private static final DispatchQueue queue = new DispatchQueue("tj-metadata");
    private final OkHttpClient httpClient;
    private volatile Call active;
    private volatile int generation;

    public TjMediaMetadata() { this(client); }
    TjMediaMetadata(OkHttpClient httpClient) { this.httpClient = httpClient; }

    public void search(int account, String query, boolean series, String language, Callback callback) {
        cancel();
        int epoch = generation;
        long owner = UserConfig.getInstance(account).getClientUserId();
        String cleanQuery = query == null ? "" : query.trim();
        queue.postRunnable(() -> {
            ArrayList<Title> result = new ArrayList<>();
            int error = OK;
            if (epoch != generation || !UserConfig.getInstance(account).isClientActivated()
                    || UserConfig.getInstance(account).getClientUserId() != owner) return;
            String credential = TjConfig.mediaMetadataCredential(account, owner);
            if (credential.isEmpty()) error = CREDENTIAL;
            else if (cleanQuery.isEmpty() || cleanQuery.length() > 250) error = INVALID;
            else {
                HttpUrl.Builder url = HttpUrl.parse("https://api.themoviedb.org/3/search/" + (series ? "tv" : "movie")).newBuilder()
                        .addQueryParameter("query", cleanQuery).addQueryParameter("include_adult", "false")
                        .addQueryParameter("language", language == null || language.isEmpty() ? "en-US" : language);
                boolean apiKey = credential.matches("[a-fA-F0-9]{32}");
                if (apiKey) url.addQueryParameter("api_key", credential);
                try {
                    Request.Builder builder = new Request.Builder().url(url.build()).header("Accept", "application/json");
                    if (!apiKey) builder.header("Authorization", "Bearer " + credential);
                    Call call = httpClient.newCall(builder.build());
                    active = call;
                    if (epoch != generation || !UserConfig.getInstance(account).isClientActivated()
                            || UserConfig.getInstance(account).getClientUserId() != owner) { call.cancel(); return; }
                    try (Response response = call.execute()) {
                        if (response.code() == 401 || response.code() == 403) error = CREDENTIAL;
                        else if (response.code() == 429) error = RATE_LIMIT;
                        else if (!response.isSuccessful() || response.body() == null) error = NETWORK;
                        else {
                            ByteArrayOutputStream output = new ByteArrayOutputStream();
                            try (InputStream input = response.body().byteStream()) {
                                byte[] buffer = new byte[8192];
                                int read;
                                while ((read = input.read(buffer)) != -1) {
                                    if (output.size() + read > 2 * 1024 * 1024) throw new java.io.IOException("Response too large");
                                    output.write(buffer, 0, read);
                                }
                            }
                            JSONObject body = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
                            JSONArray titles = body.optJSONArray("results");
                            if (titles == null) throw new org.json.JSONException("Missing search results");
                            boolean exhaustive = body.optInt("total_pages", 0) == 1;
                            for (int i = 0; i < titles.length(); i++) {
                                Title title = new Title(titles.getJSONObject(i), series, exhaustive);
                                if (title.id > 0 && !title.name.isEmpty()) result.add(title);
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // URLs/exceptions may contain API keys. Intentionally do not log them.
                    error = NETWORK;
                } finally { active = null; }
            }
            int finalError = error;
            AndroidUtilities.runOnUIThread(() -> {
                if (epoch == generation && UserConfig.getInstance(account).isClientActivated()
                        && UserConfig.getInstance(account).getClientUserId() == owner) callback.complete(result, finalError);
            });
        });
    }
    public void cancel() {
        generation++;
        Call call = active;
        if (call != null) call.cancel();
    }
}
