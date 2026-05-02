package com.virtuallab.client.api;

import com.virtuallab.client.Config;
import com.virtuallab.client.data.SessionStore;

import java.io.File;
import java.util.concurrent.TimeUnit;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public final class ApiClient {
    private static ApiService service;

    private ApiClient() {}

    public static ApiService get() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BASIC);

            Interceptor auth = chain -> {
                Request req = chain.request();
                String token = SessionStore.getAuthToken();
                if (token != null && !token.isEmpty()) {
                    req = req.newBuilder().addHeader("Authorization", "Bearer " + token).build();
                }
                return chain.proceed(req);
            };

            Interceptor fastCache = chain -> {
                Request request = chain.request();
                Response response = chain.proceed(request);
                if (!"GET".equalsIgnoreCase(request.method())) {
                    return response;
                }
                // Respect server TTL when provided; otherwise allow short client cache.
                if (response.header("Cache-Control") != null) {
                    return response;
                }
                return response.newBuilder()
                        .header("Cache-Control", "public, max-age=60")
                        .build();
            };

            Cache cache = null;
            if (SessionStore.app() != null) {
                File cacheDir = new File(SessionStore.app().getCacheDir(), "http_cache");
                cache = new Cache(cacheDir, 20L * 1024L * 1024L); // 20 MB
            }

            OkHttpClient.Builder builder = new OkHttpClient.Builder()
                    .addInterceptor(auth)
                    .addNetworkInterceptor(fastCache)
                    .addInterceptor(logging)
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS);

            if (cache != null) {
                builder.cache(cache);
            }

            OkHttpClient client = builder.build();

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(Config.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();

            service = retrofit.create(ApiService.class);
        }
        return service;
    }
}
