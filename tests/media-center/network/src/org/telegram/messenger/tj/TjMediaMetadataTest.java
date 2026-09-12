package org.telegram.messenger.tj;

import org.telegram.messenger.UserConfig;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.MediaType;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs production request/parsing/cancellation code against in-process responses. */
public final class TjMediaMetadataTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("Metadata check " + checks); }
    private static void await(CountDownLatch latch) throws Exception { check(latch.await(5, TimeUnit.SECONDS)); }
    private static final String GOOD = "{\"total_pages\":1,\"results\":[{\"id\":7,\"title\":\"Example\",\"original_title\":\"Original\",\"poster_path\":\"/poster.jpg\",\"release_date\":\"2024-01-01\"}]}";
    private static TjMediaMetadata service(int status, String body) {
        return new TjMediaMetadata(new OkHttpClient.Builder().addInterceptor(chain -> new Response.Builder()
                .request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body(ResponseBody.create(body, MediaType.parse("application/json"))).build()).build());
    }
    private static final class Result {
        int error;
        ArrayList<TjMediaMetadata.Title> titles;
    }
    private static Result search(TjMediaMetadata client, String query) throws Exception {
        Result result = new Result();
        CountDownLatch done = new CountDownLatch(1);
        client.search(0, query, false, "he", (titles, error) -> {
            result.titles = titles; result.error = error; done.countDown();
        });
        await(done);
        return result;
    }
    public static void main(String[] args) throws Exception {
        Result result = search(service(200, GOOD), "Example");
        check(result.error == TjMediaMetadata.OK && result.titles.size() == 1);
        check(result.titles.get(0).originalName.equals("Original"));
        check(result.titles.get(0).posterUrl().equals("https://image.tmdb.org/t/p/w500/poster.jpg"));
        check(result.titles.get(0).searchComplete);
        check(!search(service(200, GOOD.replace("total_pages\":1", "total_pages\":2")), "Example").titles.get(0).searchComplete);
        check(search(service(401, "{}"), "Example").error == TjMediaMetadata.CREDENTIAL);
        check(search(service(403, "{}"), "Example").error == TjMediaMetadata.CREDENTIAL);
        check(search(service(429, "{}"), "Example").error == TjMediaMetadata.RATE_LIMIT);
        check(search(service(503, "{}"), "Example").error == TjMediaMetadata.NETWORK);
        check(search(service(302, "{}"), "Example").error == TjMediaMetadata.NETWORK);
        check(search(service(200, "broken"), "Example").error == TjMediaMetadata.NETWORK);
        check(search(service(200, "{\"other\":[]}"), "Example").error == TjMediaMetadata.NETWORK);
        check(search(service(200, "x".repeat(2 * 1024 * 1024 + 1)), "Example").error == TjMediaMetadata.NETWORK);
        check(search(service(200, GOOD), "").error == TjMediaMetadata.INVALID);
        TjConfig.credential = "";
        check(search(service(200, GOOD), "Example").error == TjMediaMetadata.CREDENTIAL);
        TjConfig.credential = "test-only-bearer";
        TjMediaMetadata headers = new TjMediaMetadata(new OkHttpClient.Builder().addInterceptor(chain -> {
            check("Bearer test-only-bearer".equals(chain.request().header("Authorization")));
            check(chain.request().url().queryParameter("api_key") == null);
            check(chain.request().url().host().equals("api.themoviedb.org"));
            check(chain.request().url().queryParameter("query").equals("שם הסרט"));
            check(chain.request().url().queryParameterNames().size() == 3);
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("fixture").body(ResponseBody.create(GOOD, MediaType.parse("application/json"))).build();
        }).build());
        check(search(headers, "שם הסרט").error == TjMediaMetadata.OK);
        check(search(service(200, GOOD.replace("/poster.jpg", "https://invalid.example/p.jpg")), "Example").titles.get(0).posterUrl().isEmpty());
        java.lang.reflect.Field transport = TjMediaMetadata.class.getDeclaredField("httpClient");
        transport.setAccessible(true);
        OkHttpClient production = (OkHttpClient) transport.get(new TjMediaMetadata());
        check(!production.followRedirects() && !production.followSslRedirects());
        check(!production.retryOnConnectionFailure());
        check(production.callTimeoutMillis() == 30000);
        staleResponse(false);
        staleResponse(true);
        System.out.println(checks + " production metadata request/response checks passed; no live requests");
    }

    private static void staleResponse(boolean changeOwner) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        TjMediaMetadata slow = new TjMediaMetadata(new OkHttpClient.Builder().addInterceptor(chain -> {
            entered.countDown();
            try { if (!release.await(5, TimeUnit.SECONDS)) throw new java.io.IOException("Fixture timeout"); }
            catch (InterruptedException e) { throw new java.io.IOException(e); }
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200)
                    .message("fixture").body(ResponseBody.create(GOOD, MediaType.parse("application/json"))).build();
        }).build());
        slow.search(0, "Example", false, "en", (titles, error) -> delivered.incrementAndGet());
        await(entered);
        if (changeOwner) UserConfig.getInstance(0).owner = 20; else slow.cancel();
        release.countDown();
        search(service(200, GOOD), "Barrier"); // Same production worker queue drains the old request first.
        check(delivered.get() == 0);
        UserConfig.getInstance(0).owner = 10;
    }
}
