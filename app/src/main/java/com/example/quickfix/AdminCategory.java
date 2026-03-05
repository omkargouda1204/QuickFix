package com.example.quickfix;

/**
 * Data model for a service category (Admin panel — Content Management).
 */
public class AdminCategory {
    public String id;
    public String name;
    public String description;
    public String iconUrl;       // remote URL from Supabase Storage (nullable)
    public int    providerCount; // populated from JOIN query
    public String createdAt;

    public AdminCategory() {}

    public AdminCategory(String id, String name, String description,
                         String iconUrl, int providerCount, String createdAt) {
        this.id            = id;
        this.name          = name;
        this.description   = description;
        this.iconUrl       = iconUrl;
        this.providerCount = providerCount;
        this.createdAt     = createdAt;
    }
}

