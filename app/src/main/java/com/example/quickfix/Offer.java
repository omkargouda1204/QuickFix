package com.example.quickfix;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for a discount/offer/coupon shown in the user app.
 * Synced with Supabase 'offers' table schema.
 */
public class Offer {
    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("image_url")
    public String imageUrl;

    @SerializedName("offer_code")
    public String offerCode;       // e.g. "QUICKFIX20"

    @SerializedName("discount_type")
    public String discountType;    // "percentage" | "fixed"

    @SerializedName("discount_value")
    public double discountValue;

    @SerializedName("min_booking_amount")
    public double minBookingAmount;

    @SerializedName("expiry_date")
    public String expiryDate;      // ISO date string (YYYY-MM-DD)

    @SerializedName("is_active")
    public boolean isActive;

    @SerializedName("status")
    public String status;          // "active" | "inactive" | "expired"

    @SerializedName("created_at")
    public String createdAt;

    @SerializedName("updated_at")
    public String updatedAt;

    @SerializedName("deleted_at")
    public String deletedAt;       // For soft deletes (null if active)

    public Offer() {}

    public Offer(String id, String title, String description, String offerCode,
                 String discountType, double discountValue, double minBookingAmount,
                 String expiryDate, boolean isActive, String status, String createdAt,
                 String updatedAt, String deletedAt) {
        this.id               = id;
        this.title            = title;
        this.description      = description;
        this.offerCode        = offerCode;
        this.discountType     = discountType;
        this.discountValue    = discountValue;
        this.minBookingAmount = minBookingAmount;
        this.expiryDate       = expiryDate;
        this.isActive         = isActive;
        this.status           = status;
        this.createdAt        = createdAt;
        this.updatedAt        = updatedAt;
        this.deletedAt        = deletedAt;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getOfferCode() {
        return offerCode;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
