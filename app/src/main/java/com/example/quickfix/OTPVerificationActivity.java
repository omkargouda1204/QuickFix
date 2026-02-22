package com.example.quickfix;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * OTPVerificationActivity — no longer used.
 * Replaced by Firebase email verification link flow.
 * Kept as a stub to avoid compilation errors from old references.
 */
public class OTPVerificationActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish(); // immediately finish, should never be started
    }
}
