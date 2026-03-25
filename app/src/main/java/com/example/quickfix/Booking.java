package com.example.quickfix;

import org.json.JSONObject;

/**
 * Booking — Model for bookings table
 */
public class Booking {
    public String id;
    public String customerId;
    public String customerName;
    public String customerPhone;
    public String providerId;
    public String providerName;
    public String serviceId;
    public String serviceName;
    public String category;
    public String bookingDate;
    public String bookingTime;
    public String address;
    public String city;
    public double price;
    public String status; // pending, accepted, completed, cancelled
    public String createdAt;
    public String completedAt;
    public String description;

    public static Booking fromJson(JSONObject o) {
        if (o == null) return null;
        Booking b = new Booking();
        b.id = s(o, "id");
        b.customerId = s(o, "customer_id");
        b.customerName = s(o, "customer_name");
        b.customerPhone = s(o, "customer_phone");
        // Current schema: provider_id. Keep firebase_uid as backward-compatible fallback.
        b.providerId = s(o, "provider_id");
        if (b.providerId.isEmpty()) {
            b.providerId = s(o, "firebase_uid");
        }
        b.providerName = s(o, "provider_name");
        b.serviceId = s(o, "service_id");
        b.serviceName = s(o, "service_name");
        b.category = s(o, "category");
        b.bookingDate = s(o, "booking_date");
        b.bookingTime = s(o, "booking_time");
        b.address = s(o, "address");
        b.city = s(o, "city");
        b.price = o.optDouble("price", 0);
        b.status = s(o, "status");
        b.createdAt = s(o, "created_at");
        b.completedAt = s(o, "completed_at");
        b.description = s(o, "description");
        return b;
    }

    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}
