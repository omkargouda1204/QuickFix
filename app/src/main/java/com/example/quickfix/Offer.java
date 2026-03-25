package com.example.quickfix;

import com.google.gson.annotations.SerializedName;

/**
 * Data model for a discount/offer/coupon shown in the user app.
 */
public class Offer {
    @SerializedName("id")
    public String id;

    @SerializedName("title")
    public String title;

    @SerializedName("description")
    public String description;

    @SerializedName("image_url")
    public String imageUrl;        // optional URL/path for visual card image

    @SerializedName("offer_code")
    public String offerCode;       // e.g. "QUICKFIX20"

    @SerializedName("discount_type")
    public String discountType;    // "percentage" | "fixed"

    @SerializedName("discount_value")
    public double discountValue;

    @SerializedName("min_booking_amount")
    public double minBookingAmount;

    @SerializedName("expiry_date")
    public String expiryDate;      // ISO date string

    @SerializedName("is_active")
    public boolean isActive;

    @SerializedName("created_at")
    public String createdAt;

    public Offer() {}

    public Offer(String id, String title, String description, String offerCode,
                 String discountType, double discountValue, double minBookingAmount,
                 String expiryDate, boolean isActive, String createdAt) {
        this.id               = id;
        this.title            = title;
        this.description      = description;
        this.offerCode        = offerCode;
        this.discountType     = discountType;
        this.discountValue    = discountValue;
        this.minBookingAmount = minBookingAmount;
        this.expiryDate       = expiryDate;
        this.isActive         = isActive;
        this.createdAt        = createdAt;
    }
}
