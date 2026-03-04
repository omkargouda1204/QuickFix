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

    public SearchResult(String id, String name, String subtitle, String city, String type) {
        this.id       = id;
        this.name     = name;
        this.subtitle = subtitle;
        this.city     = city;
        this.type     = type;
    }
}

