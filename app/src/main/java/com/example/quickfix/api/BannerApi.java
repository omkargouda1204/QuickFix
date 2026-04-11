package com.example.quickfix.api;

import com.example.quickfix.Banner;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface BannerApi {

    @GET("rest/v1/banners")
    Call<List<Banner>> getActiveBanners(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("is_active") String active,
            @Query("order") String order
    );

    @GET("rest/v1/banners?select=*&order=created_at.desc")
    Call<List<Banner>> getBanners();


    @GET("rest/v1/banners")
    Call<List<Banner>> getAllBanners(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("select") String select,
            @Query("order") String order
    );

    @POST("rest/v1/banners")
    Call<Void> createBanner(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Body Map<String, Object> body
    );

    @PATCH("rest/v1/banners")
    Call<Void> updateBanner(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("id") String idEq,
            @Body Map<String, Object> body
    );

    @DELETE("rest/v1/banners")
    Call<Void> deleteBanner(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("id") String idEq
    );
}
