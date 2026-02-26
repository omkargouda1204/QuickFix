package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * RoleManager — Role-based access control using Supabase REST API directly.
 *
 * All queries use the PostgREST REST API over HTTPS.
 * No raw SQL. No backend server.
 */
public class RoleManager {

    private static final String TAG = "RoleManager";

    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15,    TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    // ── Callbacks ─────────────────────────────────────────────────────────

    public interface RoleCheckCallback {
        void onAdminVerified(String adminRoleId, String roleName);
        void onNotAdmin();
        void onError(String message);
    }

    public interface PermissionCheckCallback {
        void onPermissionGranted();
        void onPermissionDenied();
        void onError(String message);
    }

    // ── Build common Supabase GET request ─────────────────────────────────

    private static Request buildGet(String path) {
        return new Request.Builder()
                .url(SUPABASE_URL + path)
                .get()
                .addHeader("apikey",        ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept",        "application/json")
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CHECK ADMIN ROLE
    //  Queries admin_users JOIN admin_roles using REST filter parameters.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check if a user is in the admin_users table and get their role.
     *
     * REST equivalent:
     *   GET /rest/v1/admin_users
     *     ?firebase_uid=eq.<uid>
     *     &select=id,admin_role_id,admin_roles(role_name)
     *     &limit=1
     */
    public static void checkAdminRole(String firebaseUid, RoleCheckCallback callback) {
        if (firebaseUid == null || firebaseUid.isEmpty()) {
            MAIN.post(callback::onNotAdmin);
            return;
        }

        String path = "/rest/v1/admin_users"
                + "?firebase_uid=eq." + firebaseUid
                + "&select=id,admin_role_id,admin_roles(role_name)"
                + "&limit=1";

        HTTP.newCall(buildGet(path)).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "checkAdminRole network error: " + e.getMessage());
                MAIN.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                Log.d(TAG, "checkAdminRole HTTP " + code + " | " + body);

                if (code != 200) {
                    MAIN.post(() -> callback.onError("Supabase HTTP " + code));
                    return;
                }
                try {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        JSONObject row = arr.getJSONObject(0);
                        String adminRoleId = String.valueOf(row.optInt("admin_role_id"));
                        // Nested object from PostgREST join
                        JSONObject rolesObj = row.optJSONObject("admin_roles");
                        String roleName = rolesObj != null
                                ? rolesObj.optString("role_name", "admin")
                                : "admin";
                        Log.d(TAG, "Admin verified: role=" + roleName);
                        MAIN.post(() -> callback.onAdminVerified(adminRoleId, roleName));
                    } else {
                        Log.d(TAG, "Not an admin: uid=" + firebaseUid);
                        MAIN.post(callback::onNotAdmin);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "checkAdminRole parse error: " + e.getMessage());
                    MAIN.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CHECK PERMISSION
    //  Queries admin_permissions filtered by admin_role_id for a given user.
    //  Two-step: 1) get admin_role_id, 2) check permission for that role.
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Check if an admin has a specific permission code.
     *
     * Step 1: GET admin_users where firebase_uid = uid  → get admin_role_id
     * Step 2: GET admin_permissions where admin_role_id = X AND permission_code = Y
     */
    public static void checkPermission(String adminUid, String permissionCode,
                                        PermissionCheckCallback callback) {
        if (adminUid == null || adminUid.isEmpty()
                || permissionCode == null || permissionCode.isEmpty()) {
            MAIN.post(callback::onPermissionDenied);
            return;
        }

        // Step 1: get the admin_role_id for this user
        String step1Path = "/rest/v1/admin_users"
                + "?firebase_uid=eq." + adminUid
                + "&select=admin_role_id"
                + "&limit=1";

        HTTP.newCall(buildGet(step1Path)).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "checkPermission step1 error: " + e.getMessage());
                MAIN.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();

                if (code != 200) {
                    MAIN.post(() -> callback.onError("Supabase HTTP " + code));
                    return;
                }
                try {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() == 0) {
                        MAIN.post(callback::onPermissionDenied);
                        return;
                    }
                    int adminRoleId = arr.getJSONObject(0).optInt("admin_role_id", -1);
                    if (adminRoleId == -1) {
                        MAIN.post(callback::onPermissionDenied);
                        return;
                    }
                    // Step 2: check permission for this role
                    checkPermissionForRole(adminRoleId, permissionCode, callback);
                } catch (Exception e) {
                    Log.e(TAG, "checkPermission parse step1: " + e.getMessage());
                    MAIN.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }

    private static void checkPermissionForRole(int adminRoleId, String permissionCode,
                                                PermissionCheckCallback callback) {
        String encodedCode;
        try {
            encodedCode = URLEncoder.encode(permissionCode, "UTF-8");
        } catch (Exception e) {
            encodedCode = permissionCode;
        }

        String path = "/rest/v1/admin_permissions"
                + "?admin_role_id=eq." + adminRoleId
                + "&permission_code=eq." + encodedCode
                + "&select=id"
                + "&limit=1";

        HTTP.newCall(buildGet(path)).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "checkPermissionForRole error: " + e.getMessage());
                MAIN.post(() -> callback.onError("Network error: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                int code = response.code();
                String body = response.body() != null ? response.body().string() : "[]";
                response.close();
                Log.d(TAG, "checkPermission step2 HTTP " + code + " | " + body);

                if (code != 200) {
                    MAIN.post(() -> callback.onError("Supabase HTTP " + code));
                    return;
                }
                try {
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        Log.d(TAG, "Permission granted: " + permissionCode);
                        MAIN.post(callback::onPermissionGranted);
                    } else {
                        Log.d(TAG, "Permission denied: " + permissionCode);
                        MAIN.post(callback::onPermissionDenied);
                    }
                } catch (Exception e) {
                    MAIN.post(() -> callback.onError("Parse error: " + e.getMessage()));
                }
            }
        });
    }
}
