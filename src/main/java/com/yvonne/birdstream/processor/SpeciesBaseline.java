package com.yvonne.birdstream.processor;
// can use java.util.*; instead (wild card import)
import java.util.ArrayList;
import java.util.List;

public class SpeciesBaseline {
    private List<Integer> historicalCounts = new ArrayList<>();
    private double mean = 0.0;
    private double stdDev = 1.0;
    private boolean calculated = false;
    
    public void addHistoricalObservation(int count) {
        historicalCounts.add(count);
        if (historicalCounts.size() % 100 == 0) { // Recalculate every 100 observations
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
        return calculated && historicalCounts.size() >= 50;
    }
    
    public double getMean() { 
        return mean; 
    }
    
    public double getStdDev() { 
        return stdDev; 
    }
}