package com.example.quickfix;

import org.json.JSONObject;

/**
 * Notification — Model for notifications table
 */
public class Notification {
    public String id;
    public String userId;
    public String title;
    public String message;
    public String type;       // booking_request, provider_approval, booking_confirmed, offer, emergency
    public boolean isRead;
    public String createdAt;

    public static Notification fromJson(JSONObject o) {
        if (o == null) return null;
        Notification n = new Notification();
        n.id        = s(o, "id");
        n.userId    = s(o, "user_id");
        n.title     = s(o, "title");
        n.message   = s(o, "message");
        n.type      = s(o, "type");
        n.isRead    = o.optBoolean("is_read", false);
        n.createdAt = s(o, "created_at");
        return n;
    }

    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}

