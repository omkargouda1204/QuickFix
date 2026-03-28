package com.example.quickfix;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AdminNotificationsActivity — displays all notifications from the `admin_notifications` table.
 * Supports mark-as-read, filter by type, pull-to-refresh.
 */
public class AdminNotificationsActivity extends BaseActivity {

    private static final String TAG          = "AdminNotif";
    private static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY     = BuildConfig.SUPABASE_ANON_KEY;

    private static final String TABLE_NOTIFICATIONS = "admin_notifications";

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Views
    private RecyclerView       rvNotifications;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar        progressBar;
    private LinearLayout       emptyState;
    private TextView           tvNotifCount;
    private ChipGroup          chipGroupFilter;

    // Data
    private NotifAdapter              adapter;
    private final List<NotifItem>     allItems    = new ArrayList<>();
    private final List<NotifItem>     filtered    = new ArrayList<>();
    private String                    currentFilter = "all";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        super.setContentView(R.layout.activity_admin_notifications);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) { finish(); return; }

        bindViews();
        setupRecyclerView();
        setupChips();
        loadNotifications();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    private void bindViews() {
        rvNotifications = findViewById(R.id.rvNotifications);
        swipeRefresh    = findViewById(R.id.swipeRefresh);
        progressBar     = findViewById(R.id.progressBar);
        emptyState      = findViewById(R.id.emptyState);
        tvNotifCount    = findViewById(R.id.tvNotifCount);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);

        ImageButton btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        ImageButton btnRefresh = findViewById(R.id.btnRefresh);
        if (btnRefresh != null) btnRefresh.setOnClickListener(v -> loadNotifications());

        ImageButton btnMarkAll = findViewById(R.id.btnMarkAllRead);
        if (btnMarkAll != null) btnMarkAll.setOnClickListener(v -> markAllRead());

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.primary);
            swipeRefresh.setOnRefreshListener(this::loadNotifications);
        }
    }

    private void setupRecyclerView() {
        adapter = new NotifAdapter(filtered, item -> markItemRead(item));
        if (rvNotifications != null) {
            rvNotifications.setLayoutManager(new LinearLayoutManager(this));
            rvNotifications.setAdapter(adapter);
        }
    }

    private void setupChips() {
        if (chipGroupFilter == null) return;
        chipGroupFilter.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipAll) currentFilter = "all";
            else if (id == R.id.chipUnread) currentFilter = "unread";
            else if (id == R.id.chipUsers) currentFilter = "users";
            else if (id == R.id.chipProvider) currentFilter = "providers";
            else if (id == R.id.chipOffers) currentFilter = "offer";
            applyFilter();
        });
    }

    // ── LOAD NOTIFICATIONS ────────────────────────────────────────────────
    private void loadNotifications() {
        setLoading(true);

        String url = SUPABASE_URL + "/rest/v1/" + TABLE_NOTIFICATIONS
                + "?select=id,title,message,type,is_read,created_at"
                + "&order=created_at.desc"
                + "&limit=300";

        Log.d(TAG, "loadNotifications url=" + url);

        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();

                try (Response resp = http.newCall(req).execute()) {
                    int code = resp.code();
                    String body = resp.body() != null ? resp.body().string() : "[]";
                    Log.d(TAG, "loadNotifications HTTP=" + code + " bodyLen=" + body.length());
                    main.post(() -> {
                        setLoading(false);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        if (code == 200) {
                            parseNotifications(body, TABLE_NOTIFICATIONS);
                        } else {
                            Log.e(TAG, "ERROR LOADING NOTIFICATIONS: HTTP " + code + " " + body);
                            Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
                            showEmptyState();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "ERROR LOADING NOTIFICATIONS", e);
                main.post(() -> {
                    setLoading(false);
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
                    showEmptyState();
                });
            }
        }).start();
    }

    private void parseNotifications(String json, String sourceTable) {
        allItems.clear();
        try {
            JSONArray arr = new JSONArray(json);
            Log.d(TAG, "parseNotifications(" + sourceTable + "): " + arr.length() + " rows");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                NotifItem item  = new NotifItem();
                item.id         = o.optString("id");
                item.title      = o.optString("title", "Notification");
                item.message    = o.optString("message", "");
                item.type       = o.optString("type", "general");
                item.isRead     = o.optBoolean("is_read", false);
                item.createdAt  = o.optString("created_at", "");
                item.sourceTable = sourceTable;
                item.receiverRole = "admin";
                item.receiverId = "";
                allItems.add(item);
            }
        } catch (Exception e) {
            Log.e(TAG, "parseNotifications", e);
        }
        applyFilter();
    }

    private void applyFilter() {
        filtered.clear();
        for (NotifItem item : allItems) {
            if ("all".equals(currentFilter)) {
                filtered.add(item);
            } else if ("unread".equals(currentFilter) && !item.isRead) {
                filtered.add(item);
            } else if ("users".equals(currentFilter)
                    && ("register".equalsIgnoreCase(item.type)
                    || "user_registration".equalsIgnoreCase(item.type)
                    || "welcome".equalsIgnoreCase(item.type))) {
                filtered.add(item);
            } else if ("providers".equals(currentFilter)
                    && item.type != null
                    && item.type.toLowerCase(Locale.US).contains("provider")) {
                filtered.add(item);
            } else if ("offer".equals(currentFilter)
                    && "offer".equalsIgnoreCase(item.type)) {
                filtered.add(item);
            }
        }

        int unreadCount = 0;
        for (NotifItem n : allItems) if (!n.isRead) unreadCount++;

        if (tvNotifCount != null) {
            tvNotifCount.setText(unreadCount + " unread · " + allItems.size() + " total");
        }

        Log.d(TAG, "NOTIFICATIONS COUNT: " + allItems.size() + " filtered=" + filtered.size());
        adapter.notifyDataSetChanged();

        if (filtered.isEmpty()) showEmptyState();
        else showList();
    }

    // ── MARK READ ─────────────────────────────────────────────────────────
    private void markItemRead(NotifItem item) {
        if (item == null || item.isRead || item.id == null || item.id.isEmpty()) return;
        final String table = TABLE_NOTIFICATIONS;

        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_read", true);
                Request req = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/" + table + "?id=eq." + item.id)
                        .patch(RequestBody.create(body.toString(),
                                okhttp3.MediaType.get("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String rb = r.body() != null ? r.body().string() : "";
                    if (r.isSuccessful()) {
                        main.post(() -> {
                            item.isRead = true;
                            adapter.notifyDataSetChanged();
                            applyFilter();
                        });
                    } else {
                        Log.e(TAG, "markItemRead failed [" + table + "] HTTP " + r.code() + ": " + rb);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "markItemRead", e);
            }
        }).start();
    }

    private void markAllRead() {
        new Thread(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("is_read", true);

                final String targetTable = TABLE_NOTIFICATIONS;

                String url = SUPABASE_URL + "/rest/v1/" + targetTable + "?is_read=eq.false";

                Request req = new Request.Builder()
                        .url(url)
                        .patch(RequestBody.create(body.toString(),
                                okhttp3.MediaType.get("application/json")))
                        .addHeader("apikey",        ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Content-Type",  "application/json")
                        .addHeader("Prefer",        "return=minimal")
                        .build();

                try (Response r = http.newCall(req).execute()) {
                    String rb = r.body() != null ? r.body().string() : "";
                    main.post(() -> {
                        if (r.isSuccessful()) {
                            for (NotifItem n : allItems) n.isRead = true;
                            adapter.notifyDataSetChanged();
                            applyFilter();
                            Toast.makeText(this, "All marked as read", Toast.LENGTH_SHORT).show();
                        } else {
                            Log.e(TAG, "markAllRead failed [" + targetTable + "] HTTP " + r.code() + ": " + rb);
                            Toast.makeText(this, "Could not mark notifications", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "markAllRead", e);
                main.post(() -> Toast.makeText(this, "Could not mark notifications", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // ── UI helpers ────────────────────────────────────────────────────────
    private void setLoading(boolean loading) {
        if (progressBar != null) progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showEmptyState() {
        if (emptyState       != null) emptyState.setVisibility(View.VISIBLE);
        if (rvNotifications  != null) rvNotifications.setVisibility(View.GONE);
    }

    private void showList() {
        if (emptyState      != null) emptyState.setVisibility(View.GONE);
        if (rvNotifications != null) rvNotifications.setVisibility(View.VISIBLE);
    }

    // ── Data model ────────────────────────────────────────────────────────
    public static class NotifItem {
        public String id, title, message, type, createdAt, receiverRole, receiverId;
        public boolean isRead;
        public String sourceTable;
    }

    // ── Adapter ───────────────────────────────────────────────────────────
    static class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.VH> {

        interface OnMarkRead { void onMark(NotifItem item); }

        private final List<NotifItem> items;
        private final OnMarkRead      listener;

        NotifAdapter(List<NotifItem> items, OnMarkRead listener) {
            this.items    = items;
            this.listener = listener;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_notification, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            NotifItem item = items.get(pos);

            if (h.tvTitle   != null) h.tvTitle.setText(item.title != null ? item.title : "");
            if (h.tvMessage != null) h.tvMessage.setText(item.message != null ? item.message : "");
            if (h.tvTime    != null) h.tvTime.setText(formatTime(item.createdAt));

            // Unread dot
            if (h.unreadDot != null)
                h.unreadDot.setVisibility(item.isRead ? View.INVISIBLE : View.VISIBLE);

            // Icon tint by type
            if (h.ivIcon != null) {
                int iconRes = R.drawable.ic_notification;
                int tint    = 0xFF1E88E5;
                if ("provider_application".equals(item.type)) {
                    iconRes = R.drawable.ic_approval;
                    tint    = 0xFF43A047;
                } else if ("offer".equals(item.type)) {
                    iconRes = R.drawable.ic_offers;
                    tint    = 0xFFFF7043;
                } else if ("provider_approved".equals(item.type)) {
                    iconRes = R.drawable.ic_check;
                    tint    = 0xFF00897B;
                } else if ("provider_rejected".equals(item.type)) {
                    tint    = 0xFFE53935;
                }
                if (h.ivIcon instanceof ImageView) {
                    ImageView icon = (ImageView) h.ivIcon;
                    icon.setImageResource(iconRes);
                    icon.setColorFilter(tint);
                }
            }

            // Card alpha — read vs unread
            h.itemView.setAlpha(item.isRead ? 0.7f : 1.0f);

            if (h.btnMarkRead != null)
                h.btnMarkRead.setOnClickListener(v -> { if (listener != null) listener.onMark(item); });

            h.itemView.setOnClickListener(v -> { if (listener != null) listener.onMark(item); });
        }

        @Override public int getItemCount() { return items.size(); }

        private String formatTime(String iso) {
            if (iso == null || iso.isEmpty()) return "";
            try {
                String clean = iso.replace("T", " ").replace("Z", "");
                if (clean.length() >= 19) clean = clean.substring(0, 19);
                SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                Date d = in.parse(clean);
                if (d == null) return iso;
                long diff = System.currentTimeMillis() - d.getTime();
                long mins = diff / (60 * 1000);
                if (mins < 1) return "Just now";
                if (mins < 60) return mins + "m ago";
                long hrs = mins / 60;
                if (hrs < 24) return hrs + "h ago";
                long days = hrs / 24;
                if (days < 7) return days + "d ago";
                return new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(d);
            } catch (Exception ignored) {
                return iso;
            }
        }

        static class VH extends RecyclerView.ViewHolder {
            View ivIcon;
            View unreadDot;
            TextView tvTitle, tvMessage, tvTime;
            ImageButton btnMarkRead;

            VH(View v) {
                super(v);
                ivIcon      = v.findViewById(R.id.ivNotifIcon);
                unreadDot   = v.findViewById(R.id.unreadDot);
                tvTitle     = v.findViewById(R.id.tvNotifTitle);
                tvMessage   = v.findViewById(R.id.tvNotifMessage);
                tvTime      = v.findViewById(R.id.tvNotifTime);
                btnMarkRead = v.findViewById(R.id.btnMarkRead);
            }
        }
    }
}
