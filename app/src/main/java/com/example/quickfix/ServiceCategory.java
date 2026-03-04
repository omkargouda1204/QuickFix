package com.example.quickfix;

/** Data model for a service category shown in CategoriesActivity grid. */
public class ServiceCategory {
    public String name;
    public int    iconRes;
    public int    bgRes;
    public String providerCount; // "12 providers"

    public ServiceCategory(String name, int iconRes, int bgRes) {
        this.name          = name;
        this.iconRes       = iconRes;
        this.bgRes         = bgRes;
        this.providerCount = "";
    }
}

