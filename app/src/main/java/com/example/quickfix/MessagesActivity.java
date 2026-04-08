package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;

public class MessagesActivity extends BaseActivity {

    public static final String EXTRA_CONTEXT_TITLE = "extra_context_title";
    public static final String EXTRA_CONTEXT_SUBTITLE = "extra_context_subtitle";
    public static final String EXTRA_CONTEXT_BODY = "extra_context_body";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String title = i != null ? i.getStringExtra(EXTRA_CONTEXT_TITLE) : null;
        String subtitle = i != null ? i.getStringExtra(EXTRA_CONTEXT_SUBTITLE) : null;
        String body = i != null ? i.getStringExtra(EXTRA_CONTEXT_BODY) : null;

        TextView tv = new TextView(this);
        StringBuilder text = new StringBuilder();
        text.append(TextUtils.isEmpty(title) ? "Messages" : title.trim());
        if (!TextUtils.isEmpty(subtitle)) {
            text.append("\n").append(subtitle.trim());
        }
        text.append("\n\nChat feature coming soon.");
        if (!TextUtils.isEmpty(body)) {
            text.append("\n\n").append(body.trim());
        }

        tv.setText(text.toString());
        tv.setTextSize(18f);
        tv.setPadding(40, 120, 40, 40);
        setContentView(tv);
    }
}

