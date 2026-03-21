package com.example.quickfix;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * NearbyLocationRepository - Loads nearby cities/towns from Supabase.
 *
 * Preferred path: RPC function `nearby_provider_cities(lat,lng,radius)`.
 * Fallback path: fetch providers/cities rows and compute distance client-side.
 */
public class NearbyLocationRepository {

    private static final String TAG = "NearbyLocRepo";
    private static final String BASE_URL = BuildConfig.SUPABASE_URL;
    private static final String ANON_KEY = BuildConfig.SUPABASE_ANON_KEY;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(List<LocationSuggestion> items);
        void onError(String message);
    }


    public void fetchNearbyCities(double originLat,
                                  double originLng,
                                  double radiusKm,
                                  Callback callback) {
        if (Double.isNaN(originLat) || Double.isNaN(originLng)) {
            callback.onError("Location unavailable");
            return;
        }

        new Thread(() -> {
            try {
                List<LocationSuggestion> fromRpc = fetchFromRpc(originLat, originLng, radiusKm);
                if (!fromRpc.isEmpty()) {
                    MAIN.post(() -> callback.onSuccess(fromRpc));
                    return;
                }

                List<LocationSuggestion> fromProviders = fetchFromProviders(originLat, originLng, radiusKm);
                if (!fromProviders.isEmpty()) {
                    MAIN.post(() -> callback.onSuccess(fromProviders));
                    return;
                }

                List<LocationSuggestion> fromCities = fetchFromCitiesTable(originLat, originLng, radiusKm);
                MAIN.post(() -> callback.onSuccess(fromCities));
            } catch (Exception e) {
                Log.e(TAG, "fetchNearbyCities error: " + e.getMessage(), e);
                MAIN.post(() -> callback.onError("Unable to load nearby places."));
            }
        }).start();
    }

    private List<LocationSuggestion> fetchFromRpc(double originLat,
                                                  double originLng,
                                                  double radiusKm) {
        List<LocationSuggestion> out = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("p_lat", originLat);
            body.put("p_lng", originLng);
            body.put("p_radius_km", radiusKm);

            Request req = new Request.Builder()
                    .url(BASE_URL + "/rest/v1/rpc/nearby_provider_cities")
                    .post(RequestBody.create(body.toString(), JSON))
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String raw = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) {
                    Log.d(TAG, "RPC nearby_provider_cities HTTP " + r.code());
                    return out;
                }

                JSONArray arr = new JSONArray(raw);
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String city = firstNonBlank(
                            o.optString("city", ""),
                            o.optString("town", ""),
                            o.optString("locality", "")
                    );
                    String area = firstNonBlank(
                            o.optString("area", ""),
                            o.optString("sub_locality", "")
                    );
                    String state = firstNonBlank(o.optString("state", ""), o.optString("region", ""));
                    if (city.isEmpty() && area.isEmpty()) {
                        continue;
                    }

                    double lat = pickDouble(o, "latitude", "lat");
                    double lng = pickDouble(o, "longitude", "lng");
                    float distanceKm = (float) pickDouble(o, "distance_km", "distance");
                    if (distanceKm <= 0f && !Double.isNaN(lat) && !Double.isNaN(lng)) {
                        distanceKm = (float) haversineKm(originLat, originLng, lat, lng);
                    }

                    int servicesCount = (int) Math.max(0d, pickDouble(o,
                            "services_count", "providers_count", "provider_count", "count"));

                    String key = key(city, area, state);
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);

                    out.add(new LocationSuggestion(
                            city,
                            area,
                            state,
                            Double.isNaN(lat) ? 0d : lat,
                            Double.isNaN(lng) ? 0d : lng,
                            true,
                            Math.max(0f, distanceKm),
                            servicesCount
                    ));
                }

                Collections.sort(out, (a, b) -> Float.compare(a.distanceKm, b.distanceKm));
            }
        } catch (Exception e) {
            Log.d(TAG, "RPC fallback: " + e.getMessage());
        }
        return out;
    }

    private List<LocationSuggestion> fetchFromProviders(double originLat,
                                                        double originLng,
                                                        double radiusKm) {
        List<LocationSuggestion> fromLatLng = fetchFromProviderSelect(
                "city,area,state,latitude,longitude&status=eq.active&limit=800",
                "latitude",
                "longitude",
                originLat,
                originLng,
                radiusKm
        );
        if (!fromLatLng.isEmpty()) {
            return fromLatLng;
        }

        return fetchFromProviderSelect(
                "city,area,state,lat,lng&status=eq.active&limit=800",
                "lat",
                "lng",
                originLat,
                originLng,
                radiusKm
        );
    }

    private List<LocationSuggestion> fetchFromProviderSelect(String selectAndParams,
                                                             String latKey,
                                                             String lngKey,
                                                             double originLat,
                                                             double originLng,
                                                             double radiusKm) {
        LinkedHashMap<String, Aggregate> grouped = new LinkedHashMap<>();
        try {
            Request req = new Request.Builder()
                    .url(BASE_URL + "/rest/v1/provider_profiles?select=" + selectAndParams)
                    .get()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String raw = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) {
                    return new ArrayList<>();
                }

                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String city = o.optString("city", "").trim();
                    String area = o.optString("area", "").trim();
                    String state = o.optString("state", "").trim();
                    if (city.isEmpty() && area.isEmpty()) {
                        continue;
                    }

                    double lat = optDoubleFlexible(o, latKey);
                    double lng = optDoubleFlexible(o, lngKey);
                    if (Double.isNaN(lat) || Double.isNaN(lng)) {
                        continue;
                    }

                    double distanceKm = haversineKm(originLat, originLng, lat, lng);
                    if (distanceKm > radiusKm) {
                        continue;
                    }

                    String key = key(city, area, state);
                    Aggregate ag = grouped.get(key);
                    if (ag == null) {
                        ag = new Aggregate(city, area, state);
                        grouped.put(key, ag);
                    }
                    ag.count += 1;
                    ag.sumLat += lat;
                    ag.sumLng += lng;
                    ag.minDistanceKm = Math.min(ag.minDistanceKm, distanceKm);
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "fetchFromProviderSelect fallback: " + e.getMessage());
        }

        List<LocationSuggestion> out = new ArrayList<>();
        for (Map.Entry<String, Aggregate> entry : grouped.entrySet()) {
            Aggregate ag = entry.getValue();
            if (ag.count <= 0) {
                continue;
            }
            out.add(new LocationSuggestion(
                    ag.city,
                    ag.area,
                    ag.state,
                    ag.sumLat / ag.count,
                    ag.sumLng / ag.count,
                    true,
                    (float) ag.minDistanceKm,
                    ag.count
            ));
        }

        Collections.sort(out, (a, b) -> Float.compare(a.distanceKm, b.distanceKm));
        return out;
    }

    private List<LocationSuggestion> fetchFromCitiesTable(double originLat,
                                                          double originLng,
                                                          double radiusKm) {
        List<LocationSuggestion> out = new ArrayList<>();
        try {
            Request req = new Request.Builder()
                    .url(BASE_URL + "/rest/v1/cities?select=city,town,area,state,latitude,longitude,lat,lng&limit=1200")
                    .get()
                    .addHeader("apikey", ANON_KEY)
                    .addHeader("Authorization", "Bearer " + ANON_KEY)
                    .addHeader("Accept", "application/json")
                    .build();

            try (Response r = HTTP.newCall(req).execute()) {
                String raw = r.body() != null ? r.body().string() : "[]";
                if (r.code() != 200) {
                    return out;
                }

                JSONArray arr = new JSONArray(raw);
                Set<String> seen = new HashSet<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String city = firstNonBlank(o.optString("city", ""), o.optString("town", ""));
                    String area = o.optString("area", "").trim();
                    String state = o.optString("state", "").trim();
                    double lat = pickDouble(o, "latitude", "lat");
                    double lng = pickDouble(o, "longitude", "lng");
                    if (Double.isNaN(lat) || Double.isNaN(lng)) {
                        continue;
                    }
                    double distanceKm = haversineKm(originLat, originLng, lat, lng);
                    if (distanceKm > radiusKm) {
                        continue;
                    }

                    String key = key(city, area, state);
                    if (seen.contains(key)) {
                        continue;
                    }
                    seen.add(key);

                    out.add(new LocationSuggestion(city, area, state, lat, lng,
                            true, (float) distanceKm, 0));
                }
                Collections.sort(out, (a, b) -> Float.compare(a.distanceKm, b.distanceKm));
            }
        } catch (Exception e) {
            Log.d(TAG, "cities fallback: " + e.getMessage());
        }
        return out;
    }

    private static String key(String city, String area, String state) {
        return safe(city).toLowerCase(Locale.getDefault()) + "|"
                + safe(area).toLowerCase(Locale.getDefault()) + "|"
                + safe(state).toLowerCase(Locale.getDefault());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    private static double pickDouble(JSONObject o, String... keys) {
        for (String key : keys) {
            double d = optDoubleFlexible(o, key);
            if (!Double.isNaN(d)) {
                return d;
            }
        }
        return Double.NaN;
    }

    private static double optDoubleFlexible(JSONObject obj, String key) {
        try {
            if (!obj.has(key) || obj.isNull(key)) {
                return Double.NaN;
            }
            Object raw = obj.get(key);
            if (raw instanceof Number) {
                return ((Number) raw).doubleValue();
            }
            return Double.parseDouble(String.valueOf(raw));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371.0d;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private static class Aggregate {
        final String city;
        final String area;
        final String state;
        int count;
        double sumLat;
        double sumLng;
        double minDistanceKm = Double.MAX_VALUE;

        Aggregate(String city, String area, String state) {
            this.city = safe(city);
            this.area = safe(area);
            this.state = safe(state);
        }
    }
}

