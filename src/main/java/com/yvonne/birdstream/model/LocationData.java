package com.yvonne.birdstream.model;

public class LocationData {
    final double latitude;
    final double longitude;
    final String county;
    
    public LocationData(double latitude, double longitude, String county) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.county = county;
    }

    // Public getters for cross-package access
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public String getCounty() {
        return county;
    }
}
