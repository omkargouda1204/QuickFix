package com.example.quickfix;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Booking — Model for bookings table
 */
public class Booking {
    public String id;
    public String customerId;
    public String customerName;
    public String customerPhone;
    public String customerEmail;
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
    public String paymentStatus;
    public String paymentId;
    public String paymentOrderId;
    public String paymentMode;
    public double paymentAmount;
    public String createdAt;
    public String completedAt;
    public String description;
    public String type;
    public boolean isEmergency;
    public String emergencyRequestId;

    // Optional provider review payload (joined or mapped after fetch)
    public String reviewId;
    public int reviewRating;
    public String reviewComment;
    public String reviewUpdatedAt;

    public static Booking fromJson(JSONObject o) {
        if (o == null) return null;
        Booking b = new Booking();
        b.id = s(o, "id");
        b.customerId = s(o, "customer_id");
        b.customerName = s(o, "customer_name");
        b.customerPhone = s(o, "customer_phone");
        b.customerEmail = s(o, "customer_email");
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
        b.paymentStatus = s(o, "payment_status");
        b.paymentId = s(o, "payment_id");
        b.paymentOrderId = s(o, "payment_order_id");
        if (b.paymentOrderId.isEmpty()) b.paymentOrderId = s(o, "order_id");
        b.paymentMode = s(o, "payment_mode");
        if (b.paymentMode.isEmpty()) b.paymentMode = s(o, "payment_method");
        if (b.paymentMode.isEmpty()) b.paymentMode = s(o, "mode");
        b.paymentAmount = o.optDouble("payment_amount", 0d);
        b.createdAt = s(o, "created_at");
        b.completedAt = s(o, "completed_at");
        b.description = s(o, "description");
        b.type = s(o, "type");
        b.isEmergency = o.optBoolean("is_emergency", false)
                || "emergency".equalsIgnoreCase(b.type)
                || "emergency".equalsIgnoreCase(b.category)
                || "emergency".equalsIgnoreCase(b.serviceName);
        b.emergencyRequestId = s(o, "emergency_request_id");

        // Read review if API includes an embedded relation or flat columns.
        JSONObject reviewObj = null;
        Object nested = o.opt("provider_reviews");
        if (nested instanceof JSONObject) {
            reviewObj = (JSONObject) nested;
        } else if (nested instanceof JSONArray) {
            JSONArray arr = (JSONArray) nested;
            if (arr.length() > 0) reviewObj = arr.optJSONObject(0);
        }

        if (reviewObj != null) {
            b.reviewId = s(reviewObj, "id");
            b.reviewRating = reviewObj.optInt("rating", 0);
            b.reviewComment = s(reviewObj, "comment");
            b.reviewUpdatedAt = s(reviewObj, "updated_at");
            if (b.reviewUpdatedAt.isEmpty()) {
                b.reviewUpdatedAt = s(reviewObj, "created_at");
            }
        } else {
            b.reviewId = s(o, "review_id");
            b.reviewRating = o.optInt("review_rating", 0);
            b.reviewComment = s(o, "review_comment");
            b.reviewUpdatedAt = s(o, "review_updated_at");
        }
        return b;
    }

    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}
