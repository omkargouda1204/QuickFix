package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.quickfix.adapters.ChatMessageAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiChatActivity extends BaseActivity {

    private static final String TAG = "AiChatActivity";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MaterialToolbar toolbar;
    private RecyclerView rvMessages;
    private LinearLayout emptyState, composerBar;
    private MaterialCardView suggestionCard;
    private ProgressBar progressBar;
    private TextInputEditText etMessage;
    private MaterialButton btnSend, btnBookNow;
    private TextView tvEmptyState, tvServiceType, tvIssue, tvCost, tvBookingSuggestion;

    private final List<ChatMessage> messages = new ArrayList<>();
    private ChatMessageAdapter adapter;
    private String currentUserId = "";
    private String suggestedCategory = "";
    private boolean sending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Please log in to use AI Assistant", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        currentUserId = safe(user.getUid());

        bindViews();
        setupToolbar();
        setupRecycler();
        setupComposer();
        loadHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && messages.isEmpty()) {
            loadHistory();
        }
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarAi);
        rvMessages = findViewById(R.id.rvAiMessages);
        emptyState = findViewById(R.id.emptyState);
        composerBar = findViewById(R.id.composerBar);
        suggestionCard = findViewById(R.id.suggestionCard);
        progressBar = findViewById(R.id.progressBar);
        etMessage = findViewById(R.id.etAiMessage);
        btnSend = findViewById(R.id.btnAiSend);
        btnBookNow = findViewById(R.id.btnBookNow);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        tvServiceType = findViewById(R.id.tvAiServiceType);
        tvIssue = findViewById(R.id.tvAiIssue);
        tvCost = findViewById(R.id.tvAiCost);
        tvBookingSuggestion = findViewById(R.id.tvAiBookingSuggestion);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.ai_assistant_title));
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        adapter = new ChatMessageAdapter(this, currentUserId, "user");
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        rvMessages.setAdapter(adapter);
    }

    private void setupComposer() {
        btnSend.setOnClickListener(v -> sendMessage());
        if (btnBookNow != null) {
            btnBookNow.setOnClickListener(v -> openBookingFlow());
        }
        if (etMessage != null) {
            etMessage.setOnEditorActionListener((v, actionId, event) -> {
                sendMessage();
                return true;
            });
        }
    }

    private void loadHistory() {
        showLoading(true);
        new Thread(() -> {
            List<ChatMessage> history = new ArrayList<>();
            try {
                String url = BuildConfig.SUPABASE_URL + "/rest/v1/ai_messages?select=*&user_id=eq." + encode(currentUserId) + "&order=created_at.asc";
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();
                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.isSuccessful()) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject row = arr.getJSONObject(i);
                            String userMsg = safe(row.optString("message", ""));
                            String aiMsg = safe(row.optString("response", ""));
                            String createdAt = safe(row.optString("created_at", ""));
                            if (!userMsg.isEmpty()) {
                                history.add(buildMessage("user", userMsg, createdAt));
                            }
                            if (!aiMsg.isEmpty()) {
                                history.add(buildMessage("assistant", aiMsg, createdAt));
                            }
                        }
                    } else {
                        Log.w(TAG, "loadHistory HTTP " + r.code() + " body=" + body);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadHistory failed", e);
            }

            main.post(() -> {
                showLoading(false);
                messages.clear();
                messages.addAll(history);
                refreshMessages();
                if (messages.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    tvEmptyState.setText(R.string.ai_assistant_empty);
                } else {
                    emptyState.setVisibility(View.GONE);
                }
            });
        }).start();
    }

    private void sendMessage() {
        if (sending) return;
        String text = etMessage != null && etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;

        etMessage.setText("");
        emptyState.setVisibility(View.GONE);

        ChatMessage userMessage = buildMessage("user", text, nowIso());
        messages.add(userMessage);
        refreshMessages();
        setSendingState(true);

        new Thread(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("user_id", currentUserId);
                payload.put("message", text);
                payload.put("prompt", getSystemPrompt());

                Request req = new Request.Builder()
                        .url(BuildConfig.SUPABASE_AI_ASSISTANT_FN_URL)
                        .post(RequestBody.create(payload.toString(), JSON))
                        .addHeader("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Authorization", "Bearer " + BuildConfig.SUPABASE_ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "{}";
                    if (!r.isSuccessful()) {
                        throw new IllegalStateException("HTTP " + r.code() + " " + body);
                    }

                    JSONObject data = new JSONObject(body);
                    String reply = safe(data.optString("reply", ""));
                    if (reply.isEmpty()) reply = safe(data.optString("response", ""));
                    if (reply.isEmpty()) reply = "AI not available, try again later";

                    String serviceType = safe(data.optString("service_type", ""));
                    String issue = safe(data.optString("possible_issue", ""));
                    String cost = safe(data.optString("estimated_cost", ""));
                    String bookingSuggestion = safe(data.optString("booking_suggestion", ""));
                    String category = normalizeCategory(safe(data.optString("suggested_category", serviceType)));

                    ChatMessage aiMessage = buildMessage("assistant", reply, nowIso());
                    main.post(() -> {
                        messages.add(aiMessage);
                        updateSuggestionCard(serviceType, issue, cost, bookingSuggestion, category);
                        refreshMessages();
                        setSendingState(false);
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage failed", e);
                main.post(() -> {
                    messages.add(buildMessage("assistant", getString(R.string.ai_assistant_unavailable), nowIso()));
                    refreshMessages();
                    hideSuggestionCard();
                    setSendingState(false);
                    Toast.makeText(this, R.string.ai_assistant_unavailable, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateSuggestionCard(String serviceType, String issue, String cost, String bookingSuggestion, String category) {
        suggestedCategory = safe(category);
        if (suggestionCard == null) return;

        boolean hasAny = !safe(serviceType).isEmpty() || !safe(issue).isEmpty() || !safe(cost).isEmpty() || !safe(bookingSuggestion).isEmpty();
        if (!hasAny && suggestedCategory.isEmpty()) {
            hideSuggestionCard();
            return;
        }

        tvServiceType.setText(safe(serviceType).isEmpty() ? getString(R.string.ai_assistant_service_type) + ": " + suggestedCategory : getString(R.string.ai_assistant_service_type) + ": " + serviceType);
        tvIssue.setText(safe(issue).isEmpty() ? getString(R.string.ai_assistant_issue) + ": Not specified" : getString(R.string.ai_assistant_issue) + ": " + issue);
        tvCost.setText(safe(cost).isEmpty() ? getString(R.string.ai_assistant_cost) + ": Ask for quote" : getString(R.string.ai_assistant_cost) + ": " + cost);
        tvBookingSuggestion.setText(safe(bookingSuggestion).isEmpty() ? getString(R.string.ai_assistant_booking_suggestion) + ": We can help you book this service." : getString(R.string.ai_assistant_booking_suggestion) + ": " + bookingSuggestion);
        suggestionCard.setVisibility(View.VISIBLE);
        if (btnBookNow != null) btnBookNow.setVisibility(suggestedCategory.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void hideSuggestionCard() {
        suggestedCategory = "";
        if (suggestionCard != null) suggestionCard.setVisibility(View.GONE);
    }

    private void openBookingFlow() {
        if (suggestedCategory.isEmpty()) return;
        Intent i = new Intent(this, ProviderListActivity.class);
        i.putExtra(ProviderListActivity.EXTRA_CATEGORY, suggestedCategory);
        i.putExtra(ProviderListActivity.EXTRA_QUERY, suggestedCategory);
        startActivity(i);
    }

    private void refreshMessages() {
        adapter.setItems(new ArrayList<>(messages));
        if (messages.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            rvMessages.post(() -> rvMessages.scrollToPosition(Math.max(0, adapter.getItemCount() - 1)));
        }
    }

    private void setSendingState(boolean active) {
        sending = active;
        if (btnSend != null) btnSend.setEnabled(!active);
        if (etMessage != null) etMessage.setEnabled(!active);
        if (progressBar != null) progressBar.setVisibility(active ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean visible) {
        if (progressBar != null) progressBar.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private ChatMessage buildMessage(String senderType, String text, String createdAt) {
        ChatMessage m = new ChatMessage();
        m.id = "";
        m.bookingId = "";
        m.chatType = "ai_assistant";
        m.senderId = "user".equals(senderType) ? currentUserId : "assistant";
        m.receiverId = "user".equals(senderType) ? "assistant" : currentUserId;
        m.senderType = senderType;
        m.message = text;
        m.createdAt = createdAt;
        m.isRead = true;
        m.isSeen = true;
        return m;
    }

    private String getSystemPrompt() {
        return "You are an AI assistant for a service booking app. "
                + "Help users identify problems, suggest services, estimate cost in INR, and guide them to book services. "
                + "Return concise, helpful answers with fields: service_type, possible_issue, estimated_cost, booking_suggestion, suggested_category, reply. "
                + "If the user mentions AC, normalize suggested_category to AC Service. For cleaning use Cleaning, plumbing use Plumbing, painting use Painting, electrical use Electrical, carpentry use Carpentry, repairs use Repair.";
    }

    private String normalizeCategory(String raw) {
        String s = safe(raw).toLowerCase(Locale.US);
        if (s.contains("ac")) return "AC Service";
        if (s.contains("clean")) return "Cleaning";
        if (s.contains("paint")) return "Painting";
        if (s.contains("plumb")) return "Plumbing";
        if (s.contains("elect")) return "Electrical";
        if (s.contains("carp")) return "Carpentry";
        if (s.contains("repair")) return "Repair";
        return safe(raw);
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value == null ? "" : value;
        }
    }

    private String nowIso() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new java.util.Date());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
