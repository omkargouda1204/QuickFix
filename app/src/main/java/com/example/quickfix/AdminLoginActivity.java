package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class AdminLoginActivity extends BaseActivity {

    private static final String TAG = "AdminLoginActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            Log.d("ADMIN_LOGIN", "User: " + user);
            if (user == null) {
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
                openLogin();
                return;
            }

            String uid = user.getUid();
            String email = user.getEmail() == null ? "" : user.getEmail();
            SupabaseClient.getUserRoleWithEmail(uid, email, new SupabaseClient.RoleInfoCallback() {
                @Override
                public void onSuccess(SupabaseClient.RoleInfo info) {
                    if (isFinishing()) return;
                    if (info != null && info.isAdmin) {
                        Intent target = new Intent(AdminLoginActivity.this, AdminDashboardActivity.class);
                        target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(target);
                        finish();
                    } else {
                        Toast.makeText(AdminLoginActivity.this, "Admin access required", Toast.LENGTH_SHORT).show();
                        openLogin();
                    }
                }

                @Override
                public void onError(String message) {
                    Log.e(TAG, "LOGIN_ERROR: " + message);
                    if (isFinishing()) return;
                    Toast.makeText(AdminLoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                    openLogin();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Admin redirect crash", e);
            Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            openLogin();
        }
    }

    private void openLogin() {
        Intent login = new Intent(this, LoginActivity.class);
        login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(login);
        finish();
    }
}
