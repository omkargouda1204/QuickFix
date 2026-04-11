package com.example.quickfix.api;

import com.example.quickfix.Offer;

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

public interface OfferApi {

    @GET("rest/v1/offers")
    Call<List<Offer>> getActiveOffers(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("is_active") String active,
            @Query("order") String order
    );


    @GET("rest/v1/offers")
    Call<List<Offer>> getAllOffers(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("select") String select,
            @Query("order") String order
    );

    @GET("rest/v1/offers?select=*&order=created_at.desc")
    Call<List<Offer>> getOffers();

    @POST("rest/v1/offers")
    Call<Void> createOffer(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Body Map<String, Object> body
    );

    @PATCH("rest/v1/offers")
    Call<Void> updateOffer(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Header("Content-Type") String contentType,
            @Header("Prefer") String prefer,
            @Query("id") String idEq,
            @Body Map<String, Object> body
    );

    @DELETE("rest/v1/offers")
    Call<Void> deleteOffer(
            @Header("apikey") String apiKey,
            @Header("Authorization") String bearer,
            @Query("id") String idEq
    );
}
