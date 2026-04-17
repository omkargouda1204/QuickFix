package com.example.quickfix;

import java.util.Locale;

/**
 * Shared category normalization and matching used by category count and listing screens.
 */
public final class CategoryMatcher {

    private CategoryMatcher() {}

    public static boolean matches(String selectedCategory, String providerCategory) {
        if (selectedCategory == null || selectedCategory.trim().isEmpty()) return true;
        if (providerCategory == null || providerCategory.trim().isEmpty()) return false;

        String selected = normalize(selectedCategory);
        String provider = normalize(providerCategory);

        if (selected.isEmpty() || provider.isEmpty()) return false;
        if (selected.equals(provider)) return true;
        if (provider.contains(selected) || selected.contains(provider)) return true;

        // Keep broader categories discoverable for legacy provider records.
        if ("repair".equals(selected) && provider.contains("repair")) return true;
        if ("acservice".equals(selected) && (provider.contains("ac") || provider.contains("aircondition"))) return true;
        if ("security".equals(selected) && provider.contains("security")) return true;
        return false;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";

        String s = raw.toLowerCase(Locale.US).trim();
        s = s.replace("&", "and");
        s = s.replaceAll("[^a-z0-9]", "");

        if (s.isEmpty()) return "";

        if (s.equals("ac") || s.equals("acservice") || s.equals("acservices")
                || s.equals("airconditioner") || s.equals("airconditionerservice")
                || s.equals("airconditioning") || s.equals("acrepair")) {
            return "acservice";
        }

        if (s.equals("repairandmaintenance") || s.equals("maintenance") || s.equals("repairing")) {
            return "repair";
        }

        if (s.equals("appliancerepair") || s.equals("appliancerepairing")) {
            return "appliancerepair";
        }

        if (s.equals("securitysystems") || s.equals("securitysystem")) {
            return "security";
        }

        if (s.equals("laundry") || s.equals("laundryservice") || s.equals("housekeeping")) {
            return "cleaning";
        }

        if (s.equals("electrician")) return "electrical";
        if (s.equals("plumber")) return "plumbing";
        if (s.equals("carpenter")) return "carpentry";
        if (s.equals("landscape")) return "landscaping";

        return s;
    }
}
