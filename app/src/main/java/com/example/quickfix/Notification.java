package com.example.quickfix;

import org.json.JSONObject;

/**
 * Notification model mapped to public.notifications.
 */
public class Notification {
    public String id;
    public String userId;
    public String receiverId;
    public String receiverRole;
    public String title;
    public String body;
    public String type;
    public String referenceId;
    public boolean isRead;
    public String createdAt;

    public static Notification fromJson(JSONObject o) {
        if (o == null) return null;
        Notification n = new Notification();
        n.id           = s(o, "id");
        n.userId       = s(o, "user_id");
        n.receiverId   = firstNonEmpty(s(o, "receiver_id"), s(o, "user_id"), s(o, "firebase_uid"));
        n.receiverRole = s(o, "receiver_role");
        n.title        = s(o, "title");
        n.body         = firstNonEmpty(s(o, "body"), s(o, "message"));
        n.type         = s(o, "type");
        n.referenceId  = s(o, "reference_id");
        n.isRead       = o.optBoolean("is_read", false);
        n.createdAt    = s(o, "created_at");
        return n;
    }

    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.trim().isEmpty()) return v.trim();
        }
        return "";
    }
}
