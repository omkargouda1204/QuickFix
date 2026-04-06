package com.example.quickfix.api;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * Retrofit interface for Supabase Storage uploads.
 */
public interface SupabaseStorageApi {

    @POST("storage/v1/object/{bucket}/{path}")
    Call<ResponseBody> uploadObject(
            @Path("bucket") String bucket,
            @Path(value = "path", encoded = true) String path,
            @Body RequestBody body,
            @Header("apikey") String apiKey,
            @Header("Authorization") String authorization,
            @Header("Content-Type") String contentType,
            @Header("x-upsert") String upsert
    );
}

