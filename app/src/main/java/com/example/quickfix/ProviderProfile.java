package com.example.quickfix;

/** Model for a provider fetched from provider_profiles. */
public class ProviderProfile {
    public String id;
    public String firebaseUid;
    public String businessName;
    public String ownerName;
    public String category;
    public String city;
    public String address;
    public String phone;
    public String email;
    public String description;
    public String experienceYrs;
    public String businessImageUrl;
    public String idProofUrl;
    public boolean isVerified;
    public boolean isApproved;
    public float   rating;
    public int     reviewCount;
    public String  status;

    public double  lat = Double.NaN;
    public double  lng = Double.NaN;

    public ProviderProfile() {}
}
