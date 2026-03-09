package com.example.quickfix;

/**
 * Data model for a promotional banner displayed on the home screen.
 */
public class Banner {
    public String  id;
    public String  title;
    public String  imageUrl;       // Supabase Storage public URL
    public String  redirectType;   // "service" | "category" | "external"
    public String  redirectId;     // category/service id or URL
    public String  startDate;      // ISO date string
    public String  expiryDate;     // ISO date string
    public boolean isActive;
    public String  status;         // DB column: "active" | "inactive" (string)
    public int     priority;       // lower = higher priority
    public String  createdAt;

    /** Returns true if either is_active=true OR status='active'. */
    public boolean isEffectivelyActive() {
        if (status != null && !status.isEmpty()) return "active".equalsIgnoreCase(status);
        return isActive;
    }

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

