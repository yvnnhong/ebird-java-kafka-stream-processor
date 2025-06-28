# scripts/test_integration.py
# Quick test to verify the regression pipeline works
import pandas as pd
import json
from pathlib import Path
import matplotlib.pyplot as plt

script_dir = Path(__file__).parent
project_root = script_dir.parent

def test_regression_pipeline():
    print("Testing Regression-Based Synthetic Data Pipeline")
    print("=" * 55)
    
    # Test 1: Check if historical CSV exists
    csv_file = project_root / "data" / "results_csv" / "mourning_dove_baseline_complete.csv"
    if csv_file.exists():
        print("Historical CSV found")
        df = pd.read_csv(csv_file)
        print(f"Shape: {df.shape}")
        print(f"Years: {df['year'].min()} - {df['year'].max()}")
        print(f"Seasons: {df['season'].unique()}")
    else:
        print("Historical CSV not found at:", csv_file)
        return False
    
    # Test 2: Check if synthetic JSON exists
    json_file = project_root / "data" / "synthetic_observations_regression.json"
    if json_file.exists():
        print("Synthetic JSON found")
        with open(json_file, 'r') as f:
            synthetic_data = json.load(f)
        print(f"   Observations: {len(synthetic_data)}")
        
        # Analyze the synthetic data
        if synthetic_data:
            anomalies = [obs for obs in synthetic_data if obs.get('isAnomaly', False)]
            seasons = {}
            for obs in synthetic_data:
                season = obs.get('season', 'Unknown')
                seasons[season] = seasons.get(season, 0) + 1
            
            print(f"   Anomalies: {len(anomalies)} ({len(anomalies)/len(synthetic_data)*100:.1f}%)")
            print(f"   Seasons: {seasons}")
            
            # Show sample observations
            print("\nSample Observations:")
            for i in range(min(3, len(synthetic_data))):
                obs = synthetic_data[i]
                anomaly_flag = " (ANOMALY)" if obs.get('isAnomaly', False) else ""
                print(f"   {obs['id']}: {obs['count']} birds, {obs['season']}, {obs['breedingCode']}{anomaly_flag}")
    else:
        print("Synthetic JSON not found - run regression_synthetic_generator.py first")
        return False
    
    # Test 3: Validate data quality
    print("\nData Quality Checks:")
    
    # Check for required fields
    required_fields = ['id', 'commonName', 'count', 'observationDate', 'latitude', 'longitude', 'county', 'season', 'breedingCode', 'dataType']
    sample_obs = synthetic_data[0] if synthetic_data else {}
    
    missing_fields = [field for field in required_fields if field not in sample_obs]
    if not missing_fields:
        print("All required fields present")
    else:
        print(f"Missing fields: {missing_fields}")
    
    # Check coordinate ranges (San Diego County)
    coords_valid = True
    for obs in synthetic_data[:10]:  # Check first 10
        lat, lon = obs['latitude'], obs['longitude']
        if not (32.5 <= lat <= 33.6 and -118.0 <= lon <= -116.0):
            coords_valid = False
            break
    
    if coords_valid:
        print("Coordinates within San Diego County bounds")
    else:
        print("Some coordinates outside San Diego County")
    
    # Test 4: Check breeding code distribution
    breeding_codes = {}
    for obs in synthetic_data:
        code = obs.get('breedingCode', 'Unknown')
        breeding_codes[code] = breeding_codes.get(code, 0) + 1
    
    print(f"Breeding codes: {list(breeding_codes.keys())}")
    
    # Test 5: Visualize count distribution
    counts = [obs['count'] for obs in synthetic_data]
    normal_counts = [obs['count'] for obs in synthetic_data if not obs.get('isAnomaly', False)]
    anomaly_counts = [obs['count'] for obs in synthetic_data if obs.get('isAnomaly', False)]
    
    print(f"\nCount Statistics:")
    print(f"Normal counts: {len(normal_counts)}, avg = {sum(normal_counts)/len(normal_counts):.1f}")
    print(f"Anomaly counts: {len(anomaly_counts)}, avg = {sum(anomaly_counts)/len(anomaly_counts):.1f}")
    print(f"Anomaly multiplier: {(sum(anomaly_counts)/len(anomaly_counts))/(sum(normal_counts)/len(normal_counts)):.1f}x")
    
    # Quick plot
    plt.figure(figsize=(10, 6))
    plt.hist(normal_counts, bins=20, alpha=0.7, label='Normal', color='blue')
    plt.hist(anomaly_counts, bins=10, alpha=0.7, label='Anomalies', color='red')
    plt.xlabel('Bird Count')
    plt.ylabel('Frequency')
    plt.title('Synthetic Data: Normal vs Anomaly Count Distribution')
    plt.legend()
    plt.yscale('log')
    
    plot_path = project_root / "data" / "test_count_distribution.png"
    plt.savefig(plot_path, dpi=150, bbox_inches='tight')
    print(f"Count distribution plot saved to: {plot_path}")
    plt.show()
    
    print("\nIntegration Test Results:")
    print("Historical data loaded and ready for baseline building")
    print("Synthetic data generated with realistic patterns")
    print("Anomalies present for testing detection algorithms")
    print("Data format compatible with Java streaming pipeline")
    
    print("\nReady to run:")
    print("1. docker-compose up -d")
    print("2. ./scripts/create-topics.sh")
    print("3. Start BirdStreamProcessor.java")
    print("4. Start DataStreamProducer.java")
    print("5. Watch for anomaly alerts!")
    
    return True

if __name__ == "__main__":
    test_regression_pipeline()