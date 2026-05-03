package com.example.quickfix;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ChatListActivity extends BaseActivity {

    private static final String TAG = "ChatListActivity";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    private MaterialToolbar toolbar;
    private RecyclerView rvThreads;
    private SwipeRefreshLayout swipeThreads;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ChatThreadsAdapter adapter;

    private String currentFirebaseUid = "";
    private String currentUserId = "";
    private String currentUserRole = "user";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_threads);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        currentFirebaseUid = user.getUid();

        bindViews();
        setupToolbar();
        resolveUserIdentityAndLoad();
    }

    private void bindViews() {
        toolbar = findViewById(R.id.toolbarThreads);
        rvThreads = findViewById(R.id.rvThreads);
        swipeThreads = findViewById(R.id.swipeThreads);
        progressBar = findViewById(R.id.progressThreads);
        tvEmpty = findViewById(R.id.tvThreadsEmpty);

        rvThreads.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChatThreadsAdapter(this::onThreadClicked);
        rvThreads.setAdapter(adapter);

        if (swipeThreads != null) {
            swipeThreads.setColorSchemeResources(R.color.primary);
            swipeThreads.setOnRefreshListener(this::loadThreads);
        }
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Messages");
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void resolveUserIdentityAndLoad() {
        showLoading(true);
        new Thread(() -> {
            try {
                // Try resolving as provider first
                String providerUrl = SUPABASE_URL + "/rest/v1/provider_profiles?firebase_uid=eq." + currentFirebaseUid + "&select=id&limit=1";
                String bodyP = fetch(providerUrl);
                JSONArray arrP = new JSONArray(bodyP);
                if (arrP.length() > 0) {
                    currentUserId = arrP.getJSONObject(0).optString("id", "");
                    currentUserRole = "provider";
                } else {
                    // Try resolving as user
                    String userUrl = SUPABASE_URL + "/rest/v1/users?firebase_uid=eq." + currentFirebaseUid + "&select=id&limit=1";
                    String bodyU = fetch(userUrl);
                    JSONArray arrU = new JSONArray(bodyU);
                    if (arrU.length() > 0) {
                        currentUserId = arrU.getJSONObject(0).optString("id", "");
                        currentUserRole = "user";
                    } else {
                        currentUserId = currentFirebaseUid;
                        currentUserRole = "user";
                    }
                }
                main.post(this::loadThreads);
            } catch (Exception e) {
                Log.e(TAG, "resolveUserIdentity failed", e);
                main.post(() -> {
                    showLoading(false);
                    tvEmpty.setText("Unable to load messages");
                    tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void loadThreads() {
        showLoading(true);
        new Thread(() -> {
            try {
                String encodedId = java.net.URLEncoder.encode(currentUserId, "UTF-8");
                String url = SUPABASE_URL + "/rest/v1/messages?or=(sender_id.eq." + encodedId + ",receiver_id.eq." + encodedId + ")"
                        + "&order=created_at.desc";
                
                String body = fetch(url);
                JSONArray arr = new JSONArray(body);
                List<ChatMessage> allMessages = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    ChatMessage m = ChatMessage.fromJson(arr.getJSONObject(i));
                    if (m != null) allMessages.add(m);
                }

                Map<String, ChatThread> threadMap = new HashMap<>();
                for (ChatMessage m : allMessages) {
                    String threadKey;
                    boolean isBooking = "booking".equals(m.chatType) && !m.bookingId.isEmpty();
                    if (isBooking) {
                        threadKey = "booking_" + m.bookingId;
                    } else {
                        String otherId = m.senderId.equals(currentUserId) ? m.receiverId : m.senderId;
                        threadKey = "pre_" + otherId;
                    }

                    if (!threadMap.containsKey(threadKey)) {
                        ChatThread t = new ChatThread();
                        t.bookingId = isBooking ? m.bookingId : "";
                        t.chatType = m.chatType;
                        t.otherPartyId = m.senderId.equals(currentUserId) ? m.receiverId : m.senderId;
                        t.lastMessage = m.message;
                        t.lastMessageAt = m.createdAt;
                        t.unreadCount = (!m.isSeen && m.receiverId.equals(currentUserId)) ? 1 : 0;
                        threadMap.put(threadKey, t);
                    } else {
                        ChatThread t = threadMap.get(threadKey);
                        if (!m.isSeen && m.receiverId.equals(currentUserId)) {
                            t.unreadCount++;
                        }
                    }
                }

                List<ChatThread> threads = new ArrayList<>(threadMap.values());
                // Resolve names for other parties
                resolveOtherPartyNames(threads);

                main.post(() -> {
                    showLoading(false);
                    adapter.setItems(threads);
                    tvEmpty.setVisibility(threads.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                Log.e(TAG, "loadThreads failed", e);
                main.post(() -> {
                    showLoading(false);
                    tvEmpty.setText("Failed to load messages");
                    tvEmpty.setVisibility(View.VISIBLE);
                });
            }
        }).start();
    }

    private void resolveOtherPartyNames(List<ChatThread> threads) {
        for (ChatThread t : threads) {
            try {
                if (!t.bookingId.isEmpty()) {
                    // Fetch booking to get service name or other party name
                    String bUrl = SUPABASE_URL + "/rest/v1/bookings?id=eq." + t.bookingId + "&select=service_name,provider_name,customer_name,provider_id,customer_id&limit=1";
                    String bBody = fetch(bUrl);
                    JSONArray bArr = new JSONArray(bBody);
                    if (bArr.length() > 0) {
                        JSONObject bObj = bArr.getJSONObject(0);
                        String sName = bObj.optString("service_name", "");
                        if ("provider".equals(currentUserRole)) {
                            t.otherPartyName = bObj.optString("customer_name", "User");
                        } else {
                            t.otherPartyName = bObj.optString("provider_name", "Provider");
                        }
                        if (!sName.isEmpty()) t.otherPartyName = sName + " (" + t.otherPartyName + ")";
                    }
                }

                if (t.otherPartyName == null || t.otherPartyName.isEmpty()) {
                    // Try fetching from provider_profiles
                    String pUrl = SUPABASE_URL + "/rest/v1/provider_profiles?or=(id.eq." + t.otherPartyId + ",firebase_uid.eq." + t.otherPartyId + ")&select=business_name&limit=1";
                    String pBody = fetch(pUrl);
                    JSONArray pArr = new JSONArray(pBody);
                    if (pArr.length() > 0) {
                        t.otherPartyName = pArr.getJSONObject(0).optString("business_name", "Provider");
                    } else {
                        // Try fetching from users
                        String uUrl = SUPABASE_URL + "/rest/v1/users?or=(id.eq." + t.otherPartyId + ",firebase_uid.eq." + t.otherPartyId + ")&select=full_name,name&limit=1";
                        String uBody = fetch(uUrl);
                        JSONArray uArr = new JSONArray(uBody);
                        if (uArr.length() > 0) {
                            JSONObject uObj = uArr.getJSONObject(0);
                            t.otherPartyName = uObj.optString("full_name", uObj.optString("name", "User"));
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private String fetch(String url) throws Exception {
        Request req = new Request.Builder()
                .url(url)
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();
        try (Response r = http.newCall(req).execute()) {
            return r.body() != null ? r.body().string() : "[]";
        }
    }

    private void onThreadClicked(ChatThread thread) {
        Intent i = new Intent(this, MessagesActivity.class);
        i.putExtra(MessagesActivity.EXTRA_BOOKING_ID, thread.bookingId);
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_ID, thread.otherPartyId);
        i.putExtra(MessagesActivity.EXTRA_OTHER_PARTY_NAME, thread.otherPartyName);
        i.putExtra(MessagesActivity.EXTRA_USER_UID, currentFirebaseUid);
        startActivity(i);
    }

    private void showLoading(boolean loading) {
        if (swipeThreads != null && swipeThreads.isRefreshing()) {
            if (!loading) swipeThreads.setRefreshing(false);
            return;
        }
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
