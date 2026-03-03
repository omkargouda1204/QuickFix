package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Redirect wrapper -> AdminProviderApprovalActivity */
public class BusinessApprovalActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, AdminProviderApprovalActivity.class));
        finish();
    }
}
