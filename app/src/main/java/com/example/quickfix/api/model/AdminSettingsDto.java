package com.example.quickfix.api.model;

import com.google.gson.annotations.SerializedName;

/**
 * DTO for admin_settings table.
 */
public class AdminSettingsDto {
    public String id;

    @SerializedName("auto_approve_providers")
    public Boolean autoApproveProviders;

    @SerializedName("maintenance_mode")
    public Boolean maintenanceMode;

    @SerializedName("maintenance_message")
    public String maintenanceMessage;

    @SerializedName("maintenance_end_time")
    public String maintenanceEndTime;
}

