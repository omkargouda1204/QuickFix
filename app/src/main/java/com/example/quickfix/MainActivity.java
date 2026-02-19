package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private TextView welcomeMessage, userEmail;
    private MaterialButton logoutButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        welcomeMessage = findViewById(R.id.welcomeMessage);
        userEmail = findViewById(R.id.userEmail);
        logoutButton = findViewById(R.id.logoutButton);

        // Show generic welcome message (since backend login does not use Firebase)
        welcomeMessage.setText("Welcome to QuickFix!");
        userEmail.setText("");

        // Logout button click listener
        logoutButton.setOnClickListener(v -> redirectToLogin());
    }

    private void redirectToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
