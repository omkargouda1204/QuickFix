package com.example.quickfix.api;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.PATCH;
import retrofit2.http.Query;

public interface BookingApi {

    @Headers("Prefer: return=minimal")
    @PATCH("rest/v1/bookings")
    Call<Void> updateBookingStatus(
            @Header("apikey") String key,
            @Header("Authorization") String auth,
            @Query("id") String bookingId,
            @Body Map<String, Object> body
    );
}

