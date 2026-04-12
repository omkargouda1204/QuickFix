package com.example.quickfix;

import org.json.JSONObject;

public class ChatMessage {
    public String id;
    public String bookingId;
    public String senderId;
    public String receiverId;
    public String senderType;
    public String message;
    public String createdAt;
    public boolean isRead;

    public static ChatMessage fromJson(JSONObject o) {
        if (o == null) return null;
        ChatMessage m = new ChatMessage();
        m.id = s(o, "id");
        m.bookingId = s(o, "booking_id");
        m.senderId = s(o, "sender_id");
        m.receiverId = s(o, "receiver_id");
        m.senderType = s(o, "sender_type");
        if (m.senderType.isEmpty()) {
            m.senderType = "user";
        }
        m.message = s(o, "message");
        m.createdAt = s(o, "created_at");
        m.isRead = o.optBoolean("is_read", false);
        return m;
    }

    private static String s(JSONObject o, String key) {
        if (o == null || o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equalsIgnoreCase(v) ? "" : v.trim();
    }
}

