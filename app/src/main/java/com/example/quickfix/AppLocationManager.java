package com.example.quickfix;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.List;
import java.util.Locale;

/**
 * AppLocationManager — GPS live location + SharedPreferences storage.
 * Used globally to filter providers/services by city.
 */
public class AppLocationManager {

    private static final String TAG          = "AppLocationManager";
    public  static final String PREFS_NAME   = "quickfix_prefs";
    public  static final String KEY_CITY     = "selected_city";
    public  static final String KEY_CITY_LEGACY = "city";
    public  static final String KEY_AREA     = "selected_area";
    public  static final String KEY_ADDRESS  = "selected_address";
    public  static final String KEY_PINCODE  = "selected_pincode";
    public  static final String KEY_LAT      = "selected_lat";
    public  static final String KEY_LNG      = "selected_lng";

    /** 100+ common Indian cities for manual picker. */
    public static final String[] CITY_LIST = {
        "Mumbai", "Delhi", "Bangalore", "Hyderabad", "Ahmedabad",
        "Chennai", "Kolkata", "Pune", "Jaipur", "Lucknow",
        "Kanpur", "Nagpur", "Indore", "Thane", "Bhopal",
        "Visakhapatnam", "Pimpri-Chinchwad", "Patna", "Vadodara", "Ghaziabad",
        "Ludhiana", "Agra", "Nashik", "Faridabad", "Meerut",
        "Rajkot", "Kalyan", "Vasai-Virar", "Varanasi", "Srinagar",
        "Aurangabad", "Dhanbad", "Amritsar", "Navi Mumbai", "Allahabad",
        "Ranchi", "Coimbatore", "Jabalpur", "Gwalior", "Vijayawada",
        "Jodhpur", "Madurai", "Raipur", "Kota", "Guwahati",
        "Chandigarh", "Solapur", "Hubli", "Tiruchirappalli", "Bareilly",
        "Mysore", "Tiruppur", "Gurgaon", "Noida", "Moradabad",
        "Jalandhar", "Bhubaneswar", "Salem", "Warangal", "Guntur",
        "Aligarh", "Jamalpur", "Dehradun", "Surat", "Bikaner",
        "Firozabad", "Jamshedpur", "Asansol", "Bhilai", "Cuttack",
        "Kochi", "Siliguri", "Durgapur", "Kolhapur", "Ajmer",
        "Thiruvananthapuram", "Mangalore", "Udaipur", "Bellary", "Nellore",
        "Jammu", "Shimla", "Puducherry", "Tirupati", "Karnal"
    };

    private final Context ctx;
    private final SharedPreferences prefs;

    public AppLocationManager(Context context) {
        this.ctx   = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns currently saved city (or empty string). */
    public String getSavedCity() {
        String city = prefs.getString(KEY_CITY, "");
        if (city == null || city.trim().isEmpty()) {
            city = prefs.getString(KEY_CITY_LEGACY, "");
        }
        return city == null ? "" : city.trim();
    }

    /** Returns currently saved area (or empty string). */
    public String getSavedArea() {
        return prefs.getString(KEY_AREA, "");
    }

    public String getSavedAddress() {
        return prefs.getString(KEY_ADDRESS, "");
    }

    public String getSavedPincode() {
        return prefs.getString(KEY_PINCODE, "");
    }

    /** Returns a UI-ready location label like "City - 560001". */
    public String getSavedLocationLabel() {
        String label = composeLocationLabel(getSavedCity(), getSavedPincode());
        if (!label.isEmpty()) return label;
        return composeLocationLabel(getSavedCity(), getSavedArea());
    }

    public String getSavedDisplayLabel() {
        return getSavedLocationLabel();
    }

    /** Saves city manually. */
    public void saveCity(String city) {
        String normalized = normalize(city);
        prefs.edit()
                .putString(KEY_CITY, normalized)
                .putString(KEY_CITY_LEGACY, normalized)
                .remove(KEY_AREA)
                .remove(KEY_ADDRESS)
                .remove(KEY_PINCODE)
                .remove(KEY_LAT)
                .remove(KEY_LNG)
                .apply();
    }

    /** Saves city with coordinates. */
    public void saveLocation(String city, double lat, double lng) {
        saveLocation(city, "", lat, lng);
    }

    /** Saves city + area with coordinates. */
    public void saveLocation(String city, String area, double lat, double lng) {
        saveLocation(city, area, "", "", lat, lng);
    }

    public void saveLocation(String city,
                             String area,
                             String address,
                             String pincode,
                             double lat,
                             double lng) {
        String normalizedCity = normalize(city);
        String normalizedArea = normalize(area);
        String normalizedAddress = normalize(address);
        String normalizedPincode = normalize(pincode);
        prefs.edit()
                .putString(KEY_CITY, normalizedCity)
                .putString(KEY_CITY_LEGACY, normalizedCity)
                .putString(KEY_AREA, normalizedArea)
                .putString(KEY_ADDRESS, normalizedAddress)
                .putString(KEY_PINCODE, normalizedPincode)
                .putString(KEY_LAT, String.valueOf(lat))
                .putString(KEY_LNG, String.valueOf(lng))
                .apply();
    }

    public double getSavedLat() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LAT, "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public double getSavedLng() {
        try {
            return Double.parseDouble(prefs.getString(KEY_LNG, "NaN"));
        } catch (Exception ignored) {
            return Double.NaN;
        }
    }

    public interface LiveLocationCallback {
        void onSuccess(String city, String area, double lat, double lng);
        void onFailure(String reason);
    }

    /**
     * Gets current GPS location, reverse-geocodes it to a city name, saves to prefs.
     */
    public void fetchLiveLocation(LiveLocationCallback callback) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            callback.onFailure("Location permission not granted");
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(ctx);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        // Fallback to last known
                        client.getLastLocation().addOnSuccessListener(last -> {
                            if (last != null) resolveCity(last, callback, mainHandler);
                            else callback.onFailure("Unable to get location. Please try again.");
                        }).addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                        return;
                    }
                    resolveCity(location, callback, mainHandler);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        } catch (SecurityException e) {
            callback.onFailure("Permission denied: " + e.getMessage());
        }
    }

    private void resolveCity(Location location, LiveLocationCallback callback, Handler mainHandler) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);

                String city = "";
                String area = "";
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    city = pickCity(addr);
                    area = pickArea(addr);
                }

                final String finalCity = normalize(city);
                final String finalArea = normalize(area);
                prefs.edit()
                        .putString(KEY_CITY, finalCity)
                        .putString(KEY_CITY_LEGACY, finalCity)
                        .putString(KEY_AREA, finalArea)
                        .putString(KEY_LAT, String.valueOf(lat))
                        .putString(KEY_LNG, String.valueOf(lng))
                        .apply();
                mainHandler.post(() -> callback.onSuccess(finalCity, finalArea, lat, lng));
            } catch (Exception e) {
                Log.e(TAG, "Geocoder failed: " + e.getMessage());
                prefs.edit()
                        .putString(KEY_CITY, "")
                        .putString(KEY_CITY_LEGACY, "")
                        .putString(KEY_AREA, "")
                        .putString(KEY_LAT, String.valueOf(lat))
                        .putString(KEY_LNG, String.valueOf(lng))
                        .apply();
                mainHandler.post(() -> callback.onSuccess("", "", lat, lng));
            }
        }).start();
    }

    /** Clears saved location. */
    public void clearCity() {
        prefs.edit()
                .remove(KEY_CITY)
                .remove(KEY_CITY_LEGACY)
                .remove(KEY_AREA)
                .remove(KEY_ADDRESS)
                .remove(KEY_PINCODE)
                .remove(KEY_LAT)
                .remove(KEY_LNG)
                .apply();
    }

    private static String pickCity(Address addr) {
        String city = normalize(addr.getLocality());
        if (city.isEmpty()) city = normalize(addr.getSubAdminArea());
        if (city.isEmpty()) city = normalize(addr.getAdminArea());
        return city;
    }

    private static String pickArea(Address addr) {
        String area = normalize(addr.getSubLocality());
        if (area.isEmpty()) area = normalize(addr.getFeatureName());
        if (area.isEmpty()) area = normalize(addr.getThoroughfare());
        return area;
    }

    public static String composeLocationLabel(String city, String area) {
        String c = normalize(city);
        String a = normalize(area);
        if (!c.isEmpty() && !a.isEmpty()) return c + " - " + a;
        return c.isEmpty() ? a : c;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
