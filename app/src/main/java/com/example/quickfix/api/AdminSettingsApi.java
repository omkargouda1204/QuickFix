package com.example.quickfix.api;

import com.example.quickfix.api.model.AdminSettingsDto;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.Query;

/**
 * Supabase admin_settings Retrofit API.
 */
public interface AdminSettingsApi {

    @GET("rest/v1/admin_settings")
    Call<List<AdminSettingsDto>> fetchSingleRow(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Accept") String accept,
            @Query("select") String select,
            @Query("limit") int limit
    );

    @PATCH("rest/v1/admin_settings")
    Call<List<AdminSettingsDto>> updateById(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("id") String eqId,
            @Body Map<String, Object> body
    );
}

