// Fixed BirdStreamProcessor.java - Remove complex branching for now
package com.yvonne.birdstream.processor;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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
        
        // Main processing pipeline - simplified approach
        KStream<String, String> observations = builder.stream(INPUT_TOPIC);
        
        System.out.println("Bird Stream Processor starting...");
        System.out.println("Listening for observations on: " + INPUT_TOPIC);
        System.out.println("Sending alerts to: " + ALERTS_TOPIC);
        
        // Process all observations and split logic internally
        KStream<String, String> alerts = observations
            .filter((key, value) -> value != null && !value.trim().isEmpty())
            .mapValues(BirdStreamProcessor::processObservation)
            .filter((key, value) -> value != null); // Only keep alerts
        
        // Send alerts to output topic
        alerts.to(ALERTS_TOPIC);
        
        // Print alerts to console for demo
        alerts.foreach((key, alert) -> 
            System.out.println("ALERT: " + alert));
        
        // Print all observations for debugging
        observations.foreach((key, value) -> {
            try {
                JsonNode obs = mapper.readTree(value);
                String dataType = obs.get("dataType").asText();
                String species = obs.get("commonName").asText();
                int count = obs.get("count").asInt();
                System.out.println("Received " + dataType + ": " + species + " count=" + count);
            } catch (Exception e) {
                System.out.println("Received observation: " + value.substring(0, Math.min(100, value.length())) + "...");
            }
        });
        
        KafkaStreams streams = new KafkaStreams(builder.build(), props);
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        
        streams.start();
        System.out.println("Bird Stream Processor started and waiting for data...");
    }
    
    private static String processObservation(String observationJson) {
        try {
            JsonNode obs = mapper.readTree(observationJson);
            String dataType = obs.get("dataType").asText();
            
            if ("HISTORICAL".equals(dataType)) {
                // Build baseline from historical data
                updateBaseline(observationJson);
                return null; // No alert for historical data
            } else if ("SYNTHETIC".equals(dataType)) {
                // Check for anomalies in synthetic data
                if (detectAnomaly(observationJson)) {
                    return createAlert(observationJson);
                }
            }
            
            return null; // No alert
            
        } catch (Exception e) {
            System.err.println("Error processing observation: " + e.getMessage());
            return null;
        }
    }
    
    private static void updateBaseline(String observationJson) {
        try {
            JsonNode obs = mapper.readTree(observationJson);
            String species = obs.get("commonName").asText();
            String county = obs.get("county").asText();
            int count = obs.get("count").asInt();
            
            String key = species + "_" + county;
            baselines.computeIfAbsent(key, _ -> new SpeciesBaseline())
                     .addHistoricalObservation(count);
                     
            // Log baseline building progress (only once per baseline)
            SpeciesBaseline baseline = baselines.get(key);
            if (baseline.shouldLogBaseline()) {
                System.out.println("Baseline established for " + key + 
                                 " (mean=" + String.format("%.1f", baseline.getMean()) + 
                                 ", observations=" + baseline.getObservationCount() + ")");
            }
                     
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
                                 " - Count: " + count + ", Z-score: " + String.format("%.2f", zscore) +
                                 " (expected ~" + String.format("%.1f", baseline.getMean()) + ")");
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
}