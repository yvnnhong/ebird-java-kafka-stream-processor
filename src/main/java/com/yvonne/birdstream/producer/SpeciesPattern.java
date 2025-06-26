package com.yvonne.birdstream.producer;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpeciesPattern {
    private Map<Integer, Double> monthlyProbabilities = new HashMap<>();
    private List<Integer> counts = new ArrayList<>();
    private List<LocationData> locations = new ArrayList<>();
    
    public void addObservation(BirdObservation obs) {
        int month = obs.getObservationDate().getMonthValue();
        monthlyProbabilities.merge(month, 0.1, Double::sum);
        counts.add(obs.getCount());
        locations.add(new LocationData(obs.getLatitude(), obs.getLongitude(), obs.getCounty()));
    }
    
    public double getMonthlyProbability(int month) {
        return monthlyProbabilities.getOrDefault(month, 0.01);
    }
    
    public double getAverageCount() {
        return counts.stream().mapToInt(Integer::intValue).average().orElse(1.0);
    }
    
    public double getCountStdDev() {
        double avg = getAverageCount();
        double variance = counts.stream()
            .mapToDouble(c -> Math.pow(c - avg, 2))
            .average().orElse(1.0);
        return Math.sqrt(variance);
    }
    
    public LocationData getRandomLocation() {
        if (locations.isEmpty()) {
            return new LocationData(37.0, -120.0, "Unknown"); // Central CA default
        }
        return locations.get(ThreadLocalRandom.current().nextInt(locations.size()));
    }
}