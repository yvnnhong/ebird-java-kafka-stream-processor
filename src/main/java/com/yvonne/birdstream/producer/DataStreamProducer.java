// DataStreamProducer.java - Integrated with regression-based synthetic data
package com.yvonne.birdstream.producer;

import com.opencsv.CSVReader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import com.yvonne.birdstream.model.BirdObservation; 
import com.yvonne.birdstream.model.LocationData;

public class DataStreamProducer {
    
    private static final String KAFKA_TOPIC = "bird-observations";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // File paths
    private static final String HISTORICAL_DATA_PATH = "data/results_csv/mourning_dove_baseline_complete.csv";
    private static final String SYNTHETIC_DATA_PATH = "data/synthetic_observations_regression.json";
    
    private KafkaProducer<String, String> producer;
    private Map<String, SpeciesPattern> speciesPatterns;
    private List<JsonNode> preGeneratedSyntheticData;
    
    public static void main(String[] args) {
        DataStreamProducer producer = new DataStreamProducer();
        producer.start();
    }
    
    public void start() {
        initializeKafkaProducer();
        loadHistoricalPatterns();
        loadPreGeneratedSyntheticData();
        
        System.out.println("🚀 Starting dual-stream data pipeline...");
        System.out.println("   📚 Historical data: " + (speciesPatterns.size() > 0 ? "Loaded" : "Not found"));
        System.out.println("   🤖 Synthetic data: " + preGeneratedSyntheticData.size() + " observations");
        
        // Start both streams
        Thread historicalReplay = new Thread(this::replayHistoricalData);
        Thread syntheticStream = new Thread(this::streamPreGeneratedSynthetic);
        
        historicalReplay.start();
        
        // Wait for historical data to build patterns, then start synthetic
        try {
            Thread.sleep(10000); // 10 second delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        syntheticStream.start();
        
        try {
            historicalReplay.join();
            syntheticStream.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void initializeKafkaProducer() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        
        this.producer = new KafkaProducer<>(props);
        System.out.println("✅ Kafka producer initialized");
    }
    
    private void loadPreGeneratedSyntheticData() {
        preGeneratedSyntheticData = new ArrayList<>();
        
        try {
            String jsonContent = Files.readString(Paths.get(SYNTHETIC_DATA_PATH));
            JsonNode arrayNode = mapper.readTree(jsonContent);
            
            if (arrayNode.isArray()) {
                for (JsonNode obsNode : arrayNode) {
                    preGeneratedSyntheticData.add(obsNode);
                }
            }
            
            System.out.println("✅ Loaded " + preGeneratedSyntheticData.size() + " pre-generated synthetic observations");
            
            // Show sample data
            if (!preGeneratedSyntheticData.isEmpty()) {
                JsonNode sample = preGeneratedSyntheticData.get(0);
                System.out.println("   📋 Sample: " + sample.get("commonName").asText() + 
                                 ", count=" + sample.get("count").asInt() + 
                                 ", season=" + sample.get("season").asText());
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Could not load pre-generated synthetic data: " + e.getMessage());
            System.err.println("   Will generate synthetic data on-the-fly instead");
        }
    }
    
    private void loadHistoricalPatterns() {
        speciesPatterns = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(HISTORICAL_DATA_PATH))) {
            String[] header = reader.readNext();
            String[] line;
            
            System.out.println("📊 Loading historical patterns from: " + HISTORICAL_DATA_PATH);
            
            while ((line = reader.readNext()) != null) {
                BirdObservation obs = parseMourningDoveObservation(line);
                if (obs != null) {
                    updateSpeciesPattern(obs);
                }
            }
            
        } catch (Exception e) {
            System.err.println("⚠️  Error loading historical patterns: " + e.getMessage());
        }
        
        System.out.println("✅ Loaded patterns for " + speciesPatterns.size() + " species/location combinations");
    }
    
    private BirdObservation parseMourningDoveObservation(String[] line) {
        try {
            // CSV format: year,season,breeding_code,observations,percentage_of_season,rank,source_file,period,analysis_period,decade,breeding_success_indicator,territorial_behavior
            int year = Integer.parseInt(line[0]);
            String season = line[1];
            String breedingCode = line[2];
            int observations = Integer.parseInt(line[3]);
            
            LocalDateTime observationDate = createSeasonalDate(year, season);
            double[] coords = getRandomSanDiegoCoordinates();
            String county = "San Diego";
            
            return new BirdObservation(
                UUID.randomUUID().toString(),
                "Mourning Dove",
                Math.max(1, observations),
                observationDate,
                coords[0], coords[1],
                county,
                "Historical_Observer_" + year
            );
            
        } catch (Exception e) {
            return null;
        }
    }
    
    private LocalDateTime createSeasonalDate(int year, String season) {
        int month;
        switch (season.toLowerCase()) {
            case "spring": month = 3 + ThreadLocalRandom.current().nextInt(3); break;
            case "summer": month = 6 + ThreadLocalRandom.current().nextInt(3); break;
            case "fall": month = 9 + ThreadLocalRandom.current().nextInt(3); break;
            case "winter": month = ThreadLocalRandom.current().nextBoolean() ? 12 : 1 + ThreadLocalRandom.current().nextInt(2); break;
            default: month = ThreadLocalRandom.current().nextInt(12) + 1;
        }
        
        int day = ThreadLocalRandom.current().nextInt(28) + 1;
        return LocalDateTime.of(year, month, day, 8 + ThreadLocalRandom.current().nextInt(10), 0);
    }
    
    private double[] getRandomSanDiegoCoordinates() {
        // San Diego County bounds
        double minLat = 32.53, maxLat = 33.51;
        double minLon = -117.60, maxLon = -116.07;
        
        double lat = minLat + (maxLat - minLat) * ThreadLocalRandom.current().nextDouble();
        double lon = minLon + (maxLon - minLon) * ThreadLocalRandom.current().nextDouble();
        
        return new double[]{lat, lon};
    }
    
    private void replayHistoricalData() {
        System.out.println("📚 Starting historical data replay...");
        
        try (CSVReader reader = new CSVReader(new FileReader(HISTORICAL_DATA_PATH))) {
            String[] header = reader.readNext();
            String[] line;
            int count = 0;
            
            while ((line = reader.readNext()) != null) {
                BirdObservation obs = parseMourningDoveObservation(line);
                
                if (obs != null) {
                    sendToKafka(obs, "HISTORICAL");
                    count++;
                    
                    if (count % 10 == 0) {
                        System.out.println("   📤 Streamed " + count + " historical observations");
                    }
                    
                    // Simulate time delay (accelerated: 1 day = 50ms)
                    Thread.sleep(50);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("✅ Historical replay completed");
    }
    
    private void streamPreGeneratedSynthetic() {
        System.out.println("🤖 Starting regression-based synthetic data stream...");
        
        if (preGeneratedSyntheticData.isEmpty()) {
            System.out.println("⚠️  No pre-generated data found, falling back to real-time generation");
            generateSyntheticDataRealTime();
            return;
        }
        
        try {
            int count = 0;
            
            for (JsonNode syntheticNode : preGeneratedSyntheticData) {
                // Convert JSON node to BirdObservation
                BirdObservation obs = jsonNodeToBirdObservation(syntheticNode);
                
                if (obs != null) {
                    sendToKafka(obs, "SYNTHETIC");
                    count++;
                    
                    // Log anomalies
                    if (syntheticNode.has("isAnomaly") && syntheticNode.get("isAnomaly").asBoolean()) {
                        System.out.println("🚨 Streaming ANOMALY: " + obs.getCount() + 
                                         " birds in " + syntheticNode.get("season").asText() + 
                                         " (ID: " + obs.getId() + ")");
                    }
                    
                    if (count % 20 == 0) {
                        System.out.println("   🔄 Streamed " + count + "/" + preGeneratedSyntheticData.size() + 
                                         " synthetic observations");
                    }
                    
                    // Stream at realistic intervals (every 3 seconds)
                    Thread.sleep(3000);
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("✅ Synthetic data streaming completed");
    }
    
    private BirdObservation jsonNodeToBirdObservation(JsonNode node) {
        try {
            String id = node.get("id").asText();
            String commonName = node.get("commonName").asText();
            int count = node.get("count").asInt();
            String dateStr = node.get("observationDate").asText();
            
            // Fix date parsing - handle both formats
            LocalDateTime observationDate;
            if (dateStr.contains("T")) {
                // Already has time component
                observationDate = LocalDateTime.parse(dateStr);
            } else {
                // Just date, add time
                observationDate = LocalDateTime.parse(dateStr + "T08:00:00");
            }
            
            double latitude = node.get("latitude").asDouble();
            double longitude = node.get("longitude").asDouble();
            String county = node.get("county").asText();
            String observerId = node.has("observerId") ? 
                               node.get("observerId").asText() : 
                               "REGRESSION_OBSERVER_" + ThreadLocalRandom.current().nextInt(100);
            
            return new BirdObservation(id, commonName, count, observationDate, 
                                     latitude, longitude, county, observerId);
            
        } catch (Exception e) {
            System.err.println("Error converting JSON to BirdObservation: " + e.getMessage());
            System.err.println("Problematic date string: " + node.get("observationDate").asText());
            return null;
        }
    }
    
    private void generateSyntheticDataRealTime() {
        System.out.println("🔄 Generating synthetic data in real-time...");
        
        while (true) {
            try {
                // Generate based on learned patterns
                for (String speciesKey : speciesPatterns.keySet()) {
                    if (shouldGenerateObservation(speciesKey)) {
                        BirdObservation syntheticObs = generateSyntheticObservation(speciesKey);
                        sendToKafka(syntheticObs, "SYNTHETIC");
                    }
                }
                
                Thread.sleep(5000); // Generate every 5 seconds
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    private boolean shouldGenerateObservation(String speciesKey) {
        SpeciesPattern pattern = speciesPatterns.get(speciesKey);
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        
        double probability = pattern.getMonthlyProbability(currentMonth);
        return ThreadLocalRandom.current().nextDouble() < probability * 0.02; // Scale down
    }
    
    private BirdObservation generateSyntheticObservation(String speciesKey) {
        SpeciesPattern pattern = speciesPatterns.get(speciesKey);
        LocalDateTime now = LocalDateTime.now();
        
        // Generate count with variation
        int baseCount = Math.max(1, (int) pattern.getAverageCount());
        int count;
        
        // 5% chance of creating an anomaly
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            count = baseCount * (5 + ThreadLocalRandom.current().nextInt(10));
            System.out.println("🎯 Generated real-time ANOMALY: " + count + " (normal: " + baseCount + ")");
        } else {
            double stdDev = Math.max(1.0, pattern.getCountStdDev());
            count = Math.max(1, (int) (baseCount + ThreadLocalRandom.current().nextGaussian() * stdDev));
        }
        
        LocationData location = pattern.getRandomLocation();
        
        return new BirdObservation(
            UUID.randomUUID().toString(),
            "Mourning Dove",
            count,
            now,
            location.getLatitude(),
            location.getLongitude(),
            location.getCounty(),
            "REALTIME_SYNTHETIC_" + ThreadLocalRandom.current().nextInt(1000)
        );
    }
    
    private void updateSpeciesPattern(BirdObservation obs) {
        String key = obs.getCommonName() + "_" + obs.getCounty();
        speciesPatterns.computeIfAbsent(key, _ -> new SpeciesPattern())
                     .addObservation(obs);
    }
    
    private void sendToKafka(BirdObservation obs, String dataType) {
        try {
            ObjectNode json = mapper.createObjectNode();
            json.put("id", obs.getId());
            json.put("commonName", obs.getCommonName());
            json.put("count", obs.getCount());
            json.put("observationDate", obs.getObservationDate().toString());
            json.put("latitude", obs.getLatitude());
            json.put("longitude", obs.getLongitude());
            json.put("county", obs.getCounty());
            json.put("observerId", obs.getObserverId());
            json.put("dataType", dataType); // HISTORICAL or SYNTHETIC
            json.put("timestamp", System.currentTimeMillis());
            
            String key = obs.getCommonName() + "_" + obs.getCounty();
            ProducerRecord<String, String> record = new ProducerRecord<>(
                KAFKA_TOPIC, key, json.toString()
            );
            
            producer.send(record);
            
            // Only log synthetic data to avoid spam
            if ("SYNTHETIC".equals(dataType)) {
                System.out.println("📤 Sent: " + obs.getCommonName() + 
                                 " count=" + obs.getCount() + 
                                 " in " + obs.getCounty() + 
                                 " (" + dataType + ")");
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}