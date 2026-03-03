package com.example.quickfix;

import org.json.JSONObject;

/**
 * ProviderService — Model for provider_services table
 */
public class ProviderService {
    public String id;
    public String providerId;
    public String providerName;
    public String serviceName;
    public String category;
    public double price;
    public String description;
    public boolean isAvailable;
    public String createdAt;
    public String updatedAt;

    public static ProviderService fromJson(JSONObject o) {
        if (o == null) return null;
        ProviderService s = new ProviderService();
        s.id = str(o, "id");
        // Try firebase_uid first (new schema), fallback to provider_id (old schema)
        s.providerId = str(o, "firebase_uid");
        if (s.providerId.isEmpty()) {
            s.providerId = str(o, "provider_id");
        }
        s.providerName = str(o, "provider_name");
        s.serviceName = str(o, "service_name");
        s.category = str(o, "category");
        s.price = o.optDouble("price", 0);
        s.description = str(o, "description");
        s.isAvailable = o.optBoolean("is_available", true);
        s.createdAt = str(o, "created_at");
        s.updatedAt = str(o, "updated_at");
        return s;
    }

    private static String str(JSONObject o, String key) {
        if (o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }
}

