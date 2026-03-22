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
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.city = city;
        this.type = type;
        this.rating = Math.max(0f, rating);
        this.distanceKm = distanceKm;
        this.availabilityStatus = availabilityStatus == null ? "" : availabilityStatus.trim();
    }
}
