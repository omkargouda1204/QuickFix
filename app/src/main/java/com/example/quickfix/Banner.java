package com.example.quickfix;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for a promotional banner displayed on the home screen.
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
    public String startDate;      // ISO date string

    @SerializedName("expiry_date")
    public String expiryDate;     // ISO date string

    @SerializedName("is_active")
    public boolean isActive;

    @SerializedName("priority")
    public int priority;       // lower = higher priority

    @SerializedName("created_at")
    public String createdAt;

    public Banner() {}

    public Banner(String id, String title, String imageUrl, String redirectType,
                  String redirectId, String startDate, String expiryDate,
                  boolean isActive, int priority, String createdAt) {
        this.id           = id;
        this.title        = title;
        this.imageUrl     = imageUrl;
        this.redirectType = redirectType;
        this.redirectId   = redirectId;
        this.startDate    = startDate;
        this.expiryDate   = expiryDate;
        this.isActive     = isActive;
        this.priority     = priority;
        this.createdAt    = createdAt;
    }
}
