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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.quickfix.adapters.ChatMessageAdapter;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    public static final String EXTRA_OTHER_PARTY_ID = "other_party_id";
    public static final String EXTRA_OTHER_PARTY_ROLE = "other_party_role";
    public static final String EXTRA_OTHER_PARTY_NAME = "other_party_name";
    public static final String EXTRA_CONTEXT_TITLE = "context_title";
    public static final String EXTRA_CONTEXT_SUBTITLE = "context_subtitle";
    public static final String EXTRA_CONTEXT_BODY = "context_body";

    private static final String TAG = "MessagesActivity";
    private static final int PREBOOKING_LIMIT = 5;
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
    private SwipeRefreshLayout swipeMessages;
    private ProgressBar progressBar;
    private LinearLayout emptyState, composerBar;
    private TextInputEditText etMessage;
    private MaterialButton btnSend;

    private ChatMessageAdapter adapter;

    private String bookingId = "";
    private String providerId = "";
    private String providerFirebaseUid = "";
    private String providerName = "";
    private String otherPartyId = "";
    private String otherPartyRole = "";
    private String otherPartyName = "";
    private String currentFirebaseUid = "";

    private String currentUserId = "";
    private String currentUserRole = "user";
    private String currentUserName = "You";

    private Booking activeBooking;
    private boolean isBookingChat = false;
    private boolean chatReady = false;
    private String lastTailMessageId = "";
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!chatReady) return;
            loadMessages(false);
            main.postDelayed(this, 2000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        bindViews();
        setupToolbar();
        readIntent();
        resolveChatContext();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (chatReady) {
            loadMessages(false);
            main.removeCallbacks(refreshRunnable);
            main.postDelayed(refreshRunnable, 2000L);
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
        swipeMessages = findViewById(R.id.swipeMessages);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        composerBar = findViewById(R.id.composerBar);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        rvMessages.setLayoutManager(lm);
        btnSend.setOnClickListener(v -> sendMessage());
        if (swipeMessages != null) {
            swipeMessages.setColorSchemeResources(R.color.primary);
            swipeMessages.setOnRefreshListener(() -> {
                if (adapter != null) adapter.setItems(new ArrayList<>());
                loadMessages(false);
            });
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

    private void readIntent() {
        Intent i = getIntent();
        if (i == null) return;
        bookingId = safe(i.getStringExtra(EXTRA_BOOKING_ID));
        providerId = safe(i.getStringExtra(EXTRA_PROVIDER_ID));
        providerFirebaseUid = safe(i.getStringExtra(EXTRA_PROVIDER_UID));
        providerName = safe(i.getStringExtra(EXTRA_PROVIDER_NAME));
        currentFirebaseUid = safe(i.getStringExtra(EXTRA_USER_UID));
        otherPartyId = safe(i.getStringExtra(EXTRA_OTHER_PARTY_ID));
        otherPartyRole = safe(i.getStringExtra(EXTRA_OTHER_PARTY_ROLE));
        otherPartyName = safe(i.getStringExtra(EXTRA_OTHER_PARTY_NAME));
    }

    private void resolveChatContext() {
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            toast("Please log in to chat");
            finish();
            return;
        }
        if (currentFirebaseUid.isEmpty()) currentFirebaseUid = firebaseUser.getUid();

        showLoading(true);
        new Thread(() -> {
            try {
                Participant current = resolveCurrentParticipant(currentFirebaseUid);
                if (current == null) {
                    current = new Participant();
                    current.id = currentFirebaseUid;
                    // Use UserSession as single source of truth
                    current.name = !UserSession.name.isEmpty() ? UserSession.name
                            : safe(firebaseUser.getDisplayName());
                    current.role = "user";
                }
                currentUserId = safe(current.id).isEmpty() ? currentFirebaseUid : safe(current.id);
                currentUserRole = safe(current.role).isEmpty() ? "user" : safe(current.role).toLowerCase(Locale.US);
                // Prefer UserSession name if available
                String sessionName = UserSession.name;
                currentUserName = !sessionName.isEmpty() ? sessionName
                        : (safe(current.name).isEmpty() ? "You" : safe(current.name));

                Booking booking = resolveBooking(current);
                if (booking != null) {
                    activeBooking = booking;
                    isBookingChat = true;
                    bookingId = safe(booking.id);
                    if ("provider".equals(currentUserRole)) {
                        otherPartyId = safe(booking.customerId);
                        otherPartyRole = "user";
                        if (otherPartyName.isEmpty()) otherPartyName = safe(booking.customerName);
                    } else {
                        otherPartyId = safe(booking.providerId);
                        otherPartyRole = "provider";
                        if (otherPartyName.isEmpty()) {
                            otherPartyName = safe(booking.providerName).isEmpty() ? providerName : safe(booking.providerName);
                        }
                    }
                } else {
                    isBookingChat = false;
                    if (otherPartyId.isEmpty()) {
                        if ("user".equals(currentUserRole)) {
                            otherPartyId = !providerId.isEmpty() ? providerId : providerFirebaseUid;
                            otherPartyRole = "provider";
                            if (otherPartyName.isEmpty()) otherPartyName = providerName;
                        }
                    }
                }

                if (otherPartyId.isEmpty()) {
                    main.post(() -> {
                        showLoading(false);
                        setComposerEnabled(false);
                        Intent i = getIntent();
                        String ctxTitle = i != null ? safe(i.getStringExtra(EXTRA_CONTEXT_TITLE)) : "";
                        String ctxSub = i != null ? safe(i.getStringExtra(EXTRA_CONTEXT_SUBTITLE)) : "";
                        String ctxBody = i != null ? safe(i.getStringExtra(EXTRA_CONTEXT_BODY)) : "";
                        if (!ctxTitle.isEmpty()) tvChatTitle.setText(ctxTitle);
                        if (!ctxSub.isEmpty()) tvChatSubtitle.setText(ctxSub);
                        tvEmptyState.setText(ctxBody.isEmpty()
                                ? "Open chat from provider profile or booking list."
                                : ctxBody);
                        emptyState.setVisibility(View.VISIBLE);
                        rvMessages.setVisibility(View.GONE);
                    });
                    return;
                }

                if (otherPartyRole.isEmpty()) {
                    otherPartyRole = "provider".equals(currentUserRole) ? "user" : "provider";
                }
                if (otherPartyName.isEmpty()) {
                    otherPartyName = "provider".equals(otherPartyRole) ? "Provider" : "User";
                }

                chatReady = true;
                main.post(() -> {
                    showLoading(false);
                    bindHeader();
                    setComposerEnabled(isComposerAllowed());
                    loadMessages(true);
                    main.removeCallbacks(refreshRunnable);
                    main.postDelayed(refreshRunnable, 2000L);
                });
            } catch (Exception e) {
                Log.e(TAG, "resolveChatContext failed", e);
                main.post(() -> {
                    showLoading(false);
                    setComposerEnabled(false);
                    tvEmptyState.setText("Unable to open chat");
                    emptyState.setVisibility(View.VISIBLE);
                    rvMessages.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void bindHeader() {
        tvChatTitle.setText(otherPartyName);
        if (isBookingChat) {
            String sub = "Booking #" + (bookingId.isEmpty() ? "-" : bookingId.substring(0, Math.min(8, bookingId.length())));
            if (activeBooking != null && !safe(activeBooking.serviceName).isEmpty()) {
                sub = activeBooking.serviceName + " • " + sub;
            }
            tvChatSubtitle.setText(sub);
            String status = activeBooking == null ? "" : safe(activeBooking.status).toLowerCase(Locale.US);
            if ("completed".equals(status)) {
                tvChatStatus.setText("Chat closed after service completion");
                btnSend.setVisibility(View.GONE);
                etMessage.setEnabled(false);
            } else {
                tvChatStatus.setText("Booking chat");
                btnSend.setVisibility(View.VISIBLE);
                etMessage.setEnabled(true);
            }
        } else {
            tvChatSubtitle.setText("Pre-booking chat");
            tvChatStatus.setText("Limited to " + PREBOOKING_LIMIT + " user messages before booking");
            btnSend.setVisibility(View.VISIBLE);
        }
        if (adapter == null) {
            adapter = new ChatMessageAdapter(this, currentUserId, currentUserRole);
            rvMessages.setAdapter(adapter);
        }
    }

    private boolean isComposerAllowed() {
        if (!isBookingChat) return true;
        return activeBooking != null && isBookingWritable(activeBooking.status);
    }

    private void setComposerEnabled(boolean enabled) {
        composerBar.setEnabled(enabled);
        etMessage.setEnabled(enabled);
        btnSend.setEnabled(enabled);
    }

    private void loadMessages(boolean scrollToBottom) {
        if (!chatReady) return;
        new Thread(() -> {
            try {
                String url = buildMessageLoadUrl();
                Request req = new Request.Builder().url(url)
                        .addHeader("apikey", ANON_KEY)
                        .addHeader("Authorization", "Bearer " + ANON_KEY)
                        .addHeader("Accept", "application/json")
                        .build();
                List<ChatMessage> list = new ArrayList<>();
                try (Response r = http.newCall(req).execute()) {
                    String body = r.body() != null ? r.body().string() : "[]";
                    if (r.code() != 200) {
                        Log.e(TAG, "loadMessages HTTP " + r.code() + " | " + body);
                        return;
                    }
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        ChatMessage m = ChatMessage.fromJson(arr.getJSONObject(i));
                        if (m != null) list.add(m);
                    }
                }

                markIncomingAsSeen();

                main.post(() -> {
                    adapter.setItems(list);
                    boolean empty = list.isEmpty();
                    emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
                    rvMessages.setVisibility(empty ? View.GONE : View.VISIBLE);
                    tvEmptyState.setText(empty ? "No messages yet. Say hello." : "");
                    boolean shouldScroll = scrollToBottom || shouldAutoScrollToBottom(list);
                    if (!empty && shouldScroll) {
                        rvMessages.scrollToPosition(list.size() - 1);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "loadMessages failed", e);
            }
        }).start();
    }

    private String buildMessageLoadUrl() {
        String a = encode(currentUserId);
        String b = encode(otherPartyId);
        String pairFilter = "or=(and(sender_id.eq." + a + ",receiver_id.eq." + b + "),and(sender_id.eq." + b + ",receiver_id.eq." + a + "))";
        if (isBookingChat) {
            return SUPABASE_URL + "/rest/v1/messages?chat_type=eq.booking&booking_id=eq." + encode(bookingId)
                    + "&" + pairFilter
                    + "&select=*&order=created_at.asc,id.asc";
        }
        return SUPABASE_URL + "/rest/v1/messages?chat_type=eq.pre_booking"
                + "&" + pairFilter
                + "&select=*&order=created_at.asc,id.asc";
    }

    private void sendMessage() {
        if (!chatReady) return;
        String text = etMessage.getText() == null ? "" : etMessage.getText().toString().trim();
        if (text.isEmpty()) return;
        if (currentUserId.isEmpty() || otherPartyId.isEmpty()) {
            toast("Chat not ready");
            return;
        }

        if (isBookingChat && (activeBooking == null || !isBookingWritable(activeBooking.status))) {
            toast("Chat closed after service completion");
            return;
        }
        if (isBookingChat && bookingId.isEmpty()) {
            toast("Booking chat unavailable");
            return;
        }

        setComposerEnabled(false);
        showLoading(true);
        new Thread(() -> {
            try {
                if (!isBookingChat && "user".equals(currentUserRole)) {
                    int sent = countPreBookingMessagesByUser();
                    if (sent >= PREBOOKING_LIMIT) {
                        main.post(() -> {
                            setComposerEnabled(true);
                            showLoading(false);
                            toast("Message limit reached. Please book service to continue.");
                        });
                        return;
                    }
                }

                JSONObject body = new JSONObject();
                if (isBookingChat) {
                    body.put("booking_id", bookingId);
                    body.put("chat_type", "booking");
                    body.put("is_prebooking", false);
                } else {
                    body.put("booking_id", JSONObject.NULL);
                    body.put("chat_type", "pre_booking");
                    body.put("is_prebooking", true);
                }
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
                    int code = r.code();
                    if (!(code == 200 || code == 201 || code == 204)
                            && resp.toLowerCase(Locale.US).contains("is_prebooking")) {
                        body.remove("is_prebooking");
                        Request fallbackReq = new Request.Builder()
                                .url(SUPABASE_URL + "/rest/v1/messages")
                                .post(RequestBody.create(body.toString(), JSON))
                                .addHeader("apikey", ANON_KEY)
                                .addHeader("Authorization", "Bearer " + ANON_KEY)
                                .addHeader("Content-Type", "application/json")
                                .addHeader("Prefer", "return=minimal")
                                .build();
                        try (Response fallbackResp = http.newCall(fallbackReq).execute()) {
                            resp = fallbackResp.body() != null ? fallbackResp.body().string() : "";
                            code = fallbackResp.code();
                        }
                    }
                    if (code == 200 || code == 201 || code == 204) {
                        FcmNotificationHelper.notifyChatMessage(
                                otherPartyId,
                                otherPartyRole,
                                currentUserName,
                                text,
                                isBookingChat ? bookingId : null
                        );
                        main.post(() -> {
                            etMessage.setText("");
                            setComposerEnabled(true);
                            showLoading(false);
                            toast(getString(R.string.toast_message_sent));
                            loadMessages(true);
                        });
                    } else {
                        Log.e(TAG, "sendMessage HTTP " + code + " | " + resp);
                        final String errorResp = resp;
                        main.post(() -> {
                            setComposerEnabled(true);
                            showLoading(false);
                            if (errorResp.contains("Please book service to continue") || errorResp.contains("Message limit reached")) {
                                toast("Message limit reached. Please book service to continue.");
                            } else if (errorResp.contains("new row violates row-level security") || errorResp.contains("42501")) {
                                toast("Message blocked by server policy. Ask admin to enable message insert policy.");
                            } else {
                                toast(getString(R.string.toast_message_send_failed));
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "sendMessage failed", e);
                main.post(() -> {
                    setComposerEnabled(true);
                    showLoading(false);
                    toast(isNetworkIssue(e)
                            ? getString(R.string.toast_no_internet)
                            : getString(R.string.toast_message_send_failed));
                });
            }
        }).start();
    }

    private boolean shouldAutoScrollToBottom(List<ChatMessage> list) {
        if (list == null || list.isEmpty()) {
            lastTailMessageId = "";
            return false;
        }
        ChatMessage tail = list.get(list.size() - 1);
        String nextTailId = safe(tail == null ? "" : tail.id);
        if (lastTailMessageId.isEmpty()) {
            lastTailMessageId = nextTailId;
            return false;
        }
        boolean isNewTail = !nextTailId.isEmpty() && !nextTailId.equals(lastTailMessageId);
        if (isNewTail) {
            lastTailMessageId = nextTailId;
            return isNearBottom();
        }
        return false;
    }

    private boolean isNearBottom() {
        if (adapter == null) return true;
        RecyclerView.LayoutManager lm = rvMessages.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager)) return true;
        int total = adapter.getItemCount();
        if (total <= 0) return true;
        int lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
        return total - 1 - lastVisible <= 2;
    }

    private boolean isNetworkIssue(Throwable t) {
        return t instanceof java.net.UnknownHostException
                || t instanceof java.net.SocketTimeoutException
                || t instanceof java.net.ConnectException;
    }

    private int countPreBookingMessagesByUser() {
        try {
            String url = SUPABASE_URL + "/rest/v1/messages?chat_type=eq.pre_booking"
                    + "&sender_type=eq.user&sender_id=eq." + encode(currentUserId)
                    + "&receiver_id=eq." + encode(otherPartyId)
                    + "&select=id&limit=6";
            Request req = new Request.Builder().url(url)
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();
            try (Response r = http.newCall(req).execute()) {
                String body = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) return 0;
                return new JSONArray(body).length();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    private void markIncomingAsSeen() {
        try {
            String filter;
            if (isBookingChat) {
                filter = "booking_id=eq." + encode(bookingId)
                        + "&chat_type=eq.booking"
                        + "&sender_id=eq." + encode(otherPartyId);
            } else {
                filter = "chat_type=eq.pre_booking&sender_id=eq." + encode(otherPartyId);
            }
            boolean marked = patchSeenFlag(filter, "is_seen");
            if (!marked) {
                patchSeenFlag(filter, "is_read");
            }
        } catch (Exception ignored) {
        }
    }

    private boolean patchSeenFlag(String filter, String seenColumn) {
        try {
            String url = SUPABASE_URL + "/rest/v1/messages?receiver_id=eq." + encode(currentUserId)
                    + "&" + seenColumn + "=eq.false&" + filter;
            JSONObject body = new JSONObject();
            body.put(seenColumn, true);
            Request req = new Request.Builder().url(url)
                    .patch(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "return=minimal")
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                return resp.code() == 200 || resp.code() == 204;
            }
        } catch (Exception ignored) {
            return false;
        }
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
        } catch (Exception ignored) {
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
            String n = safe(row.optString("full_name", ""));
            if (n.isEmpty()) n = safe(row.optString("name", "User"));
            p.name = n;
            p.role = "user";
            return p;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Booking resolveBooking(Participant current) {
        String currentResolvedId = current == null ? "" : safe(current.id);
        String currentUid = safe(currentFirebaseUid);
        if (!bookingId.isEmpty()) {
            Booking byId = fetchBookingById(bookingId);
            if (byId != null && isBookingAllowed(byId.status)) return byId;
        }

        if ("provider".equalsIgnoreCase(currentUserRole)) return null;
        String providerFilter = !providerId.isEmpty() ? providerId : providerFirebaseUid;
        if (providerFilter.isEmpty()) return null;

        String customerFilter;
        if (!currentResolvedId.isEmpty() && !currentResolvedId.equals(currentUid)) {
            customerFilter = "or=(customer_id.eq." + encode(currentResolvedId) + ",customer_id.eq." + encode(currentUid) + ")";
        } else {
            customerFilter = "customer_id=eq." + encode(currentResolvedId.isEmpty() ? currentUid : currentResolvedId);
        }

        String url = SUPABASE_URL + "/rest/v1/bookings?select=*&provider_id=eq." + encode(providerFilter)
                + "&" + customerFilter
                + "&status=in.(accepted,price_set,paid,completed)&order=created_at.desc&limit=1";
        return fetchBooking(url);
    }

    private Booking fetchBookingById(String id) {
        return fetchBooking(SUPABASE_URL + "/rest/v1/bookings?id=eq." + encode(id) + "&select=*&limit=1");
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isBookingAllowed(String status) {
        String s = safe(status).toLowerCase(Locale.US);
        return "accepted".equals(s) || "price_set".equals(s) || "paid".equals(s) || "completed".equals(s);
    }

    private boolean isBookingWritable(String status) {
        String s = safe(status).toLowerCase(Locale.US);
        return "accepted".equals(s) || "price_set".equals(s) || "paid".equals(s);
    }

    private void showLoading(boolean loading) {
        if (swipeMessages != null && swipeMessages.isRefreshing()) {
            if (!loading) swipeMessages.setRefreshing(false);
            return;
        }
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
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
