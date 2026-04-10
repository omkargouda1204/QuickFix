package com.example.quickfix.api;

import com.example.quickfix.api.model.Provider;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface ProviderSearchApi {

    @GET("rest/v1/provider_applications")
    Call<List<Provider>> searchProviders(
            @Header("apikey") String key,
            @Header("Authorization") String auth,
            @Query("status") String status,
            @Query("or") String query
    );
}

