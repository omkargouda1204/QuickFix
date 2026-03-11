package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

/**
 * AdminLoginActivity — no longer used.
 * Admin login now goes through the same LoginActivity.
 * Role check after login automatically routes admins to AdminDashboardActivity.
 * This stub simply redirects to LoginActivity to avoid any stale deep-links.
 */
public class AdminLoginActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
