package com.example.quickfix;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MessagesActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Messages — coming soon!");
        tv.setTextSize(20f);
        tv.setPadding(40, 120, 40, 40);
        setContentView(tv);
    }
}

