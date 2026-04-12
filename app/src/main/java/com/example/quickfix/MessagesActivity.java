package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MessagesActivity extends BaseActivity {

    public static final String EXTRA_BOOKING_ID = "booking_id";
    public static final String EXTRA_PROVIDER_ID = "provider_id";
    public static final String EXTRA_PROVIDER_UID = "provider_uid";
    public static final String EXTRA_USER_UID = "user_uid";
    public static final String EXTRA_PROVIDER_NAME = "provider_name";

    public static final String EXTRA_CONTEXT_TITLE = "context_title";
    public static final String EXTRA_CONTEXT_SUBTITLE = "context_subtitle";
    public static final String EXTRA_CONTEXT_BODY = "context_body";

    private static final String TAG = "MessagesActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MaterialToolbar toolbar;
    private TextView tvChatTitle, tvChatSubtitle, tvChatStatus, tvEmptyState;
    private RecyclerView rvMessages;
    private ProgressBar progressBar;
    private LinearLayout emptyState, composerBar;
    private TextInputEditText etMessage;
    private MaterialButton btnSend;

    private final List<ChatMessage> messageItems = new ArrayList<>();
    private ChatMessageAdapter adapter;

    private String bookingId = "";
    private String providerId = "";
    private String providerFirebaseUid = "";
    private String providerName = "";

    private String currentFirebaseUid = "";
    private String currentUserId = "";
    private String currentUserRole = "user";
    private String currentUserName = "You";

    private String otherPartyId = "";
    private String otherPartyRole = "";
    private String otherPartyName = "";

    private Booking activeBooking;
    private boolean chatReady;
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (chatReady) {
                loadMessages(false);
                main.postDelayed(this, 3000L);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);

        bindViews();
        setupToolbar();
        setupRecycler();
        readIntent();
        resolveChatContext();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatReady) {
            loadMessages(false);
            main.removeCallbacks(refreshRunnable);
            main.postDelayed(refreshRunnable, 3000L);
        }
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarMessages);
        tvChatTitle = findViewById(R.id.tvChatTitle);
        tvChatSubtitle = findViewById(R.id.tvChatSubtitle);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        rvMessages = findViewById(R.id.rvMessages);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        composerBar = findViewById(R.id.composerBar);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        if (btnSend != null) btnSend.setOnClickListener(v -> sendMessage());
    }

    private void setupToolbar() {
        if (toolbar == null) return;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Messages");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
    }

    private void readIntent() {
        Intent i = getIntent();
        if (i == null) return;
        bookingId = safe(i.getStringExtra(EXTRA_BOOKING_ID));
        providerId = safe(i.getStringExtra(EXTRA_PROVIDER_ID));
        providerFirebaseUid = safe(i.getStringExtra(EXTRA_PROVIDER_UID));
        providerName = safe(i.getStringExtra(EXTRA_PROVIDER_NAME));
        currentFirebaseUid = safe(i.getStringExtra(EXTRA_USER_UID));
    }

    private void resolveChatContext() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            toast("Please log in to chat");
            finish();
            return;
        }
        if (currentFirebaseUid.isEmpty()) currentFirebaseUid = user.getUid();

        showLoading(true);
        new Thread(() -> {
            try {
                Participant current = resolveCurrentParticipant(currentFirebaseUid);
                if (current == null) {
                    current = new Participant();
                    current.id = currentFirebaseUid;
                    current.name = user.getDisplayName();
                    current.role = "user";
                }
                currentUserId = safe(current.id).isEmpty() ? currentFirebaseUid : safe(current.id);
                currentUserRole = safe(current.role).isEmpty() ? "user" : current.role;
                currentUserName = safe(current.name).isEmpty() ? "You" : current.name;

                Booking booking = resolveBooking(current);
                if (booking == null) {
                    main.post(() -> {
                        showLoading(false);
                        setComposerEnabled(false);
                        tvChatStatus.setText("Chat available after booking is accepted");
                        
                        Intent i = getIntent();
                        String ctxTitle = i != null ? i.getStringExtra(EXTRA_CONTEXT_TITLE) : "";
                        String ctxBody = i != null ? i.getStringExtra(EXTRA_CONTEXT_BODY) : "";
                        
                        if (!TextUtils.isEmpty(ctxTitle)) {
                            tvChatTitle.setText(ctxTitle);
                            String ctxSub = i.getStringExtra(EXTRA_CONTEXT_SUBTITLE);
                            if (!TextUtils.isEmpty(ctxSub)) tvChatSubtitle.setText(ctxSub);
                        }

                        if (!TextUtils.isEmpty(ctxBody)) {
                            tvEmptyState.setText(ctxBody);
                        } else {
                            tvEmptyState.setText("No eligible booking found for this conversation.");
                        }
                        
                        emptyState.setVisibility(View.VISIBLE);
                        rvMessages.setVisibility(View.GONE);
                    });
                    return;
                }

                activeBooking = booking;
                bookingId = safe(booking.id);

                if ("provider".equalsIgnoreCase(currentUserRole)) {
                    otherPartyId = safe(booking.customerId);
                    otherPartyRole = "user";
                    otherPartyName = safe(booking.customerName);
                } else {
                    otherPartyId = safe(booking.providerId);
                    otherPartyRole = "provider";
                    otherPartyName = safe(safe(booking.providerName).isEmpty() ? providerName : booking.providerName);
                }

                if (otherPartyName.isEmpty()) {
                    otherPartyName = "Chat";
                }

                chatReady = true;
                main.post(() -> {
                    showLoading(false);
                    bindHeader();
                    setComposerEnabled(isChatAllowed(booking.status));
                    if (!isChatAllowed(booking.status)) {
                        tvChatStatus.setText("Chat is available only for accepted/ongoing bookings");
                    }
                    loadMessages(true);
                    main.removeCallbacks(refreshRunnable);
                    main.postDelayed(refreshRunnable, 3000L);
                });
            } catch (Exception e) {
                Log.e(TAG, "resolveChatContext: " + e.getMessage(), e);
                main.post(() -> {
                    showLoading(false);
                    tvEmptyState.setText("Unable to open chat.");
                    emptyState.setVisibility(View.VISIBLE);
                    rvMessages.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void bindHeader() {
        if (tvChatTitle != null) tvChatTitle.setText(otherPartyName.isEmpty() ? "Chat" : otherPartyName);
        if (tvChatSubtitle != null) {
            String subtitle = "Booking #" + (bookingId.isEmpty() ? "-" : bookingId.substring(0, Math.min(8, bookingId.length())));
            if (activeBooking != null && !safe(activeBooking.serviceName).isEmpty()) {
                subtitle = safe(activeBooking.serviceName) + " • " + subtitle;
            }
            tvChatSubtitle.setText(subtitle);
        }
        if (tvChatStatus != null) {
            tvChatStatus.setText("Connected • " + currentUserRole + " → " + otherPartyRole);
        }
        adapter = new ChatMessageAdapter(this, currentUserId, currentUserRole);
        rvMessages.setAdapter(adapter);
    }

    private void setComposerEnabled(boolean enabled) {
        if (composerBar != null) composerBar.setEnabled(enabled);
        if (etMessage != null) etMessage.setEnabled(enabled);
        if (btnSend != null) btnSend.setEnabled(enabled);
    }

    private void loadMessages(boolean scrollToBottom) {
        if (bookingId.isEmpty()) return;
        new Thread(() -> {
            try {
                String url = SUPABASE_URL + "/rest/v1/messages?booking_id=eq." + encode(bookingId)
                        + "&select=*&order=created_at.asc";

                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() != 200) {
                        Log.e(TAG, "loadMessages HTTP " + r.code() + " | " + body);
                        return;
                    }

                    JSONArray arr = new JSONArray(body);
                    List<ChatMessage> list = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        ChatMessage msg = ChatMessage.fromJson(arr.getJSONObject(i));
                        if (msg != null) list.add(msg);
                    }

                    main.post(() -> {
                        if (adapter == null) {
                            adapter = new ChatMessageAdapter(this, currentUserId, currentUserRole);
                            rvMessages.setAdapter(adapter);
                        }
                        adapter.setItems(list);
                        boolean empty = list.isEmpty();
                        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                        rvMessages.setVisibility(empty ? View.GONE : View.VISIBLE);
                        if (tvEmptyState != null) {
                            tvEmptyState.setText(empty ? "No messages yet. Say hello." : "");
                        }
                        if (scrollToBottom && !empty) {
                            rvMessages.scrollToPosition(list.size() - 1);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "loadMessages: " + e.getMessage(), e);
            }
        }).start();
    }

    private void sendMessage() {
        if (!chatReady || activeBooking == null || bookingId.isEmpty()) return;
        if (!isChatAllowed(activeBooking.status)) {
            toast("Chat is available only after booking is accepted");
            return;
        }

        String text = etMessage != null && etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (text.isEmpty()) return;
        if (currentUserId.isEmpty() || otherPartyId.isEmpty()) {
            toast("Chat is not ready yet");
            return;
        }

        setComposerEnabled(false);
        showLoading(true);
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("booking_id", bookingId);
                body.put("sender_id", currentUserId);
                body.put("receiver_id", otherPartyId);
                body.put("sender_type", currentUserRole);
                body.put("message", text);

                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/messages")
                        .post(RequestBody.create(body.toString(), JSON))
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Prefer", "return=minimal")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String resp = r.body() != null ? r.body().string() : "";
                    if (r.code() == 200 || r.code() == 201 || r.code() == 204) {
                        main.post(() -> {
                            if (etMessage != null) etMessage.setText("");
                            setComposerEnabled(true);
                            showLoading(false);
                            loadMessages(true);
                        });
                    } else {
                        Log.e(TAG, "sendMessage HTTP " + r.code() + " | " + resp);
                        main.post(() -> {
                            setComposerEnabled(true);
                            showLoading(false);
                            toast("Failed to send message");
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage: " + e.getMessage(), e);
                main.post(() -> {
                    setComposerEnabled(true);
                    showLoading(false);
                    toast("Failed to send message");
                });
            }
        }).start();
    }

    private Participant resolveCurrentParticipant(String firebaseUid) {
        Participant provider = fetchProviderParticipant(firebaseUid);
        if (provider != null) return provider;
        Participant user = fetchUserParticipant(firebaseUid);
        if (user != null) return user;
        return null;
    }

    private Participant fetchProviderParticipant(String firebaseUid) {
        try {
            String url = SUPABASE_URL + "/rest/v1/provider_profiles?firebase_uid=eq." + encode(firebaseUid)
                    + "&select=id,business_name,owner_name&limit=1";
            JSONObject row = fetchSingle(url);
            if (row == null) return null;
            Participant p = new Participant();
            p.id = safe(row.optString("id", ""));
            p.name = safe(row.optString("business_name", row.optString("owner_name", "Provider")));
            p.role = "provider";
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private Participant fetchUserParticipant(String firebaseUid) {
        try {
            String url = SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + encode(firebaseUid)
                    + "&select=id,name&limit=1";
            JSONObject row = fetchSingle(url);
            if (row == null) return null;
            Participant p = new Participant();
            p.id = safe(row.optString("id", ""));
            p.name = safe(row.optString("name", "User"));
            p.role = "user";
            return p;
        } catch (Exception e) {
            return null;
        }
    }

    private Booking resolveBooking(Participant current) {
        String currentUserIdResolved = current == null ? "" : safe(current.id);
        String currentUid = safe(currentFirebaseUid);
        if (!bookingId.isEmpty()) {
            Booking byId = fetchBookingById(bookingId);
            if (byId != null && isBookingAllowed(byId.status)) return byId;
        }

        if (providerId.isEmpty() && providerFirebaseUid.isEmpty()) return null;

        String providerFilter = !providerId.isEmpty() ? providerId : providerFirebaseUid;
        String customerOr;
        if (current != null && "provider".equalsIgnoreCase(current.role)) {
            customerOr = "provider_id=eq." + encode(currentUserIdResolved.isEmpty() ? currentUid : currentUserIdResolved);
        } else if (!currentUserIdResolved.isEmpty() && !currentUid.equals(currentUserIdResolved)) {
            customerOr = "or=(customer_id.eq." + encode(currentUid) + ",customer_id.eq." + encode(currentUserIdResolved) + ")";
        } else {
            customerOr = "customer_id=eq." + encode(currentUserIdResolved.isEmpty() ? currentUid : currentUserIdResolved);
        }

        try {
            String url = SUPABASE_URL + "/rest/v1/bookings?select=*&provider_id=eq." + encode(providerFilter)
                    + "&" + customerOr
                    + "&status=in.(accepted,price_set,paid,completed)"
                    + "&order=created_at.desc&limit=1";
            return fetchBooking(url);
        } catch (Exception e) {
            Log.e(TAG, "resolveBooking: " + e.getMessage(), e);
            return null;
        }
    }

    private Booking fetchBookingById(String id) {
        try {
            String url = SUPABASE_URL + "/rest/v1/bookings?id=eq." + encode(id) + "&select=*&limit=1";
            return fetchBooking(url);
        } catch (Exception e) {
            return null;
        }
    }

    private Booking fetchBooking(String url) {
        try {
            Request req = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();
            try (Response r = http.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) return null;
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return null;
                return Booking.fromJson(arr.getJSONObject(0));
            }
        } catch (Exception e) {
            return null;
        }
    }

    private JSONObject fetchSingle(String url) {
        try {
            Request req = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();
            try (Response r = http.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) return null;
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return null;
                return arr.getJSONObject(0);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isChatAllowed(String status) {
        return isBookingAllowed(status);
    }

    private boolean isBookingAllowed(String status) {
        String s = safe(status).toLowerCase();
        return "accepted".equals(s) || "price_set".equals(s) || "paid".equals(s) || "completed".equals(s);
    }

    private void showLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ignored) {
            return value == null ? "" : value;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static class Participant {
        String id;
        String name;
        String role;
    }
}
