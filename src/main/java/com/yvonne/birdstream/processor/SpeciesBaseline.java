package com.yvonne.birdstream.processor;
import java.util.ArrayList;
import java.util.List;

public class SpeciesBaseline {
    private List<Integer> historicalCounts = new ArrayList<>();
    private double mean = 0.0;
    private double stdDev = 1.0;
    private boolean calculated = false;
    private boolean baselineLogged = false; // Prevent spam logging
    
    public void addHistoricalObservation(int count) {
        historicalCounts.add(count);
        if (historicalCounts.size() % 10 == 0) { // Recalculate every 10 observations
            calculateStats();
        }
    }
    
    private void calculateStats() {
        if (historicalCounts.isEmpty()) return;
        
        mean = historicalCounts.stream().mapToInt(Integer::intValue).average().orElse(1.0);
        
        double variance = historicalCounts.stream()
            .mapToDouble(c -> Math.pow(c - mean, 2))
            .average().orElse(1.0);
        stdDev = Math.max(Math.sqrt(variance), 0.5); // Minimum stddev to avoid division by zero
        
        calculated = true;
    }
    
    public boolean hasEnoughData() {
        return calculated && historicalCounts.size() >= 20; // Reduced threshold for faster testing
    }
    
    public boolean shouldLogBaseline() {
        if (hasEnoughData() && !baselineLogged) {
            baselineLogged = true;
            return true;
        }
        return false;
    }
    
    public double getMean() { 
        return mean; 
    }
    
    public double getStdDev() { 
        return stdDev; 
    }
    
    public int getObservationCount() {
        return historicalCounts.size();
    }
}