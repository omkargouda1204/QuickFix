package com.example.quickfix;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HomeRepository {
    private static final String TAG = "HomeRepository";
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient HTTP = HttpClientProvider.getInstance();

    public interface TopRatedCallback {
        void onSuccess(List<ProviderProfile> providers, boolean isNearby);
        void onError(String message);
    }

    public interface TopBookedCallback {
        void onSuccess(List<TopBookedService> services, boolean isNearby);
        void onError(String message);
    }

    public static void fetchTopRated(double lat, double lng, TopRatedCallback callback) {
        new Thread(() -> {
            try {
                List<ProviderProfile> results = fetchProvidersWithLogic(lat, lng, "rating.desc");
                boolean isNearby = !Double.isNaN(lat) && isAnyNearby(lat, lng, results);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onSuccess(results, isNearby));

            } catch (Exception e) {
                Log.e(TAG, "fetchTopRated", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onError(e.getMessage()));
            }
        }).start();
    }

    public static void fetchTopBookedProviders(double lat, double lng, TopRatedCallback callback) {
        new Thread(() -> {
            try {
                // Sorting by total_bookings column
                List<ProviderProfile> results = fetchProvidersWithLogic(lat, lng, "total_bookings.desc");
                boolean isNearby = !Double.isNaN(lat) && isAnyNearby(lat, lng, results);

                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onSuccess(results, isNearby));

            } catch (Exception e) {
                Log.e(TAG, "fetchTopBookedProviders", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onError(e.getMessage()));
            }
        }).start();
    }

    private static List<ProviderProfile> fetchProvidersWithLogic(double lat, double lng, String orderBy) throws Exception {
        List<ProviderProfile> list = new ArrayList<>();
        // Base query: active and approved
        String url = BASE_URL + "/rest/v1/provider_profiles?select=*&status=eq.active&is_approved=eq.true&order=" + orderBy + "&limit=100";
        
        Request req = new Request.Builder()
                .url(url)
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (r.isSuccessful() && r.body() != null) {
                JSONArray arr = new JSONArray(r.body().string());
                for (int i = 0; i < arr.length(); i++) {
                    ProviderProfile p = parseProvider(arr.getJSONObject(i));
                    if (p == null) continue;
                    
                    // If location available, filter within 25km. 
                    // However, we want to show global if none nearby. 
                    // So we'll first collect all, then filter, then fallback.
                    list.add(p);
                }
            }
        }

        // Logic: If location available, try to filter within 25km
        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
            List<ProviderProfile> nearby = new ArrayList<>();
            for (ProviderProfile p : list) {
                double pLat = !Double.isNaN(p.latitude) ? p.latitude : p.lat;
                double pLng = !Double.isNaN(p.longitude) ? p.longitude : p.lng;
                if (!Double.isNaN(pLat) && !Double.isNaN(pLng)) {
                    if (haversineKm(lat, lng, pLat, pLng) <= 25d) {
                        nearby.add(p);
                    }
                }
            }
            if (!nearby.isEmpty()) return nearby;
        }

        // Fallback to global list (top sorted)
        if (list.size() > 10) {
            return new ArrayList<>(list.subList(0, 10));
        }
        return list;
    }

    private static boolean isAnyNearby(double lat, double lng, List<ProviderProfile> list) {
        for (ProviderProfile p : list) {
            double pLat = !Double.isNaN(p.latitude) ? p.latitude : p.lat;
            double pLng = !Double.isNaN(p.longitude) ? p.longitude : p.lng;
            if (!Double.isNaN(pLat) && haversineKm(lat, lng, pLat, pLng) <= 25d) return true;
        }
        return false;
    }

    public static void fetchTopBooked(double lat, double lng, TopBookedCallback callback) {
        new Thread(() -> {
            try {
                List<TopBookedService> results = !Double.isNaN(lat) && !Double.isNaN(lng)
                        ? fetchTopBookedFromProviders(lat, lng, 25d)
                        : new ArrayList<>();
                boolean isNearby = !results.isEmpty();

                if (results.isEmpty()) {
                    results = fetchTopBookedFromProviders(Double.NaN, Double.NaN, Double.NaN);
                    isNearby = false;
                }

                List<TopBookedService> finalResults = results;
                boolean finalIsNearby = isNearby;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onSuccess(finalResults, finalIsNearby));

            } catch (Exception e) {
                Log.e(TAG, "fetchTopBooked", e);
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> 
                    callback.onError(e.getMessage()));
            }
        }).start();
    }

    private static List<TopBookedService> fetchTopBookedFromProviders(double lat, double lng, double radiusKm) throws Exception {
        Map<String, CategoryAggregate> aggregates = new LinkedHashMap<>();
        Request req = new Request.Builder()
                .url(BASE_URL + "/rest/v1/provider_profiles?select=category,rating,lat,lng,latitude,longitude,status,is_approved&status=eq.active&is_approved=eq.true&limit=1000")
                .get()
                .addHeader("apikey", ANON_KEY)
                .addHeader("Authorization", "Bearer " + ANON_KEY)
                .addHeader("Accept", "application/json")
                .build();

        try (Response r = HTTP.newCall(req).execute()) {
            if (r.isSuccessful() && r.body() != null) {
                JSONArray arr = new JSONArray(r.body().string());
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String category = obj.optString("category", "").trim();
                    if (category.isEmpty()) continue;

                    double providerLat = obj.has("lat") && !obj.isNull("lat") ? obj.optDouble("lat", Double.NaN) : obj.optDouble("latitude", Double.NaN);
                    double providerLng = obj.has("lng") && !obj.isNull("lng") ? obj.optDouble("lng", Double.NaN) : obj.optDouble("longitude", Double.NaN);
                    if (!Double.isNaN(lat) && !Double.isNaN(lng) && !Double.isNaN(providerLat) && !Double.isNaN(providerLng)) {
                        if (haversineKm(lat, lng, providerLat, providerLng) > radiusKm) continue;
                    }

                    CategoryAggregate aggregate = aggregates.get(category.toLowerCase(Locale.getDefault()));
                    if (aggregate == null) {
                        aggregate = new CategoryAggregate(category);
                        aggregates.put(category.toLowerCase(Locale.getDefault()), aggregate);
                    }
                    aggregate.count += 1;
                    aggregate.ratingSum += obj.optDouble("rating", 0d);
                }
            }
        }
        List<TopBookedService> list = new ArrayList<>();
        for (CategoryAggregate aggregate : aggregates.values()) {
            if (aggregate.count <= 0) continue;
            list.add(new TopBookedService(
                    aggregate.category,
                    aggregate.count,
                    499.0,
                    aggregate.count > 0 ? (float) (aggregate.ratingSum / aggregate.count) : 0f
            ));
        }
        Collections.sort(list, (a, b) -> {
            int countCmp = Integer.compare(b.totalBookings, a.totalBookings);
            if (countCmp != 0) return countCmp;
            return a.category.compareToIgnoreCase(b.category);
        });
        if (list.size() > 10) {
            return new ArrayList<>(list.subList(0, 10));
        }
        return list;
    }

    private static ProviderProfile parseProvider(JSONObject obj) {
        ProviderProfile p = new ProviderProfile();
        p.id = obj.optString("id");
        p.businessName = obj.optString("business_name");
        p.ownerName = obj.optString("owner_name");
        p.category = obj.optString("category");
        p.rating = (float) obj.optDouble("rating", 0.0);
        p.latitude = obj.optDouble("latitude", Double.NaN);
        p.longitude = obj.optDouble("longitude", Double.NaN);
        p.lat = obj.optDouble("lat", Double.NaN);
        p.lng = obj.optDouble("lng", Double.NaN);
        p.businessImageUrl = obj.optString("business_image_url");
        p.totalBookings = obj.optInt("total_bookings", 0);
        return p;
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2d) * Math.sin(dLat / 2d)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2d) * Math.sin(dLon / 2d);
        return 2d * r * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private static class CategoryAggregate {
        final String category;
        int count;
        double ratingSum;

        CategoryAggregate(String category) {
            this.category = category;
        }
    }
}
