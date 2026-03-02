package com.example.quickfix;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseStorageHelper — Uploads images to Supabase Storage via the REST API.
 *
 * Buckets used:
 *   • provider-business-images  (business_image_url)
 *   • provider-id-proofs        (id_proof_url)
 *
 * Both buckets must exist in Supabase Storage and be configured as PUBLIC or have
 * the appropriate RLS policies for INSERT from anon key.
 *
 * CREATE STORAGE BUCKET: provider-business-images  (public: true)
 * CREATE STORAGE BUCKET: provider-id-proofs        (public: false, admin-readable)
 */
public class SupabaseStorageHelper {

    private static final String TAG = "SupabaseStorage";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60,    TimeUnit.SECONDS)
            .writeTimeout(60,   TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String publicUrl);
        void onError(String message);
    }

    // ── Bucket names ───────────────────────────────────────────────────────
    public static final String BUCKET_BUSINESS_IMAGES = "provider-business-images";
    public static final String BUCKET_ID_PROOFS       = "provider-id-proofs";

    /**
     * Upload a file from a Uri to a Supabase Storage bucket.
     *
     * @param context    Application context
     * @param fileUri    Content URI of the file to upload
     * @param bucket     Target bucket name
     * @param prefix     Path prefix, e.g. firebase_uid
     * @param callback   Called with public URL on success
     */
    public static void uploadFile(Context context, Uri fileUri, String bucket,
                                  String prefix, UploadCallback callback) {
        new Thread(() -> {
            try {
                // Determine MIME type
                String mimeType = context.getContentResolver().getType(fileUri);
                if (mimeType == null) mimeType = "application/octet-stream";

                // Generate unique file name
                String ext = mimeTypeToExtension(mimeType);
                String fileName = prefix + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;
                String storagePath = prefix + "/" + fileName;

                // Read file bytes
                byte[] fileBytes;
                try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
                    if (is == null) {
                        MAIN.post(() -> callback.onError("Cannot read file"));
                        return;
                    }
                    fileBytes = is.readAllBytes();
                }

                // Upload to Supabase Storage
                String uploadUrl = SUPABASE_URL + "/storage/v1/object/" + bucket + "/" + storagePath;

                Request request = new Request.Builder()
                        .url(uploadUrl)
                        .post(RequestBody.create(fileBytes, MediaType.parse(mimeType)))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  mimeType)
                        .addHeader("x-upsert",      "true")
                        .build();

                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    String body = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "uploadFile HTTP " + code + " | " + body);

                    if (code == 200 || code == 200) {
                        // Build public URL
                        String publicUrl = SUPABASE_URL + "/storage/v1/object/public/"
                                + bucket + "/" + storagePath;
                        Log.d(TAG, "Upload success: " + publicUrl);
                        MAIN.post(() -> callback.onSuccess(publicUrl));
                    } else {
                        MAIN.post(() -> callback.onError("Upload failed HTTP " + code + ": " + body));
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "uploadFile exception: " + e.getMessage());
                MAIN.post(() -> callback.onError("Upload error: " + e.getMessage()));
            }
        }).start();
    }

    private static String mimeTypeToExtension(String mimeType) {
        if (mimeType == null) return ".bin";
        switch (mimeType) {
            case "image/jpeg": return ".jpg";
            case "image/png":  return ".png";
            case "image/webp": return ".webp";
            case "image/gif":  return ".gif";
            case "application/pdf": return ".pdf";
            default:
                // Try to extract from mime
                if (mimeType.startsWith("image/")) {
                    return "." + mimeType.substring(6);
                }
                return ".bin";
        }
    }
}

