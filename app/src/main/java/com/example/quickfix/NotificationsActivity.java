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

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.NotificationsAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
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

/**
 * NotificationsActivity — Displays user notifications from Supabase notifications table.
 * Supports mark as read, delete, pull-to-refresh.
 */
public class NotificationsActivity extends BaseActivity
        implements NotificationsAdapter.NotificationActionListener {

    private static final String TAG      = "Notifications";
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;

    private RecyclerView          recyclerView;
    private NotificationsAdapter  adapter;
    private SwipeRefreshLayout    swipeRefresh;
    private ProgressBar           progressBar;
    private LinearLayout          emptyState;
    private TextView              tvUnreadCount;

    private String firebaseUid;
    private String currentUserId;
    private String currentUserRole = "user";
    private List<Notification> allNotifications = new ArrayList<>();

    private final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }
        firebaseUid = user.getUid();

        bindViews();
        setupRecyclerView();
        setupSwipeRefresh();
        resolveUserIdentityAndLoad();
    }

    private void resolveUserIdentityAndLoad() {
        showLoading(true);
        new Thread(() -> {
            UserIdentity identity = resolveUserIdentity(firebaseUid);
            currentUserId = identity.userId;
            currentUserRole = identity.role;
            MAIN.post(this::loadNotifications);
        }).start();
    }

    private UserIdentity resolveUserIdentity(String uid) {
        UserIdentity fallback = new UserIdentity(uid, "user");
        if (uid == null || uid.trim().isEmpty()) return fallback;

        try {
            String url = BASE_URL + "/rest/v1/users?firebase_uid=eq."
                    + URLEncoder.encode(uid, StandardCharsets.UTF_8.name())
                    + "&select=id,role,is_admin,is_service_provider,is_user&limit=1";
            Request req = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200 || !body.startsWith("[")) {
                    Log.w(TAG, "resolveUserIdentity HTTP=" + r.code() + " body=" + body);
                    return fallback;
                }
                JSONArray arr = new JSONArray(body);
                if (arr.length() == 0) return fallback;
                JSONObject row = arr.getJSONObject(0);

                String userId = row.optString("id", uid);
                String role = normalizeRole(row);
                return new UserIdentity(userId, role);
            }
        } catch (Exception e) {
            Log.w(TAG, "resolveUserIdentity failed", e);
            return fallback;
        }
    }

    private String normalizeRole(JSONObject row) {
        String role = row.optString("role", "").trim().toLowerCase();
        if ("admin".equals(role) || "provider".equals(role) || "user".equals(role)) {
            return role;
        }
        if (row.optBoolean("is_admin", false)) return "admin";
        if (row.optBoolean("is_service_provider", false)) return "provider";
        return "user";
    }

    private void bindViews() {
        recyclerView   = findViewById(R.id.recyclerView);
        swipeRefresh   = findViewById(R.id.swipeRefresh);
        progressBar    = findViewById(R.id.progressBar);
        emptyState     = findViewById(R.id.emptyState);
        tvUnreadCount  = findViewById(R.id.tvUnreadCount);
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        
        View btnMarkAll = findViewById(R.id.btnMarkAllRead);
        if (btnMarkAll != null) {
            btnMarkAll.setOnClickListener(v -> markAllRead());
        }
        
        View btnClear = findViewById(R.id.btnClearAllNotifications);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> confirmClearAllNotifications());
        }
    }

    private void setupRecyclerView() {
        adapter = new NotificationsAdapter(this, this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary);
            swipeRefresh.setOnRefreshListener(this::loadNotifications);
        }
    }

    private void loadNotifications() {
        showLoading(true);
        new Thread(() -> {
            try {
                String roleFilter = (currentUserRole == null || currentUserRole.trim().isEmpty())
                        ? "user"
                        : currentUserRole.trim().toLowerCase();
                String resolvedUserId = (currentUserId == null || currentUserId.trim().isEmpty())
                        ? firebaseUid
                        : currentUserId.trim();

                String url = BASE_URL
                        + "/rest/v1/notifications?select=*"
                        + "&or=(user_id.eq." + URLEncoder.encode(resolvedUserId, StandardCharsets.UTF_8.name())
                        + ",receiver_id.eq." + URLEncoder.encode(resolvedUserId, StandardCharsets.UTF_8.name())
                        + ",receiver_role.eq." + URLEncoder.encode(roleFilter, StandardCharsets.UTF_8.name()) + ")"
                        + "&order=created_at.desc";
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                List<Notification> list = new ArrayList<>();
                try (Response r = HTTP.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() == 200 && body.startsWith("[")) {
                        JSONArray arr = new JSONArray(body);
                        for (int i = 0; i < arr.length(); i++) {
                            Notification n = Notification.fromJson(arr.getJSONObject(i));
                            if (n != null) list.add(n);
                        }
                    }
                }

                final List<Notification> finalList = list;
                allNotifications = new ArrayList<>(finalList);
                MAIN.post(() -> {
                    showLoading(false);
                    adapter.setNotifications(finalList);
                    updateUnreadCount();
                    emptyState.setVisibility(finalList.isEmpty() ? View.VISIBLE : View.GONE);
                    recyclerView.setVisibility(finalList.isEmpty() ? View.GONE : View.VISIBLE);
                });
            } catch (Exception e) {
                Log.e(TAG, "ERROR LOADING NOTIFICATIONS", e);
                MAIN.post(() -> {
                    showLoading(false);
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    @Override
    public void onMarkRead(Notification notification) {
        if (notification.isRead) return;
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_read", true);
                String url = BASE_URL + "/rest/v1/notifications?id=eq." + notification.id;
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    MAIN.post(() -> {
                        if (r.code() == 200 || r.code() == 204) {
                            notification.isRead = true;
                            adapter.notifyDataSetChanged();
                            updateUnreadCount();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "onMarkRead: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onDelete(Notification notification) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Notification")
                .setMessage("Remove this notification?")
                .setPositiveButton("Delete", (d, w) -> deleteNotification(notification))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onAcceptEmergency(Notification notification) {
        if (notification.referenceId == null || notification.referenceId.isEmpty()) {
            Toast.makeText(this, "Request ID missing", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, ProviderEmergencyRequestActivity.class);
        intent.putExtra("request_id", notification.referenceId);
        intent.putExtra("quick_action", "accept");
        startActivity(intent);
        onMarkRead(notification);
    }

    private void mirrorToBookings(String requestId) {
        new Thread(() -> {
            try {
                // Fetch emergency request details
                String fetchUrl = BASE_URL + "/rest/v1/emergency_requests?id=eq." + requestId + "&select=*&limit=1";
                Request fetchReq = new Request.Builder().url(fetchUrl)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .build();

                try (Response fr = HTTP.newCall(fetchReq).execute()) {
                    String body = fr.body() != null ? fr.body().string() : "[]";
                    JSONArray arr = new JSONArray(body);
                    if (arr.length() > 0) {
                        JSONObject req = arr.getJSONObject(0);
                        
                        // Create booking
                        JSONObject booking = new JSONObject();
                        booking.put("customer_id", req.optString("user_id"));
                        booking.put("provider_id", currentUserId);
                        booking.put("service_name", "Emergency - " + req.optString("category"));
                        booking.put("status", "accepted");
                        booking.put("address", req.optString("location_label"));
                        booking.put("emergency_request_id", requestId);
                        booking.put("created_at", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new java.util.Date()));

                        Request insertReq = new Request.Builder()
                                .url(BASE_URL + "/rest/v1/bookings")
                                .post(RequestBody.create(booking.toString(), MediaType.get("application/json")))
                                .addHeader("apikey", ANON_KEY)
                                .addHeader("Authorization", "Bearer " + ANON_KEY)
                                .addHeader("Content-Type", "application/json")
                                .build();
                        
                        try (Response ir = HTTP.newCall(insertReq).execute()) {
                            if (ir.isSuccessful()) {
                                // Successfully accepted and mirrored!
                                // Now cleanup other providers' pending bookings for this emergency
                                cleanupOtherEmergencyBookings(requestId);
                            } else {
                                Log.w(TAG, "Booking insertion failed: " + ir.code());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "mirrorToBookings failed: " + e.getMessage());
            }
        }).start();
    }

    private void cleanupOtherEmergencyBookings(String emergencyId) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/rest/v1/bookings"
                        + "?emergency_request_id=eq." + emergencyId
                        + "&provider_id=neq." + currentUserId;

                Request request = new Request.Builder()
                        .url(url)
                        .delete()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .build();

                try (Response response = HTTP.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        Log.w(TAG, "cleanupOtherEmergencyBookings failed: " + response.code());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "cleanupOtherEmergencyBookings error: " + e.getMessage());
            }
        }).start();
    }

    @Override
    public void onRejectEmergency(Notification notification) {
        if (notification.referenceId != null && !notification.referenceId.trim().isEmpty()) {
            Intent intent = new Intent(this, ProviderEmergencyRequestActivity.class);
            intent.putExtra("request_id", notification.referenceId.trim());
            intent.putExtra("quick_action", "reject");
            startActivity(intent);
        }
        deleteNotification(notification);
    }

    private void deleteNotification(Notification notification) {
        new Thread(() -> {
            try {
                String url = BASE_URL + "/rest/v1/notifications?id=eq." + notification.id;
                Request req = new Request.Builder().url(url)
                        .delete()
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    MAIN.post(() -> {
                        if (r.code() == 200 || r.code() == 204) {
                            allNotifications.remove(notification);
                            adapter.setNotifications(new ArrayList<>(allNotifications));
                            updateUnreadCount();
                            emptyState.setVisibility(allNotifications.isEmpty() ? View.VISIBLE : View.GONE);
                            recyclerView.setVisibility(allNotifications.isEmpty() ? View.GONE : View.VISIBLE);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "deleteNotification: " + e.getMessage());
            }
        }).start();
    }

    private void markAllRead() {
        new Thread(() -> {
            try {
                String resolvedUserId = (currentUserId == null || currentUserId.trim().isEmpty())
                        ? firebaseUid
                        : currentUserId.trim();
                JSONObject body = new JSONObject();
                body.put("is_read", true);
                String encodedUserId = URLEncoder.encode(resolvedUserId, StandardCharsets.UTF_8.name());
                String url = BASE_URL + "/rest/v1/notifications?or=(receiver_id.eq."
                        + encodedUserId
                        + ",user_id.eq."
                        + encodedUserId
                        + ")&is_read=eq.false";
                Request req = new Request.Builder().url(url)
                        .patch(RequestBody.create(body.toString(), MediaType.get("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();
                try (Response r = HTTP.newCall(req).execute()) {
                    MAIN.post(() -> {
                        if (r.code() == 200 || r.code() == 204) {
                            for (Notification n : allNotifications) {
                                n.isRead = true;
                            }
                            adapter.notifyDataSetChanged();
                            updateUnreadCount();
                            Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "markAllRead: " + e.getMessage());
            }
        }).start();
    }

    private void confirmClearAllNotifications() {
        if (allNotifications == null || allNotifications.isEmpty()) {
            Toast.makeText(this, "No notifications to clear", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Clear all notifications")
                .setMessage("This will permanently remove all notifications currently shown.")
                .setPositiveButton("Clear all", (d, w) -> clearAllNotifications())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAllNotifications() {
        final List<String> ids = new ArrayList<>();
        for (Notification n : allNotifications) {
            if (n != null && n.id != null) ids.add(n.id.trim());
        }

        if (ids.isEmpty()) return;

        new Thread(() -> {
            try {
                StringBuilder in = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) in.append(",");
                    in.append(ids.get(i));
                }

                String url = BASE_URL + "/rest/v1/notifications?id=in.(" + in + ")";
                Request req = new Request.Builder().url(url)
                        .delete()
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .build();

                try (Response r = HTTP.newCall(req).execute()) {
                    final int code = r.code();
                    MAIN.post(() -> {
                        if (code == 200 || code == 204) {
                            allNotifications.clear();
                            adapter.setNotifications(new ArrayList<>());
                            updateUnreadCount();
                            emptyState.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "clearAllNotifications failed", e);
            }
        }).start();
    }

    private void updateUnreadCount() {
        if (tvUnreadCount == null) return;
        long unread = allNotifications.stream().filter(n -> !n.isRead).count();
        tvUnreadCount.setText(unread > 0 ? unread + " unread" : "No unread messages");
    }

    private void showLoading(boolean show) {
        if (swipeRefresh != null && swipeRefresh.isRefreshing()) {
            if (!show) swipeRefresh.setRefreshing(false);
            return;
        }
        if (progressBar != null) progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private static final class UserIdentity {
        final String userId;
        final String role;
        UserIdentity(String userId, String role) {
            this.userId = userId;
            this.role = role;
        }
    }
}
