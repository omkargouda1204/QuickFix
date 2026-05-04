package com.example.quickfix.api;

import android.util.Log;

import com.example.quickfix.BuildConfig;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Central Retrofit provider.
 */
public final class RetrofitProvider {

    private static Retrofit retrofit;

    private RetrofitProvider() {}

    public static synchronized Retrofit get() {
        if (retrofit == null) {
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                .addInterceptor(new okhttp3.Interceptor() {
                    @Override
                    public okhttp3.Response intercept(Chain chain) throws java.io.IOException {
                        okhttp3.Request original = chain.request();
                        String token = com.example.quickfix.AuthTokenProvider.getIdTokenBlocking(5);
                        String accessToken = (token != null && !token.isEmpty()) ? token : BuildConfig.SUPABASE_ANON_KEY;
                        Log.d("TOKEN", accessToken);
                        Log.d("API_URL", original.url().toString());
                        okhttp3.Request request = original.newBuilder()
                                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                                .header("Authorization", "Bearer " + accessToken)
                                .header("Content-Type", "application/json")
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(ensureSlash(BuildConfig.SUPABASE_URL))
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }

    private static String ensureSlash(String url) {
        if (url == null || url.isEmpty()) return "https://example.supabase.co/";
        return url.endsWith("/") ? url : (url + "/");
    }
}

