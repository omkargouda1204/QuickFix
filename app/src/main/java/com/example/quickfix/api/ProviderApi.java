package com.example.quickfix.api;

import com.example.quickfix.api.model.Provider;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/**
 * Supabase providers API used for dashboard counts.
 */
public interface ProviderApi {

    @GET("rest/v1/providers")
    Call<List<Provider>> getProviders(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Prefer") String prefer,
            @Query("select") String select,
            // column name is `is_approved` in the DB schema
            @Query("is_approved") String isApproved,
            @Query("limit") Integer limit
    );
}

