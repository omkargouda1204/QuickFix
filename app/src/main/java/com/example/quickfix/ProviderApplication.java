package com.example.quickfix;

import org.json.JSONObject;

/**
 * ProviderApplication — POJO that mirrors the provider_applications table.
 *


 * Actual DB columns: id, firebase_uid, user_name, user_email, user_phone,
 *             business_name, category, description, address, city, experience_yrs,
 *             business_image_url, id_proof_url, status, terms_accepted,
 *             submitted_at, reviewed_at, reviewed_by_uid, rejection_reason
 */
public class ProviderApplication {

    public String id;
    public String firebaseUid;
    /** Maps DB column: user_id — Supabase users.id (UUID) for the applicant */
    public String userId;
    /** Maps DB column: owner_name */
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
    public String rejectedAt;   // used for 24-hour reapply countdown

    /** Parse a single row returned by PostgREST. Never returns null fields — uses "". */
    public static ProviderApplication fromJson(JSONObject o) {
        if (o == null) return new ProviderApplication();
        ProviderApplication app = new ProviderApplication();
        app.id               = s(o, "id");
        app.firebaseUid      = s(o, "firebase_uid");
        app.userId           = s(o, "user_id");   // Supabase users.id UUID

        // Support both column naming conventions:
        //   Old (ProviderOnboardingActivity): user_name, user_email, experience_yrs
        //   New (ProviderApplicationActivity): owner_name, email, experience
        String ownerName = s(o, "owner_name");
        if (ownerName.isEmpty()) ownerName = s(o, "user_name");   // fallback old column
        if (ownerName.isEmpty()) ownerName = s(o, "name");        // legacy
        app.name = ownerName;

        String email = s(o, "email");
        if (email.isEmpty()) email = s(o, "user_email");          // fallback old column
        app.email = email;

        app.phone        = s(o, "user_phone");
        if (app.phone.isEmpty()) app.phone = s(o, "phone"); // legacy fallback
        app.businessName = s(o, "business_name");
        app.category     = s(o, "category");
        app.description  = s(o, "description");
        app.address      = s(o, "address");
        app.city         = s(o, "city");

        String experience = s(o, "experience");
        if (experience.isEmpty()) {
            // experience_yrs stores an integer — convert to string
            int expYrs = o.optInt("experience_yrs", -1);
            if (expYrs >= 0) experience = String.valueOf(expYrs);
        }
        app.experience = experience;

        app.businessImageUrl = s(o, "business_image_url");
        app.idProofUrl       = s(o, "id_proof_url");
        app.status           = s(o, "status");
        app.submittedAt      = s(o, "submitted_at");
        if (app.submittedAt.isEmpty()) app.submittedAt = s(o, "created_at"); // fallback
        app.reviewedAt       = s(o, "reviewed_at");
        app.reviewedByUid    = s(o, "reviewed_by_uid");

        String rejectionReason = s(o, "rejection_reason");
        if (rejectionReason.isEmpty()) rejectionReason = s(o, "admin_rejection_reason");
        app.rejectionReason = rejectionReason;

        // rejectedAt = reviewed_at when status is rejected
        app.rejectedAt = s(o, "reviewed_at");
        if (app.rejectedAt.isEmpty()) app.rejectedAt = s(o, "rejected_at");

        return app;
    }

    /**
     * Safe string getter — returns "" for null, missing, or literal "null" values.
     */
    private static String s(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}
