// Simplified BirdStreamProcessor.java - Remove complex windowing for now
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
        
        // Main processing pipeline
        KStream<String, String> observations = builder.stream(INPUT_TOPIC);
        
        // Split historical vs synthetic data
        Map<String, KStream<String, String>> branches = observations.split()
            .branch((_, value) -> isHistoricalData(value), Branched.as("historical"))
            .branch((_, value) -> isSyntheticData(value), Branched.as("synthetic"))
            .defaultBranch(Branched.as("other"));
        
        // Process historical data to build baselines
        branches.get("historical")
            .foreach((_, value) -> updateBaseline(value));
        
        // Analyze synthetic data for anomalies
        KStream<String, String> alerts = branches.get("synthetic")
            .filter((_, value) -> detectAnomaly(value))
            .mapValues(BirdStreamProcessor::createAlert);
        
        // Send alerts to output topic
        alerts.to(ALERTS_TOPIC);
        
        // Print alerts to console for demo
        alerts.foreach((_, alert) -> 
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
            baselines.computeIfAbsent(key, _ -> new SpeciesBaseline())
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
}
