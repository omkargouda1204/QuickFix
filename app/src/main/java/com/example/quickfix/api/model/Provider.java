package com.example.quickfix.api.model;

import com.google.gson.annotations.SerializedName;

public class Provider {
    @SerializedName("id")
    public String id;

    @SerializedName("business_name")
    public String businessName;

    @SerializedName("category")
    public String category;

    @SerializedName("city")
    public String city;

    @SerializedName("profile_image")
    public String profileImage;

    @SerializedName("phone")
    public String phone;

    @SerializedName("description")
    public String description;
}

