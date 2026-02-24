package com.example.quickfix;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * RoleManager — Handles role-based access control (RBAC) and permission checks.
 *
 * Verifies if a user is an admin and has specific permissions by querying Supabase.
 * Called before allowing access to admin screens.
 */
public class RoleManager {

    private static final String TAG = "RoleManager";

    // Callback for role check
    public interface RoleCheckCallback {
        void onAdminVerified(String adminRoleId, String roleName);
        void onNotAdmin();
        void onError(String message);
    }

    // Callback for permission check
    public interface PermissionCheckCallback {
        void onPermissionGranted();
        void onPermissionDenied();
        void onError(String message);
    }

    /**
     * Check if a user has admin access (is in admin_users table with valid role).
     * Called during login to decide if user should see admin dashboard.
     */
    public static void checkAdminRole(String firebaseUid, RoleCheckCallback callback) {
        // Query: SELECT admin_role_id, role_name FROM admin_user_details WHERE firebase_uid = ?
        String query = "SELECT au.id, au.admin_role_id, ar.role_name " +
                "FROM admin_users au " +
                "JOIN admin_roles ar ON au.admin_role_id = ar.id " +
                "WHERE au.firebase_uid = '" + escapeQueryString(firebaseUid) + "'";

        SupabaseClient.querySupabase(query, new SupabaseClient.QueryCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                try {
                    if (result.length() > 0) {
                        JSONObject admin = result.getJSONObject(0);
                        String adminRoleId = admin.optString("admin_role_id");
                        String roleName = admin.optString("role_name");
                        Log.d(TAG, "Admin verified: " + roleName);
                        callback.onAdminVerified(adminRoleId, roleName);
                    } else {
                        Log.d(TAG, "User is not an admin: " + firebaseUid);
                        callback.onNotAdmin();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error parsing admin role: " + e.getMessage());
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Database error: " + message);
                callback.onError(message);
            }
        });
    }

    /**
     * Check if an admin has a specific permission.
     * Example: checkPermission(adminUid, "approve_provider", callback)
     */
    public static void checkPermission(String adminUid, String permissionCode, PermissionCheckCallback callback) {
        // Query: SELECT COUNT(*) FROM admin_users au
        // JOIN admin_permissions ap ON au.admin_role_id = ap.admin_role_id
        // WHERE au.firebase_uid = ? AND ap.permission_code = ?

        String query = "SELECT COUNT(*) as permission_count " +
                "FROM admin_users au " +
                "JOIN admin_permissions ap ON au.admin_role_id = ap.admin_role_id " +
                "WHERE au.firebase_uid = '" + escapeQueryString(adminUid) + "' " +
                "AND ap.permission_code = '" + escapeQueryString(permissionCode) + "'";

        SupabaseClient.querySupabase(query, new SupabaseClient.QueryCallback() {
            @Override
            public void onSuccess(JSONArray result) {
                try {
                    if (result.length() > 0) {
                        JSONObject row = result.getJSONObject(0);
                        int count = row.optInt("permission_count", 0);
                        if (count > 0) {
                            Log.d(TAG, "Permission granted: " + permissionCode);
                            callback.onPermissionGranted();
                        } else {
                            Log.d(TAG, "Permission denied: " + permissionCode);
                            callback.onPermissionDenied();
                        }
                    } else {
                        callback.onPermissionDenied();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error checking permission: " + e.getMessage());
                    callback.onError(e.getMessage());
                }
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "Database error: " + message);
                callback.onError(message);
            }
        });
    }

    /**
     * Utility: Escape single quotes in query strings to prevent SQL injection
     */
    private static String escapeQueryString(String input) {
        if (input == null) return "";
        return input.replace("'", "''");
    }
}

