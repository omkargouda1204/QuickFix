package com.example.quickfix;

import org.json.JSONObject;

/**
 * ProviderApplication — POJO that mirrors the provider_applications table.
 */
public class ProviderApplication {

    public String id;
    public String firebaseUid;
    public String name;
    public String email;
    public String phone;
    public String businessName;
    public String category;
    public String description;
    public String city;
    public String experience;
    public String status;           // pending | approved | rejected
    public String rejectionReason;
    public String submittedAt;
    public String reviewedAt;
    public String reviewedByUid;

    /** Parse a single row returned by PostgREST */
    public static ProviderApplication fromJson(JSONObject o) {
        ProviderApplication a = new ProviderApplication();
        a.id              = o.optString("id");
        a.firebaseUid     = o.optString("firebase_uid");
        a.name            = o.optString("name");
        a.email           = o.optString("email");
        a.phone           = o.optString("phone");
        a.businessName    = o.optString("business_name");
        a.category        = o.optString("category");
        a.description     = o.optString("description");
        a.city            = o.optString("city");
        a.experience      = o.optString("experience");
        a.status          = o.optString("status", "pending");
        a.rejectionReason = o.optString("rejection_reason");
        a.submittedAt     = o.optString("submitted_at");
        a.reviewedAt      = o.optString("reviewed_at");
        a.reviewedByUid   = o.optString("reviewed_by_uid");
        return a;
    }
}

