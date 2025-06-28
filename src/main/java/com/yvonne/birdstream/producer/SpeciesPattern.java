package com.yvonne.birdstream.producer;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.yvonne.birdstream.model.LocationData;
import com.yvonne.birdstream.model.BirdObservation; 

public class SpeciesPattern {
    private Map<Integer, Double> monthlyProbabilities = new HashMap<>();
    private Map<String, Double> seasonalProbabilities = new HashMap<>(); // NEW: Track seasons
    private List<Integer> counts = new ArrayList<>();
    private List<LocationData> locations = new ArrayList<>();
    private Map<String, List<Integer>> seasonalCounts = new HashMap<>(); // NEW: Track counts by season
    
    public void addObservation(BirdObservation obs) {
        int month = obs.getObservationDate().getMonthValue();
        String season = getSeason(month);
        
        // Update monthly probabilities
        monthlyProbabilities.merge(month, 0.1, Double::sum);
        
        // Update seasonal probabilities
        seasonalProbabilities.merge(season, 0.1, Double::sum);
        
        // Track all counts
        counts.add(obs.getCount());
        
        // Track seasonal counts for more accurate patterns
        seasonalCounts.computeIfAbsent(season, _ -> new ArrayList<>()).add(obs.getCount());
        
        // Track locations
        locations.add(new LocationData(obs.getLatitude(), obs.getLongitude(), obs.getCounty()));
    }
    
    private String getSeason(int month) {
        if (month >= 3 && month <= 5) return "Spring";
        if (month >= 6 && month <= 8) return "Summer"; 
        if (month >= 9 && month <= 11) return "Fall";
        return "Winter";
    }
    
    public double getMonthlyProbability(int month) {
        return monthlyProbabilities.getOrDefault(month, 0.01);
    }
    
    public double getSeasonalProbability(String season) {
        return seasonalProbabilities.getOrDefault(season, 0.01);
    }
    
    public double getAverageCount() {
        return counts.stream().mapToInt(Integer::intValue).average().orElse(1.0);
    }
    
    public double getSeasonalAverageCount(String season) {
        List<Integer> seasonCounts = seasonalCounts.get(season);
        if (seasonCounts == null || seasonCounts.isEmpty()) {
            return getAverageCount(); // Fallback to overall average
        }
        return seasonCounts.stream().mapToInt(Integer::intValue).average().orElse(1.0);
    }
    
    public double getCountStdDev() {
        double avg = getAverageCount();
        double variance = counts.stream()
            .mapToDouble(c -> Math.pow(c - avg, 2))
            .average().orElse(1.0);
        return Math.sqrt(variance);
    }
    
    public double getSeasonalCountStdDev(String season) {
        List<Integer> seasonCounts = seasonalCounts.get(season);
        if (seasonCounts == null || seasonCounts.isEmpty()) {
            return getCountStdDev(); // Fallback
        }
        
        double avg = getSeasonalAverageCount(season);
        double variance = seasonCounts.stream()
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
    
    // NEW: Get statistics for debugging
    public void printStats() {
        System.out.println("Species Pattern Statistics:");
        System.out.println("  Total observations: " + counts.size());
        System.out.println("  Average count: " + String.format("%.2f", getAverageCount()));
        System.out.println("  Count std dev: " + String.format("%.2f", getCountStdDev()));
        System.out.println("  Unique locations: " + locations.size());
        System.out.println("  Monthly probabilities: " + monthlyProbabilities);
        System.out.println("  Seasonal probabilities: " + seasonalProbabilities);
        
        for (String season : seasonalCounts.keySet()) {
            System.out.println("  " + season + " avg count: " + 
                             String.format("%.2f", getSeasonalAverageCount(season)));
        }
    }
}