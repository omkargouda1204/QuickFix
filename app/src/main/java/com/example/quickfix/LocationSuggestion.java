package com.example.quickfix;

public class LocationSuggestion {
    public final String city;
    public final String area;
    public final String state;
    public final double lat;
    public final double lng;
    public final boolean nearby;
    public final float distanceKm;

    public LocationSuggestion(String city, String area, String state, double lat, double lng,
                              boolean nearby, float distanceKm) {
        this.city = normalize(city);
        this.area = normalize(area);
        this.state = normalize(state);
        this.lat = lat;
        this.lng = lng;
        this.nearby = nearby;
        this.distanceKm = distanceKm;
    }

    public String displayTitle() {
        return AppLocationManager.composeLocationLabel(city, area);
    }

    public String displaySubtitle() {
        if (!state.isEmpty()) {
            return state;
        }
        return area.isEmpty() ? "State unavailable" : "Area selected";
    }

    public String searchableText() {
        return (city + " " + area + " " + state).toLowerCase(java.util.Locale.getDefault());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
