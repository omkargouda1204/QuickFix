package com.example.quickfix;

/**
 * Data model for a service (Admin panel — Service Management).
 */
public class AdminService {
    public String  id;
    public String  name;
    public String  categoryId;
    public String  categoryName;
    public String  description;
    public double  basePrice;
    public String  iconUrl;       // remote URL (nullable)
    public boolean isActive;
    public String  createdAt;

    public AdminService() {}

    public AdminService(String id, String name, String categoryId, String categoryName,
                        String description, double basePrice,
                        String iconUrl, boolean isActive, String createdAt) {
        this.id           = id;
        this.name         = name;
        this.categoryId   = categoryId;
        this.categoryName = categoryName;
        this.description  = description;
        this.basePrice    = basePrice;
        this.iconUrl      = iconUrl;
        this.isActive     = isActive;
        this.createdAt    = createdAt;
    }
}

