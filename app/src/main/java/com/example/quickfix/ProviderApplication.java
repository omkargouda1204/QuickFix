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
    public String address;
    public String city;
    public String experience;
    public String businessImageUrl;
    public String idProofUrl;
    public String status;
    public String submittedAt;
    public String reviewedAt;
    public String reviewedByUid;
    public String rejectionReason;

    /** Parse a single row returned by PostgREST */
    public static ProviderApplication fromJson(JSONObject o) {
        ProviderApplication app = new ProviderApplication();
        app.id               = o.optString("id");
        app.firebaseUid      = o.optString("firebase_uid");
        app.name             = o.optString("name");
        app.email            = o.optString("email");
        app.phone            = o.optString("phone");
        app.businessName     = o.optString("business_name");
        app.category         = o.optString("category");
        app.description      = o.optString("description");
        app.address          = o.optString("address");
        app.city             = o.optString("city");
        app.experience       = o.optString("experience");
        app.businessImageUrl = o.optString("business_image_url");
        app.idProofUrl       = o.optString("id_proof_url");
        app.status           = o.optString("status");
        app.submittedAt      = o.optString("submitted_at");
        app.reviewedAt       = o.optString("reviewed_at");
        app.reviewedByUid    = o.optString("reviewed_by_uid");
        app.rejectionReason  = o.optString("rejection_reason");
        return app;
    }
}
