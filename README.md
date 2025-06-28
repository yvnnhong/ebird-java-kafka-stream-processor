# eBird Java Kafka Stream Processor

**Note**: This project utilizes a 47GB eBird dataset for California bird observations (2005-2025). The original raw dataset was cleaned and processed down to focused subsets for analysis and streaming demonstration purposes.

In this project, I focused specifically on the mourning dove population and nesting behaviours
in San Diego from 2005-2024. 

My project is a real-time bird observation data processing system that combines historical eBird data analysis with synthetic data generation to demonstrate streaming data engineering capabilities and anomaly detection.

## Overview

My project processes 20 years of California bird observation data (2005-2024) using regression-based prediction models to generate synthetic observations and detect statistical anomalies in real-time using Apache Kafka stream processing.

### Key Features

- **Historical Data Analysis**: Processes 447 Mourning Dove observations from San Diego County
- **Regression-Based Prediction**: Uses polynomial regression (R² = 0.969) for 2025 population forecasts  
- **Synthetic Data Generation**: Creates 1,000 realistic observations with controlled anomalies
- **Real-Time Anomaly Detection**: Z-score analysis with <100ms processing latency
- **Apache Kafka Integration**: Production-ready streaming architecture with fault tolerance

## Performance Results

- **Precision: 100.0%** (no false positives)
- **Recall: 96.9%** (detected 31 of 32 true anomalies)
- **F1-Score: 98.4%** (exceeds industry standards)
- **Processing Speed**: 1,000+ observations per session
- **Zero False Positives**: Perfect precision for production deployment

## Technology Stack

- **Java 23** - Core streaming applications and business logic
- **Apache Kafka 3.5.0** - Message streaming platform and event processing
- **Python 3.13** - Data analysis, regression modeling, and visualization
- **Docker & Docker Compose** - Containerized deployment and orchestration
- **Maven** - Dependency management and build automation
- **Jackson JSON** - Data serialization and API integration
- **OpenCSV** - CSV file processing and data ingestion

## Project Structure

```
ebird-java-kafka-stream-processor/
├── README.md                                    # Project documentation
├── pom.xml                                      # Maven dependencies and build configuration
├── docker-compose.yml                          # Kafka infrastructure setup
│
├── src/main/java/com/yvonne/birdstream/
│   ├── model/
│   │   ├── BirdObservation.java                # Data model for observations
│   │   └── LocationData.java                   # Geographic data model
│   ├── producer/
│   │   ├── DataStreamProducer.java             # Streams historical and synthetic data
│   │   └── SpeciesPattern.java                 # Learns species behavioral patterns
│   └── processor/
│       ├── BirdStreamProcessor.java            # Real-time anomaly detection
│       └── SpeciesBaseline.java                # Statistical baseline calculations
│
├── scripts/
│   ├── regression_synthetic_generator.py       # Generates synthetic data using regression
│   ├── analyze_pipeline_results.py             # Performance analysis and visualization
│   ├── analyze_seasonal_patterns.py            # Seasonal pattern analysis
│   └── test_integration.py                     # Integration testing utilities
│
├── data/
│   ├── results_csv/                            # Processed historical datasets
│   │   ├── baseline_2005_2009.csv              # Early eBird period baseline
│   │   ├── baseline_2010_2014.csv              # Growth period baseline
│   │   ├── baseline_2015_2017.csv              # Expansion period baseline
│   │   ├── baseline_2018_2019.csv              # Maturation period baseline
│   │   ├── baseline_2020_2022.csv              # COVID impact period baseline
│   │   ├── baseline_2023_2024.csv              # Recent period baseline
│   │   └── mourning_dove_baseline_complete.csv # Master combined dataset (447 observations)
│   ├── results_txt/                            # Raw SQL query outputs
│   │   ├── breeding_baseline_2005_2009.txt     # Historical breeding pattern analysis
│   │   ├── breeding_baseline_2010_2014.txt     # Growth period breeding patterns
│   │   ├── breeding_baseline_2015_2017.txt     # Expansion period breeding patterns
│   │   ├── breeding_baseline_2018_2019.txt     # Maturation period breeding patterns
│   │   ├── breeding_baseline_2020_2022.txt     # COVID period breeding patterns
│   │   ├── breeding_baseline_2023_2024.txt     # Recent breeding patterns
│   │   ├── cleaned_breeding_codes_distribution.txt # Breeding code analysis
│   │   └── total_annual_populations.txt        # Population trend analysis
│   ├── sql_queries/                            # SQL analysis scripts
│   │   ├── breeding_baseline_2005_2009.sql     # Historical period analysis
│   │   ├── breeding_baseline_2010_2014.sql     # Growth period analysis
│   │   ├── breeding_baseline_2015_2017.sql     # Expansion period analysis
│   │   ├── breeding_baseline_2018_2019.sql     # Maturation period analysis
│   │   ├── breeding_baseline_2020_2022.sql     # COVID impact analysis
│   │   ├── breeding_baseline_2023_2024.sql     # Recent period analysis
│   │   ├── cleaned_breeding_codes_distribution.sql # Breeding behavior analysis
│   │   ├── total_annual_populations.sql        # Population trend queries
│   │   ├── data_quality_checks.sql             # Data validation queries
│   │   ├── export_filtered_data.sql            # Data export utilities
│   │   └── stable_species_analysis.sql         # Species stability analysis
│   ├── synthetic_observations_regression.json   # Generated synthetic dataset (1,000 observations)
│   ├── population_predictions.csv              # Regression model predictions (2025-2027)
│   └── synthetic_generation_summary.json       # Synthetic data generation metadata
│
├── documentation/
│   ├── 01_producer_streaming_progress.png      # Producer streaming demonstration
│   ├── 02_processor_first_anomaly_72_birds.png # First anomaly detection (Z-score: 7.66)
│   ├── 03_processor_second_anomaly_120_birds.png # Second anomaly detection (Z-score: 13.33)
│   └── 04_producer_third_anomaly_96_birds.png  # Third anomaly detection (96 birds)
│
├── results/
│   ├── pipeline_analysis_results.png           # Comprehensive performance visualizations
│   └── pipeline_performance_report.txt         # Detailed statistical analysis report
│
├── prototyping/
│   ├── regression_prototype.js                 # JavaScript proof-of-concept validation
│   ├── population_analysis.js                  # Data exploration prototype
│   └── README.md                              # Prototyping methodology documentation
│
└── target/                                     # Maven build outputs
    ├── classes/                                # Compiled Java classes
    ├── dependency/                             # JAR dependencies
    └── ebird-processor-1.0-SNAPSHOT.jar        # Executable application JAR
```

## Quick Start

### Prerequisites

- Java 23+
- Python 3.13+
- Docker Desktop
- Maven 3.6+

### Installation & Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ebird-java-kafka-stream-processor
   ```

2. **Start Kafka infrastructure**
   ```bash
   docker-compose up -d
   ```

3. **Create Kafka topics**
   ```bash
   docker exec kafka kafka-topics --create --topic bird-observations --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
   docker exec kafka kafka-topics --create --topic bird-alerts --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
   ```

4. **Generate synthetic data**
   ```bash
   cd scripts
   python regression_synthetic_generator.py
   cd ..
   ```

5. **Compile Java applications**
   ```bash
   mvn clean package
   ```

### Running the Pipeline

**Terminal 1 - Start Stream Processor:**
```bash
java -cp "target/classes;target/dependency/*" com.yvonne.birdstream.processor.BirdStreamProcessor
```

**Terminal 2 - Start Data Producer:**
```bash
java -cp "target/classes;target/dependency/*" com.yvonne.birdstream.producer.DataStreamProducer
```

### Expected Output

The processor will display:
- Historical data baseline building: `Baseline established for Mourning Dove_San Diego (mean=7.2)`
- Real-time observations: `Received SYNTHETIC: Mourning Dove count=6`
- Anomaly detection: `Anomaly detected: Mourning Dove in San Diego - Count: 120, Z-score: 13.33`
- Alert generation: `ALERT: {"alertType":"UNUSUAL_COUNT","severity":"HIGH"...}`

## Data Pipeline Flow

```
Historical Data (CSV) → Baseline Learning → Synthetic Data Generation → Kafka Streaming → Anomaly Detection → Alerts
```

1. **Historical Analysis**: Processes 20 years of eBird data to establish baseline patterns
2. **Synthetic Generation**: Creates 1,000 observations using regression predictions with 3% controlled anomalies
3. **Stream Processing**: Kafka handles real-time data flow between producer and processor
4. **Anomaly Detection**: Z-score analysis flags observations >3 standard deviations from baseline
5. **Alert System**: Generates structured JSON alerts for unusual observations

## Key Algorithms

### Regression-Based Prediction
- **Model**: Polynomial regression (degree 2)
- **Performance**: R² = 0.969 on historical data
- **Prediction**: 215,280 birds forecasted for 2025 (6.5 birds/observation average)

### Anomaly Detection
- **Method**: Z-score statistical analysis
- **Threshold**: |Z-score| > 3.0 standard deviations
- **Baseline**: Learned from historical observations (minimum 20 samples required)
- **Processing**: Real-time evaluation with <100ms latency

### Synthetic Data Features
- **Seasonal Patterns**: Preserves breeding behavior distributions by season
- **Geographic Accuracy**: San Diego County coordinate boundaries (32.53°-33.51°N, 117.60°-116.07°W)
- **Controlled Anomalies**: 32 intentional outliers (8-20x normal counts) for testing
- **Realistic Variation**: Normal observations follow learned statistical distributions

## Results Analysis

Run the comprehensive analysis:
```bash
cd scripts
python analyze_pipeline_results.py
```

This generates:
- **Performance visualizations** showing count distributions, confusion matrix, and time series analysis
- **Statistical report** with precision, recall, F1-score, and detailed performance metrics
- **Validation analysis** comparing detected vs. true anomalies

## Business Applications

### Wildlife Conservation
- Early detection of population crashes or migration disruptions
- Real-time monitoring for endangered species protection
- Climate change impact assessment through behavioral pattern analysis

### Research & Academia  
- Automated quality control for citizen science data (eBird platform)
- Real-time validation of unusual wildlife observations
- Long-term ecological trend identification and forecasting

### Production Deployment
- Horizontally scalable architecture across multiple geographic regions
- RESTful API integration for external monitoring systems
- Built-in metrics and alerting for operational monitoring

## Development Methodology

This project demonstrates iterative development practices:

1. **Prototyping Phase**: JavaScript proof-of-concept for rapid algorithm validation
2. **Data Analysis Phase**: Python-based regression modeling and statistical analysis  
3. **Production Phase**: Java-based streaming applications with enterprise capabilities
4. **Validation Phase**: Comprehensive testing and performance analysis

## Future Enhancements

- **Multi-species Support**: Extend to additional bird species with species-specific baselines
- **Machine Learning Integration**: Advanced anomaly detection using isolation forests or neural networks
- **Geographic Expansion**: Scale to state-wide and national-level monitoring
- **Environmental Data**: Integration with weather and habitat data for enhanced predictions
- **Web Dashboard**: Real-time visualization interface for monitoring and alerts

## Data Attribution

This project uses data from the eBird database (https://ebird.org), a citizen science platform operated by the Cornell Lab of Ornithology. The original dataset contains bird observation records contributed by volunteer observers worldwide.

## License

This project is a personal portfolio demonstration of data engineering and stream processing capabilities.

## Contact

**Author**: Yvonne Hong  
**Project Type**: Data Engineering & Stream Processing Portfolio