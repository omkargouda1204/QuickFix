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

    /** Parse a single row returned by PostgREST. Never returns null fields — uses "". */
    public static ProviderApplication fromJson(JSONObject o) {
        if (o == null) return new ProviderApplication();
        ProviderApplication app = new ProviderApplication();
        app.id               = s(o, "id");
        app.firebaseUid      = s(o, "firebase_uid");
        app.name             = s(o, "name");
        app.email            = s(o, "email");
        app.phone            = s(o, "phone");
        app.businessName     = s(o, "business_name");
        app.category         = s(o, "category");
        app.description      = s(o, "description");
        app.address          = s(o, "address");
        app.city             = s(o, "city");
        app.experience       = s(o, "experience");
        app.businessImageUrl = s(o, "business_image_url");
        app.idProofUrl       = s(o, "id_proof_url");
        app.status           = s(o, "status");
        app.submittedAt      = s(o, "submitted_at");
        app.reviewedAt       = s(o, "reviewed_at");
        app.reviewedByUid    = s(o, "reviewed_by_uid");
        app.rejectionReason  = s(o, "rejection_reason");
        return app;
    }

    /**
     * Safe string getter: returns "" for null, missing, or literal "null" values.
     * Prevents the string "null" from appearing in the UI.
     */
    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}
