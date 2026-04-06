package com.example.quickfix;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.quickfix.api.RetrofitProvider;
import com.example.quickfix.api.SupabaseStorageApi;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.Call;

/**
 * SupabaseStorageHelper — Uploads images to Supabase Storage via the REST API.
 *
 * Buckets used:
 *   • provider-business-images  (business_image_url)
 *   • provider-id-proofs        (id_proof_url)
 *   • Quickfix                  (business media: images and videos)
 *
 * All buckets must exist in Supabase Storage and be configured as PUBLIC or have
 * the appropriate RLS policies for the client role used by the app.
 *
 * NOTE: QuickFix currently uploads to Supabase with the anon key, so the
 * profile-images bucket must allow that client role until the app migrates to a
 * true Supabase Auth session.
 *
 * CREATE STORAGE BUCKET: provider-business-images  (public: true)
 * CREATE STORAGE BUCKET: provider-id-proofs        (public: false, admin-readable)
 * CREATE STORAGE BUCKET: Quickfix                  (public: true)
 */
public class SupabaseStorageHelper {

    private static final String TAG = "SupabaseStorage";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180,   TimeUnit.SECONDS)
            .writeTimeout(180,  TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface UploadCallback {
        void onSuccess(String publicUrl);
        void onError(String message);
    }

    public interface PrivateUploadCallback {
        void onSuccess(String storagePath);
        void onError(String message);
    }

    // ── Bucket names ───────────────────────────────────────────────────────
    public static final String BUCKET_BUSINESS_IMAGES = "provider-business-images";
    public static final String BUCKET_ID_PROOFS       = "provider-id-proofs";
    public static final String BUCKET_PROFILE_IMAGES  = "profile-images";
    public static final String BUCKET_QUICKFIX        = "Quickfix";

    private static final int BANNER_MAX_SOURCE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final int BANNER_MAX_UPLOAD_BYTES = 2 * 1024 * 1024;  // 2 MB
    private static final int PROFILE_MAX_SOURCE_BYTES = 10 * 1024 * 1024; // 10 MB
    private static final int PROFILE_MAX_UPLOAD_BYTES = 1024 * 1024;      // 1 MB

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
                UploadPayload payload = buildUploadPayload(context, fileUri, prefix);
                ResponseResult result = uploadBytes(bucket, payload.storagePath, payload.fileBytes, payload.mimeType);
                Log.d(TAG, "uploadFile HTTP " + result.httpCode + " | " + result.body);

                String publicUrl = getPublicObjectUrl(bucket, payload.storagePath);

                if (isUploadSuccess(result.httpCode, result.body)) {
                    MAIN.post(() -> callback.onSuccess(publicUrl));
                } else {
                    String message = formatUploadError(result.httpCode, result.body, bucket);
                    MAIN.post(() -> callback.onError(message));
                }

            } catch (Exception e) {
                Log.e(TAG, "uploadFile exception: " + e.getMessage());
                MAIN.post(() -> callback.onError("Upload error: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Upload to a private bucket. Caller should persist storagePath in DB and request signed URL for display.
     */
    public static void uploadPrivateFile(Context context, Uri fileUri, String bucket,
                                         String prefix, PrivateUploadCallback callback) {
        new Thread(() -> {
            try {
                UploadPayload payload = buildUploadPayload(context, fileUri, prefix);
                ResponseResult result = uploadBytes(bucket, payload.storagePath, payload.fileBytes, payload.mimeType);
                Log.d(TAG, "uploadPrivateFile HTTP " + result.httpCode + " | " + result.body);

                if (isUploadSuccess(result.httpCode, result.body)) {
                    MAIN.post(() -> callback.onSuccess(payload.storagePath));
                } else {
                    String message = formatUploadError(result.httpCode, result.body, bucket);
                    MAIN.post(() -> callback.onError(message));
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadPrivateFile exception: " + e.getMessage());
                MAIN.post(() -> callback.onError("Upload error: " + e.getMessage()));
            }
        }).start();
    }

    // ─────────────────────────────────────────────────────────────────────
    //  SIGNED URL  — for private bucket files (ID proofs, admin only)
    // ─────────────────────────────────────────────────────────────────────

    public interface SignedUrlCallback {
        void onSuccess(String signedUrl);
        void onError(String message);
    }

    /**
     * Generates a short-lived signed URL to access a private Supabase Storage object.
     * Only call from admin dashboard screens.
     *
     * @param bucket       e.g. "QuickFix"
     * @param storagePath  path stored in DB, e.g. "uid/uid_abc123.jpg"
     *                     If the full URL is stored, we extract the path automatically.
     * @param expiresIn    seconds the signed URL is valid (e.g. 300 = 5 minutes)
     * @param callback     receives the signed URL on success
     */
    public static void getSignedUrl(String bucket, String storagePath,
                                    int expiresIn, SignedUrlCallback callback) {
        new Thread(() -> {
            try {
                // If storagePath is a full URL, extract just the path after /object/bucket/
                String path = storagePath;
                String marker = "/object/" + bucket + "/";
                int idx = storagePath.indexOf(marker);
                if (idx >= 0) {
                    path = storagePath.substring(idx + marker.length());
                } else {
                    // Also try /object/public/bucket/
                    String pubMarker = "/object/public/" + bucket + "/";
                    int pidx = storagePath.indexOf(pubMarker);
                    if (pidx >= 0) path = storagePath.substring(pidx + pubMarker.length());
                }

                String url = SUPABASE_URL + "/storage/v1/object/sign/" + bucket + "/" + path;

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("expiresIn", expiresIn);

                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body.toString(),
                                MediaType.parse("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .build();

                try (Response response = HTTP.newCall(request).execute()) {
                    int code = response.code();
                    String respBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "getSignedUrl HTTP " + code + " | " + respBody);

                    if (code == 200) {
                        org.json.JSONObject json = new org.json.JSONObject(respBody);
                        String signedUrl = json.optString("signedURL", "");
                        if (signedUrl.isEmpty()) signedUrl = json.optString("signedUrl", "");
                        if (!signedUrl.startsWith("http")) {
                            // PostgREST returns relative path — prepend base URL
                            signedUrl = SUPABASE_URL + "/storage/v1" + signedUrl;
                        }
                        String finalUrl = signedUrl;
                        MAIN.post(() -> callback.onSuccess(finalUrl));
                    } else {
                        MAIN.post(() -> callback.onError("HTTP " + code + ": " + respBody));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "getSignedUrl exception: " + e.getMessage());
                MAIN.post(() -> callback.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Uploads a profile image to:
     *   profile-images/profiles/{userId}/{userId}_{timestamp}.jpg
     * The returned public URL is stored in the users table.
     */
    public static void uploadProfileImage(Context context,
                                          Uri fileUri,
                                          String userId,
                                          UploadCallback callback) {
        if (callback == null) return;

        FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
        if (current == null) {
            MAIN.post(() -> callback.onError("Please login again before uploading profile image."));
            return;
        }

        String uid = userId != null && !userId.trim().isEmpty() ? userId.trim() : current.getUid();
        if (uid == null || uid.isEmpty()) {
            MAIN.post(() -> callback.onError("Invalid user session. Please login again."));
            return;
        }

        if (!uid.equals(current.getUid())) {
            MAIN.post(() -> callback.onError("Session mismatch. Please login and try again."));
            return;
        }

        new Thread(() -> {
            try {
                String storagePath = "profiles/" + uid + "/" + uid + "_" + System.currentTimeMillis() + ".jpg";
                UploadPayload payload = buildUploadPayloadForFixedPath(context, fileUri, storagePath);
                ResponseResult result = uploadBytes(BUCKET_PROFILE_IMAGES,
                        payload.storagePath, payload.fileBytes, payload.mimeType);

                String publicUrl = getPublicObjectUrl(BUCKET_PROFILE_IMAGES, payload.storagePath);

                if (isUploadSuccess(result.httpCode, result.body)) {
                    MAIN.post(() -> callback.onSuccess(publicUrl));
                } else {
                    String msg = formatUploadError(result.httpCode, result.body, BUCKET_PROFILE_IMAGES);
                    Log.e(TAG, "uploadProfileImage failed HTTP " + result.httpCode + ": " + result.body);
                    MAIN.post(() -> callback.onError(msg));
                }
            } catch (Exception e) {
                Log.e(TAG, "uploadProfileImage exception", e);
                MAIN.post(() -> callback.onError("Upload error: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * Banner upload helper: validates image file, compresses it and uploads to banner-images/banners/.
     * Returns a public URL (never raw bytes/path).
     */
    public static void uploadBannerImage(Context context, Uri fileUri, UploadCallback callback) {
        if (callback == null) return;
        if (context == null || fileUri == null) {
            MAIN.post(() -> callback.onError("Invalid image selection."));
            return;
        }

        new Thread(() -> {
            try {
                String mimeType = context.getContentResolver().getType(fileUri);
                if (mimeType == null || mimeType.trim().isEmpty()) mimeType = "image/jpeg";
                if (!isSupportedBannerMime(mimeType)) {
                    String finalMimeType = mimeType;
                    MAIN.post(() -> callback.onError("Unsupported image type: " + finalMimeType
                            + ". Use JPG, PNG, WEBP, or HEIC."));
                    return;
                }

                byte[] sourceBytes = readUriBytes(context, fileUri, BANNER_MAX_SOURCE_BYTES);
                if (sourceBytes.length == 0) {
                    MAIN.post(() -> callback.onError("Selected image is empty."));
                    return;
                }

                byte[] optimized = compressBannerBytes(sourceBytes, BANNER_MAX_UPLOAD_BYTES);
                String storagePath = "banners/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
                ResponseResult result = uploadBytes("banner-images", storagePath, optimized, "image/jpeg");

                if (isUploadSuccess(result.httpCode, result.body)) {
                    String publicUrl = getPublicObjectUrl("banner-images", storagePath);
                    MAIN.post(() -> callback.onSuccess(publicUrl));
                } else {
                    String message = formatUploadError(result.httpCode, result.body, "banner-images");
                    MAIN.post(() -> callback.onError(message));
                }
            } catch (IllegalStateException tooLarge) {
                MAIN.post(() -> callback.onError(tooLarge.getMessage()));
            } catch (Exception e) {
                Log.e(TAG, "uploadBannerImage exception", e);
                MAIN.post(() -> callback.onError("Image upload failed: " + e.getMessage()));
            }
        }).start();
    }

    private static boolean isSupportedBannerMime(String mimeType) {
        if (mimeType == null) return false;
        String safe = mimeType.trim().toLowerCase();
        return "image/jpeg".equals(safe)
                || "image/jpg".equals(safe)
                || "image/png".equals(safe)
                || "image/webp".equals(safe)
                || "image/heic".equals(safe)
                || "image/heif".equals(safe);
    }

    /**
     * Converts either a storage path or existing URL into a canonical public object URL.
     */
    public static String getPublicObjectUrl(String bucket, String pathOrUrl) {
        String b = bucket == null ? "" : bucket.trim();
        String raw = pathOrUrl == null ? "" : pathOrUrl.trim();
        if (b.isEmpty() || raw.isEmpty()) return "";
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw;

        String marker = "/storage/v1/object/public/" + b + "/";
        int markerIndex = raw.indexOf(marker);
        if (markerIndex >= 0) {
            String path = raw.substring(markerIndex + marker.length());
            return SUPABASE_URL + marker + Uri.encode(path, "/");
        }

        String normalizedPath = raw.startsWith("/") ? raw.substring(1) : raw;
        return SUPABASE_URL + "/storage/v1/object/public/" + b + "/" + Uri.encode(normalizedPath, "/");
    }

    private static UploadPayload buildUploadPayloadForFixedPath(Context context,
                                                                Uri fileUri,
                                                                String storagePath) throws Exception {
        UploadPayload payload = new UploadPayload();
        payload.mimeType = "image/jpeg";
        payload.storagePath = storagePath;

        byte[] sourceBytes = readUriBytes(context, fileUri, PROFILE_MAX_SOURCE_BYTES);
        payload.fileBytes = compressProfileBytes(sourceBytes, PROFILE_MAX_UPLOAD_BYTES);

        return payload;
    }

    private static byte[] readUriBytes(Context context, Uri fileUri, int maxBytes) throws Exception {
        try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            if (is == null) throw new IllegalStateException("Cannot read selected file");
            return readAllBytes(is, maxBytes);
        }
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int nRead;
        while ((nRead = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static byte[] readAllBytes(InputStream is, int maxBytes) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int nRead;
        int total = 0;
        while ((nRead = is.read(chunk)) != -1) {
            total += nRead;
            if (total > maxBytes) {
                throw new IllegalStateException("Image is too large. Please select an image under 10 MB.");
            }
            buffer.write(chunk, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static byte[] compressBannerBytes(byte[] source, int maxBytes) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
        if (bitmap == null) {
            throw new IllegalStateException("Unsupported or corrupted image file.");
        }

        Bitmap current = bitmap;
        try {
            int quality = 90;
            for (int iteration = 0; iteration < 6; iteration++) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                current.compress(Bitmap.CompressFormat.JPEG, quality, out);
                byte[] result = out.toByteArray();
                if (result.length <= maxBytes) return result;

                quality -= 10;
                if (quality < 55) {
                    quality = 85;
                    int w = Math.max(480, (int) (current.getWidth() * 0.82f));
                    int h = Math.max(320, (int) (current.getHeight() * 0.82f));
                    if (w == current.getWidth() && h == current.getHeight()) {
                        break;
                    }
                    Bitmap scaled = Bitmap.createScaledBitmap(current, w, h, true);
                    if (current != bitmap && !current.isRecycled()) current.recycle();
                    current = scaled;
                }
            }

            java.io.ByteArrayOutputStream fallback = new java.io.ByteArrayOutputStream();
            current.compress(Bitmap.CompressFormat.JPEG, 70, fallback);
            byte[] finalBytes = fallback.toByteArray();
            if (finalBytes.length > maxBytes) {
                throw new IllegalStateException("Compressed image is still too large. Please choose a smaller image.");
            }
            return finalBytes;
        } finally {
            if (current != null && !current.isRecycled()) current.recycle();
            if (bitmap != null && bitmap != current && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static byte[] compressProfileBytes(byte[] source, int maxBytes) throws Exception {
        Bitmap bitmap = BitmapFactory.decodeByteArray(source, 0, source.length);
        if (bitmap == null) {
            throw new IllegalStateException("Unsupported or corrupted image file.");
        }

        Bitmap current = bitmap;
        try {
            int quality = 88;
            for (int iteration = 0; iteration < 7; iteration++) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                current.compress(Bitmap.CompressFormat.JPEG, quality, out);
                byte[] result = out.toByteArray();
                if (result.length <= maxBytes) return result;

                quality -= 8;
                if (quality < 56) {
                    quality = 82;
                    int w = Math.max(480, (int) (current.getWidth() * 0.8f));
                    int h = Math.max(480, (int) (current.getHeight() * 0.8f));
                    if (w == current.getWidth() && h == current.getHeight()) {
                        break;
                    }
                    Bitmap scaled = Bitmap.createScaledBitmap(current, w, h, true);
                    if (current != bitmap && !current.isRecycled()) current.recycle();
                    current = scaled;
                }
            }

            java.io.ByteArrayOutputStream fallback = new java.io.ByteArrayOutputStream();
            current.compress(Bitmap.CompressFormat.JPEG, 68, fallback);
            byte[] finalBytes = fallback.toByteArray();
            if (finalBytes.length > maxBytes) {
                throw new IllegalStateException("Image is too large after compression. Please choose a smaller photo.");
            }
            return finalBytes;
        } finally {
            if (current != null && !current.isRecycled()) current.recycle();
            if (bitmap != null && bitmap != current && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private static boolean isAlreadyExistsConflict(int code, String body) {
        if (code != 409) return false;
        String safeBody = body == null ? "" : body.toLowerCase();
        return safeBody.contains("already exists")
                || safeBody.contains("duplicate")
                || safeBody.contains("resource already")
                || safeBody.contains("key") && safeBody.contains("exists");
    }

    private static String formatUploadError(int code, String body, String bucket) {
        String safeBody = body == null ? "" : body;
        String lower = safeBody.toLowerCase();

        if (code == 401 || code == 403 || lower.contains("\"error\":\"unauth\"")
                || lower.contains("\"statuscode\":\"403\"")) {
            return "Storage permission denied for bucket '" + bucket +
                    "'. Please add a Supabase Storage INSERT policy for the client role in use (anon for this app, or authenticated if you migrate to Supabase Auth).";
        }

        if (lower.contains("bucket") && lower.contains("not") && lower.contains("found")) {
            return "Bucket '" + bucket + "' not found in Supabase Storage.";
        }

        return "Upload failed HTTP " + code + ": " + safeBody;
    }

    private static String mimeTypeToExtension(String mimeType) {
        if (mimeType == null) return ".bin";
        switch (mimeType) {
            case "image/jpeg":  return ".jpg";
            case "image/png":   return ".png";
            case "image/webp":  return ".webp";
            case "image/gif":   return ".gif";
            case "video/mp4":   return ".mp4";
            case "video/3gpp":  return ".3gp";
            case "video/webm":  return ".webm";
            case "video/quicktime": return ".mov";
            case "application/pdf": return ".pdf";
            default:
                if (mimeType.startsWith("image/")) return "." + mimeType.substring(6);
                if (mimeType.startsWith("video/")) return "." + mimeType.substring(6);
                return ".bin";
        }
    }

    private static boolean isUploadSuccess(int code, String body) {
        return code == 200 || code == 201 || code == 204 || code == 409
                || isAlreadyExistsConflict(code, body);
    }

    private static final class UploadPayload {
        byte[] fileBytes;
        String mimeType;
        String storagePath;
    }

    private static final class ResponseResult {
        int httpCode;
        String body;
    }

    private static UploadPayload buildUploadPayload(Context context, Uri fileUri, String prefix) throws Exception {
        UploadPayload payload = new UploadPayload();

        String mimeType = context.getContentResolver().getType(fileUri);
        if (mimeType == null) mimeType = "application/octet-stream";
        payload.mimeType = mimeType;

        String ext = mimeTypeToExtension(mimeType);
        String uniqueId = UUID.randomUUID().toString().replace("-", "");
        String safePrefix = prefix == null ? "user" : prefix.trim();
        if (safePrefix.isEmpty()) safePrefix = "user";
        payload.storagePath = safePrefix + "/" + uniqueId + ext;

        try (InputStream is = context.getContentResolver().openInputStream(fileUri)) {
            if (is == null) throw new IllegalStateException("Cannot read file");
            payload.fileBytes = readAllBytes(is);
        }

        return payload;
    }

    private static ResponseResult uploadBytes(String bucket, String storagePath, byte[] fileBytes,
                                              String mimeType) throws Exception {
        ResponseResult out = new ResponseResult();
        SupabaseStorageApi api = RetrofitProvider.get().create(SupabaseStorageApi.class);
        Call<ResponseBody> call = api.uploadObject(
                bucket,
                storagePath,
                RequestBody.create(fileBytes, MediaType.parse(mimeType)),
                ANON_KEY,
                "Bearer " + ANON_KEY,
                mimeType,
                "true"
        );

        retrofit2.Response<ResponseBody> response = call.execute();
        out.httpCode = response.code();
        out.body = response.body() != null ? response.body().string() : "";
        return out;
    }
}
