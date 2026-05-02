package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * UserRepository — Supabase REST calls for Admin User Management.
 *
 * Required SQL (run once in Supabase SQL editor):
 *   ALTER TABLE public.users ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
 */
public class UserRepository {

    private static final String TAG  = "UserRepository";
    private static final String BASE = BuildConfig.SUPABASE_URL + "/rest/v1/users";
    private static final String KEY  = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,  TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ─── Callbacks ────────────────────────────────────────────────────────
    public interface UsersCallback {
        void onSuccess(List<UserModel> users);
        void onError(String msg);
    }

    public interface StatsCallback {
        void onSuccess(int total, int providers, int admins, int verified);
        void onError(String msg);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String msg);
    }

    // ─── Request builders ────────────────────────────────────────────────
    private static Request buildGet(String url) {
        return new Request.Builder().url(url).get()
                .addHeader("apikey",        KEY)
                .addHeader("Authorization", "Bearer " + KEY)
                .addHeader("Accept",        "application/json")
                .build();
    }

    private static Request buildPatch(String url, JSONObject body) {
        return new Request.Builder().url(url)
                .patch(RequestBody.create(body.toString(), JSON))
                .addHeader("apikey",        KEY)
                .addHeader("Authorization", "Bearer " + KEY)
                .addHeader("Content-Type",  "application/json")
                .addHeader("Prefer",        "return=minimal")
                .build();
    }

    private static Request buildDelete(String url) {
        return new Request.Builder().url(url).delete()
                .addHeader("apikey",        KEY)
                .addHeader("Authorization", "Bearer " + KEY)
                .addHeader("Prefer",        "return=minimal")
                .build();
    }

    // ─── Parse a single user JSON object ─────────────────────────────────
    private static UserModel parseUser(JSONObject o) throws Exception {
        UserModel u        = new UserModel();
        u.id               = o.optString("id",                "");
        u.firebaseUid      = o.optString("firebase_uid",      "");
        String name = o.optString("full_name", "");
        if (name.isEmpty()) name = o.optString("name", "Unknown");
        u.name             = name;
        u.email            = o.optString("email",             "");
        u.phone            = o.optString("phone",             "");
        u.isUser           = o.optBoolean("is_user",            true);
        u.isServiceProvider= o.optBoolean("is_service_provider",false);
        u.isAdmin          = o.optBoolean("is_admin",           false);
        u.isVerified       = o.optBoolean("is_verified",        false);
        u.isActive         = o.optBoolean("is_active",          true);
        u.createdAt        = o.optString("created_at",        "");
        u.updatedAt        = o.optString("updated_at",        "");
        return u;
    }

    public interface UserCallback {
        void onSuccess(UserModel user);
        void onError(String msg);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FETCH SINGLE USER BY UID
    // ═══════════════════════════════════════════════════════════════════════
    public static void fetchUserByUid(String uid, UserCallback cb) {
        new Thread(() -> {
            try {
                String url = BASE + "?firebase_uid=eq." + uid + "&select=*&limit=1";
                try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            UserModel u = parseUser(arr.getJSONObject(0));
                            MAIN.post(() -> cb.onSuccess(u));
                        } else {
                            MAIN.post(() -> cb.onError("User not found"));
                        }
                    } else {
                        MAIN.post(() -> cb.onError("HTTP " + r.code()));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e.getMessage()));
            }
        }).start();
    }
    public static void fetchAllUsers(UsersCallback cb) {
        new Thread(() -> {
            try {
                String url = BASE + "?select=*&order=created_at.desc";
                try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    Log.d(TAG, "fetchAllUsers " + r.code());
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        List<UserModel> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++)
                            list.add(parseUser(arr.getJSONObject(i)));
                        MAIN.post(() -> cb.onSuccess(list));
                    } else {
                        String err = "HTTP " + r.code() + ": " + body;
                        MAIN.post(() -> cb.onError(err));
                    }
                }
            } catch (IOException e) {
                MAIN.post(() -> cb.onError("Network error: " + e.getMessage()));
            } catch (Exception e) {
                MAIN.post(() -> cb.onError("Error: " + e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  FETCH STATS (derived client-side from one call)
    // ═══════════════════════════════════════════════════════════════════════
    public static void fetchStats(StatsCallback cb) {
        new Thread(() -> {
            try {
                String url = BASE + "?select=is_service_provider,is_admin,is_verified";
                try (Response r = HTTP.newCall(buildGet(url)).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        int total = arr.length(), prov = 0, adm = 0, ver = 0;
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            if (o.optBoolean("is_service_provider", false)) prov++;
                            if (o.optBoolean("is_admin",            false)) adm++;
                            if (o.optBoolean("is_verified",         false)) ver++;
                        }
                        final int fp = prov, fa = adm, fv = ver;
                        MAIN.post(() -> cb.onSuccess(total, fp, fa, fv));
                    } else {
                        MAIN.post(() -> cb.onError("HTTP " + r.code()));
                    }
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.onError(e.getMessage()));
            }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  CHANGE ROLE
    // ═══════════════════════════════════════════════════════════════════════
    public static void changeRole(String uid, boolean isUser, boolean isProv, boolean isAdmin,
                                  ActionCallback cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_user",             isUser);
                body.put("is_service_provider", isProv);
                body.put("is_admin",            isAdmin);
                String url = BASE + "?firebase_uid=eq." + uid;
                try (Response r = HTTP.newCall(buildPatch(url, body)).execute()) {
                    Log.d(TAG, "changeRole " + r.code());
                    if (r.code() == 200 || r.code() == 204) MAIN.post(cb::onSuccess);
                    else MAIN.post(() -> cb.onError("HTTP " + r.code()));
                }
            } catch (Exception e) { MAIN.post(() -> cb.onError(e.getMessage())); }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SET VERIFIED
    // ═══════════════════════════════════════════════════════════════════════
    public static void setVerified(String uid, boolean verified, ActionCallback cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_verified", verified);
                String url = BASE + "?firebase_uid=eq." + uid;
                try (Response r = HTTP.newCall(buildPatch(url, body)).execute()) {
                    Log.d(TAG, "setVerified " + r.code());
                    if (r.code() == 200 || r.code() == 204) MAIN.post(cb::onSuccess);
                    else MAIN.post(() -> cb.onError("HTTP " + r.code()));
                }
            } catch (Exception e) { MAIN.post(() -> cb.onError(e.getMessage())); }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SET ACTIVE (suspend / activate)
    // ═══════════════════════════════════════════════════════════════════════
    public static void setActive(String uid, boolean active, ActionCallback cb) {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_active", active);
                String url = BASE + "?firebase_uid=eq." + uid;
                try (Response r = HTTP.newCall(buildPatch(url, body)).execute()) {
                    Log.d(TAG, "setActive " + r.code());
                    if (r.code() == 200 || r.code() == 204) MAIN.post(cb::onSuccess);
                    else MAIN.post(() -> cb.onError("HTTP " + r.code()));
                }
            } catch (Exception e) { MAIN.post(() -> cb.onError(e.getMessage())); }
        }).start();
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DELETE USER
    // ═══════════════════════════════════════════════════════════════════════
    public static void deleteUser(String uid, ActionCallback cb) {
        new Thread(() -> {
            try {
                String url = BASE + "?firebase_uid=eq." + uid;
                try (Response r = HTTP.newCall(buildDelete(url)).execute()) {
                    Log.d(TAG, "deleteUser " + r.code());
                    if (r.code() == 200 || r.code() == 204) MAIN.post(cb::onSuccess);
                    else MAIN.post(() -> cb.onError("HTTP " + r.code()));
                }
            } catch (Exception e) { MAIN.post(() -> cb.onError(e.getMessage())); }
        }).start();
    }
}

