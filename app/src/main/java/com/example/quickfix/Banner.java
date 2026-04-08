package com.example.quickfix;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for a promotional banner displayed on the home screen.
 * Synced with Supabase 'banners' table schema.
 */
public class Banner {
    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("image_url")
    public String imageUrl;       // Supabase Storage public URL

    @SerializedName("redirect_type")
    public String redirectType;   // "service" | "category" | "external"

    @SerializedName("redirect_id")
    public String redirectId;     // category/service id or URL

    @SerializedName("start_date")
    public String startDate;      // ISO date string (YYYY-MM-DD)

    @SerializedName("expiry_date")
    public String expiryDate;     // ISO date string (YYYY-MM-DD)

    @SerializedName(value = "is_active", alternate = {"active"})
    public boolean isActive;

    @SerializedName("priority")
    public int priority;          // lower = higher priority

    @SerializedName("status")
    public String status;         // "active" | "inactive" | "expired"

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("deleted_at")
    public String deletedAt;      // For soft deletes (null if active)

    @SerializedName(value = "created_by", alternate = {"created_by_uid", "created_by_firebase_uid"})
    public String createdBy;

    @SerializedName(value = "created_by_uid", alternate = {"created_by_firebase_uid"})
    public String createdByUid;

    @SerializedName("created_by_email")
    public String createdByEmail;

    public Banner() {}

    public Banner(String id, String title, String imageUrl, String redirectType,
                  String redirectId, String startDate, String expiryDate,
                  boolean isActive, int priority, String status, String createdAt,
                  String updatedAt, String deletedAt) {
        this.id           = id;
        this.title        = title;
        this.imageUrl     = imageUrl;
        this.redirectType = redirectType;
        this.redirectId   = redirectId;
        this.startDate    = startDate;
        this.expiryDate   = expiryDate;
        this.isActive     = isActive;
        this.priority     = priority;
        this.status       = status;
        this.createdAt    = createdAt;
        this.updatedAt    = updatedAt;
        this.deletedAt    = deletedAt;
    }
}
