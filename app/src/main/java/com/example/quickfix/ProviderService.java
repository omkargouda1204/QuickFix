package com.example.quickfix;

import org.json.JSONObject;

/**
 * ProviderService — Supports both legacy `provider_services` and current `services` schemas.
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

        // Provider id fallback chain: services.provider_uid -> provider_services.firebase_uid -> provider_id.
        s.providerId = str(o, "provider_uid");
        if (s.providerId.isEmpty()) s.providerId = str(o, "firebase_uid");
        if (s.providerId.isEmpty()) s.providerId = str(o, "provider_id");

        s.providerName = str(o, "provider_name");

        // Service name fallback chain: services.title -> provider_services.service_name.
        s.serviceName = str(o, "title");
        if (s.serviceName.isEmpty()) s.serviceName = str(o, "service_name");

        s.category = str(o, "category");
        s.price = o.optDouble("price", 0);
        s.description = str(o, "description");

        // Availability fallback chain: services.is_active -> provider_services.is_available.
        if (!o.isNull("is_active")) {
            s.isAvailable = o.optBoolean("is_active", true);
        } else {
            s.isAvailable = o.optBoolean("is_available", true);
        }

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
