package com.example.quickfix.api;

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
            retrofit = new Retrofit.Builder()
                    .baseUrl(ensureSlash(BuildConfig.SUPABASE_URL))
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

