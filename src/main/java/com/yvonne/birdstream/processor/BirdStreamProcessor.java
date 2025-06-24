// BirdStreamProcessor.java - Real-time anomaly detection and alerting
package main.java.com.yvonne.birdstream.processor;
//package com.yvonne.birdstream.processor;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.Stores;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BirdStreamProcessor {
    
    private static final String INPUT_TOPIC = "bird-observations";
    private static final String ALERTS_TOPIC = "bird-alerts";
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // In-memory baselines learned from historical data
    private static final Map<String, SpeciesBaseline> baselines = new ConcurrentHashMap<>();
    
    public static void main(String[] args) {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "bird-stream-processor");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        
        StreamsBuilder builder = new StreamsBuilder();
        
        // Main processing pipeline
        KStream<String, String> observations = builder.stream(INPUT_TOPIC);
        
        // Split historical vs synthetic data
        Map<String, KStream<String, String>> branches = observations.split()
            .branch((key, value) -> isHistoricalData(value), Branched.as("historical"))
            .branch((key, value) -> isSyntheticData(value), Branched.as("synthetic"))
            .defaultBranch(Branched.as("other"));
        
        // Process historical data to build baselines
        branches.get("historical")
            .foreach((key, value) -> updateBaseline(value));
        
        // Analyze synthetic data for anomalies
        KStream<String, String> alerts = branches.get("synthetic")
            .filter((key, value) -> detectAnomaly(value))
            .mapValues(BirdStreamProcessor::createAlert);
        
        // Advanced analytics on 5-minute windows
        KStream<String, String> windowedAlerts = observations
            .groupByKey()
            .windowedBy(TimeWindows.of(Duration.ofMinutes(5)))
            .aggregate(
                () -> new WindowedSpeciesData(),
                (key, value, aggregate) -> aggregate.add(parseObservation(value)),
                Materialized.with(Serdes.String(), new WindowedSpeciesDataSerde())
            )
            .toStream()
            .filter((windowedKey, aggregate) -> aggregate.isAnomalous())
            .mapValues(aggregate -> createWindowedAlert(windowedKey.key(), aggregate));
        
        // Send alerts to output topic
        alerts.to(ALERTS_TOPIC);
        windowedAlerts.to(ALERTS_TOPIC);
        
        // Print alerts to console for demo
        alerts.foreach((key, alert) -> 
            System.out.println("🚨 ALERT: " + alert));
        
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        
        streams.start();
        System.out.println("Bird Stream Processor started...");
    }
    
    private static boolean isHistoricalData(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            return "HISTORICAL".equals(node.get("dataType").asText());
        } catch (Exception e) {
            return false;
        }
    }
    
    private static boolean isSyntheticData(String value) {
        try {
            JsonNode node = mapper.readTree(value);
            return "SYNTHETIC".equals(node.get("dataType").asText());
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void updateBaseline(String observationJson) {
        try {
            JsonNode obs = mapper.readTree(observationJson);
            String species = obs.get("commonName").asText();
            String county = obs.get("county").asText();
            int count = obs.get("count").asInt();
            
            String key = species + "_" + county;
            baselines.computeIfAbsent(key, k -> new SpeciesBaseline())
                     .addHistoricalObservation(count);
                     
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private static boolean detectAnomaly(String observationJson) {
        try {
            JsonNode obs = mapper.readTree(observationJson);
            String species = obs.get("commonName").asText();
            String county = obs.get("county").asText();
            int count = obs.get("count").asInt();
            
            String key = species + "_" + county;
            SpeciesBaseline baseline = baselines.get(key);
            
            if (baseline == null || !baseline.hasEnoughData()) {
                return false; // Not enough historical data
            }
            
            // Anomaly detection: count is > 3 standard deviations from mean
            double zscore = Math.abs(count - baseline.getMean()) / baseline.getStdDev();
            
            if (zscore > 3.0) {
                System.out.println("Anomaly detected: " + species + " in " + county + 
                                 " - Count: " + count + ", Z-score: " + String.format("%.2f", zscore));
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    private static String createAlert(String observationJson) {
        try {
            JsonNode obs = mapper.readTree(observationJson);
            String species = obs.get("commonName").asText();
            String county = obs.get("county").asText();
            int count = obs.get("count").asInt();
            
            String key = species + "_" + county;
            SpeciesBaseline baseline = baselines.get(key);
            double zscore = Math.abs(count - baseline.getMean()) / baseline.getStdDev();
            
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "UNUSUAL_COUNT");
            alert.put("species", species);
            alert.put("county", county);
            alert.put("observedCount", count);
            alert.put("expectedCount", (int) baseline.getMean());
            alert.put("zScore", Math.round(zscore * 100.0) / 100.0);
            alert.put("severity", zscore > 5.0 ? "HIGH" : "MEDIUM");
            alert.put("timestamp", System.currentTimeMillis());
            alert.put("message", String.format(
                "Unusual %s count in %s: observed %d, expected ~%d (%.1fx normal)",
                species, county, count, (int) baseline.getMean(), 
                count / baseline.getMean()
            ));
            
            return mapper.writeValueAsString(alert);
            
        } catch (Exception e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    private static ObservationData parseObservation(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            return new ObservationData(
                node.get("commonName").asText(),
                node.get("county").asText(),
                node.get("count").asInt(),
                node.get("timestamp").asLong()
            );
        } catch (Exception e) {
            return null;
        }
    }
    
    private static String createWindowedAlert(String key, WindowedSpeciesData data) {
        try {
            Map<String, Object> alert = new HashMap<>();
            alert.put("alertType", "WINDOWED_ANOMALY");
            alert.put("key", key);
            alert.put("windowTotalCount", data.getTotalCount());
            alert.put("windowObservationCount", data.getObservationCount());
            alert.put("averageCountPerObservation", data.getAverageCount());
            alert.put("timestamp", System.currentTimeMillis());
            alert.put("message", String.format(
                "Unusual activity in 5-min window for %s: %d observations, %d total birds",
                key, data.getObservationCount(), data.getTotalCount()
            ));
            
            return mapper.writeValueAsString(alert);
        } catch (Exception e) {
            return "{}";
        }
    }
}

// Supporting classes
class SpeciesBaseline {
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
    
    public double getMean() { return mean; }
    public double getStdDev() { return stdDev; }
}

class ObservationData {
    private String species;
    private String county;
    private int count;
    private long timestamp;
    
    public ObservationData(String species, String county, int count, long timestamp) {
        this.species = species;
        this.county = county;
        this.count = count;
        this.timestamp = timestamp;
    }
    
    // Getters
    public String getSpecies() { return species; }
    public String getCounty() { return county; }
    public int getCount() { return count; }
    public long getTimestamp() { return timestamp; }
}

class WindowedSpeciesData {
    private int totalCount = 0;
    private int observationCount = 0;
    private List<Integer> counts = new ArrayList<>();
    
    public WindowedSpeciesData add(ObservationData obs) {
        if (obs != null) {
            totalCount += obs.getCount();
            observationCount++;
            counts.add(obs.getCount());
        }
        return this;
    }
    
    public boolean isAnomalous() {
        // Alert if we see unusually high activity in a 5-minute window
        return observationCount > 10 || // More than 10 observations in 5 minutes
               totalCount > 100 ||      // More than 100 total birds
               (observationCount > 0 && getAverageCount() > 20); // Very high per-observation count
    }
    
    public int getTotalCount() { return totalCount; }
    public int getObservationCount() { return observationCount; }
    public double getAverageCount() { 
        return observationCount > 0 ? (double) totalCount / observationCount : 0; 
    }
}

// Custom Serde for WindowedSpeciesData (simplified)
class WindowedSpeciesDataSerde extends org.apache.kafka.common.serialization.Serde<WindowedSpeciesData> {
    // Implementation would go here - simplified for this example
}