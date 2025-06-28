// DataStreamProducer.java - Fixed file paths and enhanced synthetic generation
package com.yvonne.birdstream.producer;

import com.opencsv.CSVReader;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.FileReader;
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
    
    // Updated file path to your actual CSV
    private static final String HISTORICAL_DATA_PATH = "data/results_csv/mourning_dove_baseline_complete.csv";
    
    private KafkaProducer<String, String> producer;
    private Map<String, SpeciesPattern> speciesPatterns;
    
    public static void main(String[] args) {
        DataStreamProducer producer = new DataStreamProducer();
        producer.start();
    }
    
    public void start() {
        initializeKafkaProducer();
        loadHistoricalPatterns();
        
        // Start both streams
        Thread historicalReplay = new Thread(this::replayHistoricalData);
        Thread syntheticGenerator = new Thread(this::generateSyntheticData);
        
        historicalReplay.start();
        syntheticGenerator.start();
        
        try {
            historicalReplay.join();
            syntheticGenerator.join();
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
    }
    
    private void loadHistoricalPatterns() {
        speciesPatterns = new HashMap<>();
        
        try (CSVReader reader = new CSVReader(new FileReader(HISTORICAL_DATA_PATH))) {
            String[] header = reader.readNext(); // Skip header
            String[] line;
            
            System.out.println("Loading historical patterns from: " + HISTORICAL_DATA_PATH);
            
            while ((line = reader.readNext()) != null) {
                // Parse your CSV format: year,season,breeding_code,observations,etc.
                BirdObservation obs = parseMourningDoveObservation(line);
                if (obs != null) {
                    updateSpeciesPattern(obs);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error loading historical patterns: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Loaded patterns for " + speciesPatterns.size() + " species/location combinations");
    }
    
    private BirdObservation parseMourningDoveObservation(String[] line) {
        try {
            // Based on your CSV: year,season,breeding_code,observations,percentage_of_season,rank,source_file,period,analysis_period,decade,breeding_success_indicator,territorial_behavior
            int year = Integer.parseInt(line[0]);
            String season = line[1];
            String breedingCode = line[2];
            int observations = Integer.parseInt(line[3]);
            
            // Create a synthetic observation date based on season and year
            LocalDateTime observationDate = createSeasonalDate(year, season);
            
            // Use realistic California coordinates (you can expand this)
            double[] coords = getRandomCaliforniaCoordinates();
            String county = "California_County_" + ThreadLocalRandom.current().nextInt(20); // Simulate multiple counties
            
            return new BirdObservation(
                UUID.randomUUID().toString(),
                "Mourning Dove", // Your target species
                Math.max(1, observations), // Ensure positive count
                observationDate,
                coords[0], // latitude
                coords[1], // longitude
                county,
                "Historical_Observer_" + year
            );
            
        } catch (Exception e) {
            System.err.println("Error parsing line: " + Arrays.toString(line));
            return null;
        }
    }
    
    private LocalDateTime createSeasonalDate(int year, String season) {
        int month;
        switch (season.toLowerCase()) {
            case "spring": month = 3 + ThreadLocalRandom.current().nextInt(3); break; // Mar-May
            case "summer": month = 6 + ThreadLocalRandom.current().nextInt(3); break; // Jun-Aug
            case "fall": month = 9 + ThreadLocalRandom.current().nextInt(3); break;   // Sep-Nov
            case "winter": month = ThreadLocalRandom.current().nextBoolean() ? 12 : 1 + ThreadLocalRandom.current().nextInt(2); break; // Dec, Jan-Feb
            default: month = ThreadLocalRandom.current().nextInt(12) + 1;
        }
        
        int day = ThreadLocalRandom.current().nextInt(28) + 1; // Safe day range
        return LocalDateTime.of(year, month, day, 8 + ThreadLocalRandom.current().nextInt(10), 0); // 8 AM - 6 PM
    }
    
    private double[] getRandomCaliforniaCoordinates() {
        // California bounding box (approximate)
        double minLat = 32.5, maxLat = 42.0;
        double minLon = -124.5, maxLon = -114.0;
        
        double lat = minLat + (maxLat - minLat) * ThreadLocalRandom.current().nextDouble();
        double lon = minLon + (maxLon - minLon) * ThreadLocalRandom.current().nextDouble();
        
        return new double[]{lat, lon};
    }
    
    private void replayHistoricalData() {
        System.out.println("Starting historical data replay...");
        
        try (CSVReader reader = new CSVReader(new FileReader(HISTORICAL_DATA_PATH))) {
            String[] header = reader.readNext();
            String[] line;
            
            while ((line = reader.readNext()) != null) {
                BirdObservation obs = parseMourningDoveObservation(line);
                
                if (obs != null) {
                    sendToKafka(obs, "HISTORICAL");
                    
                    // Simulate time delay (speed up time: 1 day = 100ms)
                    Thread.sleep(100);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("Historical replay completed");
    }
    
    private void generateSyntheticData() {
        System.out.println("Starting synthetic data generation...");
        
        // Wait a bit for historical data to build patterns
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        
        while (true) {
            try {
                // Generate observations for each species pattern
                for (String speciesKey : speciesPatterns.keySet()) {
                    if (shouldGenerateObservation(speciesKey)) {
                        BirdObservation syntheticObs = generateSyntheticObservation(speciesKey);
                        sendToKafka(syntheticObs, "SYNTHETIC");
                    }
                }
                
                Thread.sleep(2000); // Generate every 2 seconds
                
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
        
        // Higher probability during peak months for this species
        double probability = pattern.getMonthlyProbability(currentMonth);
        return ThreadLocalRandom.current().nextDouble() < probability * 0.01; // Scale down for demo
    }
    
    private BirdObservation generateSyntheticObservation(String speciesKey) {
        SpeciesPattern pattern = speciesPatterns.get(speciesKey);
        LocalDateTime now = LocalDateTime.now();
        
        // Generate count with some variation (occasionally create anomalies)
        int baseCount = Math.max(1, (int) pattern.getAverageCount());
        int count;
        
        // 5% chance of creating an anomaly (very high count)
        if (ThreadLocalRandom.current().nextDouble() < 0.05) {
            count = baseCount * (5 + ThreadLocalRandom.current().nextInt(10)); // 5-15x normal
            System.out.println("🎯 Generating ANOMALY: " + count + " (normal: " + baseCount + ")");
        } else {
            // Normal variation
            double stdDev = Math.max(1.0, pattern.getCountStdDev());
            count = Math.max(1, (int) (baseCount + ThreadLocalRandom.current().nextGaussian() * stdDev));
        }
        
        // Pick a location from historical observations
        LocationData location = pattern.getRandomLocation();
        
        return new BirdObservation(
            UUID.randomUUID().toString(),
            "Mourning Dove", // Your target species
            count,
            now,
            location.getLatitude(),
            location.getLongitude(),
            location.getCounty(),
            "SYNTHETIC_OBSERVER_" + ThreadLocalRandom.current().nextInt(1000)
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
            
            if ("SYNTHETIC".equals(dataType)) {
                System.out.println("📤 Sent synthetic: " + obs.getCommonName() + 
                                 " count=" + obs.getCount() + " in " + obs.getCounty());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}