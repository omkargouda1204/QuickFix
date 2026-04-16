package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ChatThreadsAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChatThreadsActivity extends BaseActivity implements ChatThreadsAdapter.ThreadClickListener {

    private static final String TAG = "ChatThreadsActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private RecyclerView rvThreads;
    private TextView tvEmpty;
    private ProgressBar progress;
    private SwipeRefreshLayout swipeThreads;

    private ChatThreadsAdapter adapter;

    private String firebaseUid = "";
    private String currentUserId = "";
    private String currentRole = "user";
    private final Map<String, String> nameCache = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_threads);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        firebaseUid = safe(user.getUid());

        bindViews();
        setupToolbar();
        setupRecycler();
        resolveIdentityAndLoad();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!currentUserId.isEmpty()) {
            loadThreads();
        }
    }

    private void bindViews() {
        rvThreads = findViewById(R.id.rvThreads);
        tvEmpty = findViewById(R.id.tvThreadsEmpty);
        progress = findViewById(R.id.progressThreads);
        swipeThreads = findViewById(R.id.swipeThreads);
        if (swipeThreads != null) {
            swipeThreads.setColorSchemeResources(R.color.primary);
            swipeThreads.setOnRefreshListener(this::loadThreads);
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbarThreads);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void setupRecycler() {
        adapter = new ChatThreadsAdapter(this);
        rvThreads.setLayoutManager(new LinearLayoutManager(this));
        rvThreads.setAdapter(adapter);
    }

    private void resolveIdentityAndLoad() {
        showLoading(true);
        new Thread(() -> {
            try {
                JSONObject providerRow = fetchFirst(SUPABASE_URL + "/rest/v1/provider_profiles?firebase_uid=eq." + encode(firebaseUid)
                        + "&select=id,business_name&limit=1");
                if (providerRow != null) {
                    String pid = safe(providerRow.optString("id", ""));
                    if (!pid.isEmpty()) {
                        currentUserId = pid;
                        currentRole = "provider";
                    }
                }

                if (currentUserId.isEmpty()) {
                String url = SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + encode(firebaseUid)
                        + "&select=id,role,is_admin,is_service_provider&limit=1";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();
                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200) {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() > 0) {
                            JSONObject o = arr.getJSONObject(0);
                            currentUserId = safe(o.optString("id", firebaseUid));
                            currentRole = normalizeRole(o);
                        }
                    }
                }
                }
            } catch (Exception e) {
                Log.w(TAG, "resolveIdentity failed", e);
            }
            if (currentUserId.isEmpty()) currentUserId = firebaseUid;
            main.post(this::loadThreads);
        }).start();
    }

    private String normalizeRole(JSONObject o) {
        String role = safe(o.optString("role", "")).toLowerCase(Locale.US);
        if ("provider".equals(role) || "admin".equals(role) || "user".equals(role)) return role;
        if (o.optBoolean("is_admin", false)) return "admin";
        if (o.optBoolean("is_service_provider", false)) return "provider";
        return "user";
    }

    private void loadThreads() {
        showLoading(true);
        new Thread(() -> {
            List<ChatThread> out = new ArrayList<>();
            try {
                String uid = encode(currentUserId);
                String url = SUPABASE_URL + "/rest/v1/messages?select=*&or=(sender_id.eq." + uid
                        + ",receiver_id.eq." + uid + ")&order=created_at.desc&limit=500";

                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() != 200) {
                        Log.e(TAG, "loadThreads HTTP " + r.code() + " | " + body);
                    } else {
                        JSONArray arr = new JSONArray(body);
                        Map<String, ChatThread> map = new LinkedHashMap<>();
                        for (int i = 0; i < arr.length(); i++) {
                            ChatMessage m = ChatMessage.fromJson(arr.getJSONObject(i));
                            if (m == null) continue;

                            String key = threadKey(m);
                            ChatThread t = map.get(key);
                            if (t == null) {
                                t = new ChatThread();
                                t.bookingId = safe(m.bookingId);
                                t.chatType = safe(m.chatType).isEmpty() ? (t.bookingId.isEmpty() ? "pre_booking" : "booking") : safe(m.chatType);
                                t.lastMessage = safe(m.message);
                                t.lastMessageAt = safe(m.createdAt);
                                bindCounterparty(t, m);
                                map.put(key, t);
                            }
                            if (isUnreadForCurrent(m)) t.unreadCount++;
                        }

                        out.addAll(map.values());
                        for (ChatThread t : out) {
                            if (t.otherPartyName == null || t.otherPartyName.trim().isEmpty()) {
                                t.otherPartyName = resolveName(t.otherPartyId, t.otherPartyRole);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "loadThreads failed", e);
            }

            main.post(() -> {
                showLoading(false);
                adapter.setItems(out);
                boolean empty = out.isEmpty();
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                rvThreads.setVisibility(empty ? View.GONE : View.VISIBLE);
            });
        }).start();
    }

    private void bindCounterparty(ChatThread t, ChatMessage m) {
        boolean mine = currentUserId.equals(safe(m.senderId));
        String otherId = mine ? safe(m.receiverId) : safe(m.senderId);
        String senderType = safe(m.senderType).toLowerCase(Locale.US);
        String otherRole;
        if (mine) {
            otherRole = "provider".equals(currentRole) ? "user" : "provider";
        } else {
            otherRole = senderType.isEmpty() ? ("provider".equals(currentRole) ? "user" : "provider") : senderType;
        }

        t.otherPartyId = otherId;
        t.otherPartyRole = otherRole;
        t.otherPartyName = nameCache.get(otherId);
    }

    private String resolveName(String userId, String role) {
        String key = safe(userId);
        if (key.isEmpty()) return "Chat";
        String cached = nameCache.get(key);
        if (cached != null) return cached;

        String name = "provider".equalsIgnoreCase(role)
                ? fetchProviderNameById(key)
                : fetchUserNameById(key);
        if (name.isEmpty()) name = "provider".equalsIgnoreCase(role) ? "Provider" : "User";
        nameCache.put(key, name);
        return name;
    }

    private String fetchProviderNameById(String id) {
        try {
            String url = SUPABASE_URL + "/rest/v1/provider_profiles?id=eq." + encode(id)
                    + "&select=business_name,owner_name&limit=1";
            JSONObject row = fetchFirst(url);
            if (row == null) return "";
            String n = safe(row.optString("business_name", ""));
            if (n.isEmpty()) n = safe(row.optString("owner_name", ""));
            return n;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String fetchUserNameById(String id) {
        try {
            String url = SUPABASE_URL + "/rest/v1/users?id=eq." + encode(id)
                    + "&select=name&limit=1";
            JSONObject row = fetchFirst(url);
            if (row == null) return "";
            return safe(row.optString("name", ""));
        } catch (Exception ignored) {
            return "";
        }
    }

    private JSONObject fetchFirst(String url) {
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
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isUnreadForCurrent(ChatMessage m) {
        return currentUserId.equals(safe(m.receiverId)) && !m.isSeen;
    }

    private String threadKey(ChatMessage m) {
        String booking = safe(m.bookingId);
        if (!booking.isEmpty()) return "booking:" + booking;
        String a = safe(m.senderId);
        String b = safe(m.receiverId);
        if (a.compareTo(b) < 0) return "pre:" + a + ":" + b;
        return "pre:" + b + ":" + a;
    }

    @Override
    public void onThreadClick(ChatThread thread) {
        Intent i = new Intent(this, MessagesActivity.class);
        i.putExtra(MessagesActivity.EXTRA_BOOKING_ID, safe(thread.bookingId));
        i.putExtra(MessagesActivity.EXTRA_PROVIDER_ID,
                "provider".equalsIgnoreCase(thread.otherPartyRole) ? safe(thread.otherPartyId) : "");
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ID, safe(thread.otherPartyId));
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ROLE, safe(thread.otherPartyRole));
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_NAME, safe(thread.otherPartyName));
        i.putExtra(MessagesActivity.EXTRA_USER_UID, firebaseUid);
        startActivity(i);
    }

    private void showLoading(boolean show) {
        if (swipeThreads != null && swipeThreads.isRefreshing()) {
            if (!show) swipeThreads.setRefreshing(false);
            return;
        }
        if (progress != null) progress.setVisibility(show ? View.VISIBLE : View.GONE);
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
}

