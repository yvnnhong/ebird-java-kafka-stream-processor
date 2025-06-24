// DataStreamProducer.java - Hybrid historical replay + synthetic data generator
package main.java.com.yvonne.birdstream.producer;

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

public class DataStreamProducer {
    
    private static final String KAFKA_TOPIC = "bird-observations";
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final ObjectMapper mapper = new ObjectMapper();
    
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
        
        try (CSVReader reader = new CSVReader(new FileReader("data/ebird_california_2005_2025.csv"))) {
            String[] header = reader.readNext();
            String[] line;
            
            while ((line = reader.readNext()) != null) {
                BirdObservation obs = parseObservation(header, line);
                if (isTargetSpecies(obs.getCommonName())) {
                    updateSpeciesPattern(obs);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("Loaded patterns for " + speciesPatterns.size() + " species");
    }
    
    private void replayHistoricalData() {
        System.out.println("Starting historical data replay...");
        
        try (CSVReader reader = new CSVReader(new FileReader("data/ebird_california_2005_2025.csv"))) {
            String[] header = reader.readNext();
            String[] line;
            
            while ((line = reader.readNext()) != null) {
                BirdObservation obs = parseObservation(header, line);
                
                // Only replay certain species to avoid overwhelming
                if (isTargetSpecies(obs.getCommonName())) {
                    sendToKafka(obs, "HISTORICAL");
                    
                    // Simulate time delay (speed up time: 1 day = 100ms)
                    Thread.sleep(100);
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void generateSyntheticData() {
        System.out.println("Starting synthetic data generation...");
        
        while (true) {
            try {
                // Generate observations for each species based on learned patterns
                for (String species : speciesPatterns.keySet()) {
                    if (shouldGenerateObservation(species)) {
                        BirdObservation syntheticObs = generateSyntheticObservation(species);
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
    
    private boolean shouldGenerateObservation(String species) {
        SpeciesPattern pattern = speciesPatterns.get(species);
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        
        // Higher probability during peak months for this species
        double probability = pattern.getMonthlyProbability(currentMonth);
        return ThreadLocalRandom.current().nextDouble() < probability;
    }
    
    private BirdObservation generateSyntheticObservation(String species) {
        SpeciesPattern pattern = speciesPatterns.get(species);
        LocalDateTime now = LocalDateTime.now();
        
        // Add some realistic variation
        int count = Math.max(1, (int) (pattern.getAverageCount() + 
                   ThreadLocalRandom.current().nextGaussian() * pattern.getCountStdDev()));
        
        // Pick a random location from historical observations
        LocationData location = pattern.getRandomLocation();
        
        return new BirdObservation(
            UUID.randomUUID().toString(),
            species,
            count,
            now,
            location.latitude,
            location.longitude,
            location.county,
            "SYNTHETIC_OBSERVER_" + ThreadLocalRandom.current().nextInt(100)
        );
    }
    
    private boolean isTargetSpecies(String commonName) {
        // Focus on predictable species for better synthetic data
        Set<String> targetSpecies = Set.of(
            "House Finch",
            "California Scrub-Jay",
            "Anna's Hummingbird",
            "Oak Titmouse",
            "White-crowned Sparrow",
            "Yellow Warbler",
            "Red-tailed Hawk"
        );
        return targetSpecies.contains(commonName);
    }
    
    private void updateSpeciesPattern(BirdObservation obs) {
        speciesPatterns.computeIfAbsent(obs.getCommonName(), k -> new SpeciesPattern())
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
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private BirdObservation parseObservation(String[] header, String[] line) {
        // Simple parsing - adjust column indices based on your CSV structure
        return new BirdObservation(
            line[0], // GLOBAL UNIQUE IDENTIFIER
            line[6], // COMMON NAME
            parseIntSafely(line[10]), // OBSERVATION COUNT
            LocalDateTime.parse(line[30], DateTimeFormatter.ofPattern("yyyy-MM-dd")), // OBSERVATION DATE
            Double.parseDouble(line[28]), // LATITUDE
            Double.parseDouble(line[29]), // LONGITUDE
            line[20], // COUNTY
            line[33]  // OBSERVER ID
        );
    }
    
    private int parseIntSafely(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("X")) {
            return 1; // Default for presence-only records
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}

// Supporting classes
class BirdObservation {
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

class SpeciesPattern {
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

class LocationData {
    final double latitude;
    final double longitude;
    final String county;
    
    public LocationData(double latitude, double longitude, String county) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.county = county;
    }
}