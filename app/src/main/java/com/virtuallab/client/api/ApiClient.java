package com.virtuallab.client.api;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.virtuallab.client.Config;
import com.virtuallab.client.BuildConfig;
import com.virtuallab.client.data.SessionStore;
import com.virtuallab.client.security.SecurityPolicy;
import com.virtuallab.client.ui.LoginActivity;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final AtomicBoolean remoteLogoutHandled = new AtomicBoolean(false);

    private ApiClient() {}

    public static ApiService get() {
        if (service == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);

            Interceptor auth = chain -> {
                Request req = chain.request();
                String token = SessionStore.getAuthToken();
                if (token != null && !token.isEmpty() && !isLoginEndpoint(req)) {
                    req = req.newBuilder()
                            .addHeader("Authorization", "Bearer " + token)
                            .header("Cache-Control", "no-cache")
                            .build();
                }
                Response response = chain.proceed(req);
                if (isSessionReplaced(response)) {
                    handleRemoteLogout();
                }
                return response;
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
                    .addInterceptor(SecurityPolicy.trustedEndpointInterceptor())
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

    private static boolean isSessionReplaced(Response response) {
        if (response == null || response.code() != 409) {
            return false;
        }
        try {
            String body = response.peekBody(2048).string();
            return body.contains("session_replaced")
                    || body.toLowerCase(java.util.Locale.US).contains("another device");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isLoginEndpoint(Request request) {
        String path = request.url().encodedPath();
        return path.endsWith("/login.php")
                || path.endsWith("/google_login.php")
                || path.endsWith("/link_login.php");
    }

    private static void handleRemoteLogout() {
        Context app = SessionStore.app();
        if (app == null || !remoteLogoutHandled.compareAndSet(false, true)) {
            return;
        }
        SessionStore.clear();
        new Handler(Looper.getMainLooper()).post(() -> {
            Toast.makeText(app, "Logged out because this account signed in on another device.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(app, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            app.startActivity(intent);
            remoteLogoutHandled.set(false);
        });
    }
}
