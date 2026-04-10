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

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Log.e("ADMIN_LOGIN", "User: " + user);
        if (user == null) {
            Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            Intent login = new Intent(this, LoginActivity.class);
            login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(login);
            finish();
            return;
        }

        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();
        SupabaseClient.getUserRoleWithEmail(uid, email, new SupabaseClient.RoleInfoCallback() {
            @Override
            public void onSuccess(SupabaseClient.RoleInfo info) {
                if (isFinishing()) return;
                Intent target = (info != null && info.isAdmin)
                        ? new Intent(AdminLoginActivity.this, AdminDashboardActivity.class)
                        : new Intent(AdminLoginActivity.this, MainActivity.class);
                target.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(target);
                finish();
            }

            @Override
            public void onError(String message) {
                Log.e(TAG, "LOGIN_ERROR: " + String.valueOf(message));
                if (isFinishing()) return;
                Toast.makeText(AdminLoginActivity.this, "Login failed", Toast.LENGTH_SHORT).show();
                Intent login = new Intent(AdminLoginActivity.this, LoginActivity.class);
                login.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(login);
                finish();
            }
        });
    }
}
