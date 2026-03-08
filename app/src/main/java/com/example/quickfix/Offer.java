package com.example.quickfix;

/**
 * Data model for a discount/offer/coupon shown in the user app.
 */
public class Offer {
    public String  id;
    public String  title;
    public String  description;
    public String  offerCode;       // e.g. "QUICKFIX20"
    public String  discountType;    // "percentage" | "fixed"
    public double  discountValue;
    public double  minBookingAmount;
    public String  expiryDate;      // ISO date string
    public boolean isActive;
    public boolean sendNotification; // whether to push FCM on create
    public String  createdAt;

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

