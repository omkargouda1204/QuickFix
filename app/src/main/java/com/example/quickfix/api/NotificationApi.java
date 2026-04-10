package com.example.quickfix.api;

import com.example.quickfix.Notification;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface NotificationApi {

    @GET("rest/v1/notifications?order=created_at.desc")
    Call<List<Notification>> getNotifications(
            @Header("apikey") String key,
            @Header("Authorization") String auth,
            @Query("user_id") String userId
    );
}

