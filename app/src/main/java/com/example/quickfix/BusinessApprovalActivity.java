package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * BusinessApprovalActivity — DEPRECATED
 *
 * This activity is obsolete and redirects to AdminProviderApprovalActivity.
 * Please use AdminProviderApprovalActivity for provider approval management.
 */
public class BusinessApprovalActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Redirect to the new AdminProviderApprovalActivity
        Intent intent = new Intent(this, AdminProviderApprovalActivity.class);
        startActivity(intent);
        finish();
    }
}

