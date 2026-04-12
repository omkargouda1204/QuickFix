package com.example.quickfix;

/** Unified model for search results (provider or service). */
public class SearchResult {
    public static final String TYPE_PROVIDER = "Provider";
    public static final String TYPE_SERVICE  = "Service";

    public String id;
    public String name;
    public String subtitle;   // category for service, business name for provider
    public String city;
    public String type;       // TYPE_PROVIDER or TYPE_SERVICE
    public float  rating;
    public float  distanceKm;
    public String availabilityStatus;
    public String description;
    public String phone;
    public String profileImage;
    public String status;
    public boolean isVerified;

    public SearchResult(String id, String name, String subtitle, String city, String type) {
        this(id, name, subtitle, city, type, 0f, -1f, "");
    }

    public SearchResult(String id,
                        String name,
                        String subtitle,
                        String city,
                        String type,
                        float rating,
                        float distanceKm,
                        String availabilityStatus) {
        this(id, name, subtitle, city, type, rating, distanceKm, availabilityStatus, "", "", "", "");
    }

    public SearchResult(String id,
                        String name,
                        String subtitle,
                        String city,
                        String type,
                        float rating,
                        float distanceKm,
                        String availabilityStatus,
                        String description,
                        String phone,
                        String profileImage,
                        String status) {
        this(id, name, subtitle, city, type, rating, distanceKm, availabilityStatus, description, phone, profileImage, status, false);
    }

    public SearchResult(String id,
                        String name,
                        String subtitle,
                        String city,
                        String type,
                        float rating,
                        float distanceKm,
                        String availabilityStatus,
                        String description,
                        String phone,
                        String profileImage,
                        String status,
                        boolean isVerified) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.city = city;
        this.type = type;
        this.rating = Math.max(0f, rating);
        this.distanceKm = distanceKm;
        this.availabilityStatus = availabilityStatus == null ? "" : availabilityStatus.trim();
        this.description = description == null ? "" : description.trim();
        this.phone = phone == null ? "" : phone.trim();
        this.profileImage = profileImage == null ? "" : profileImage.trim();
        this.status = status == null ? "" : status.trim();
        this.isVerified = isVerified;
    }
}
