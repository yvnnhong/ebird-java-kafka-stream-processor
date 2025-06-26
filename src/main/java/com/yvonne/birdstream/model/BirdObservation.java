package com.yvonne.birdstream.model;
import java.time.LocalDateTime;

public class BirdObservation {
    private String id;
    private String commonName;
    private int count;
    private LocalDateTime observationDate;
    private double latitude;
    private double longitude;
    private String county;
    private String observerId;
    
    public BirdObservation(String id, String commonName, int count, 
                          LocalDateTime observationDate, double latitude, 
                          double longitude, String county, String observerId) {
        this.id = id;
        this.commonName = commonName;
        this.count = count;
        this.observationDate = observationDate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.county = county;
        this.observerId = observerId;
    }
    
    // Getters
    public String getId() { return id; }
    public String getCommonName() { return commonName; }
    public int getCount() { return count; }
    public LocalDateTime getObservationDate() { return observationDate; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getCounty() { return county; }
    public String getObserverId() { return observerId; }
}