# scripts/regression_synthetic_generator.py
import pandas as pd
import numpy as np
import matplotlib
matplotlib.use('Agg')  # Use non-interactive backend
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.linear_model import LinearRegression
from sklearn.preprocessing import PolynomialFeatures
from sklearn.pipeline import Pipeline
from sklearn.metrics import r2_score
import json
from pathlib import Path
from datetime import datetime, timedelta
import random
import warnings
warnings.filterwarnings('ignore')

# Set up paths
script_dir = Path(__file__).parent
project_root = script_dir.parent
csv_file = project_root / "data" / "results_csv" / "mourning_dove_baseline_complete.csv"

# Create population data from your SQL results
population_data = {
    'year': [2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014, 
             2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024],
    'birds_counted_sd': [2345, 3299, 4579, 7797, 9723, 12585, 13266, 16554, 19796, 30668,
                        41104, 60324, 88534, 96460, 105462, 145591, 157672, 153099, 164134, 174422],
    'observations_sd': [484, 701, 959, 1431, 1861, 2450, 2880, 3615, 4799, 6947,
                       7966, 10350, 13048, 16520, 18619, 25367, 30266, 32025, 34293, 34963],
    'percent_of_state_total': [5.7, 6.3, 5.8, 8.8, 8.2, 6.9, 6.5, 7.1, 7.5, 8.5,
                              8.2, 10.3, 13.4, 13.6, 13.7, 14.5, 15.4, 14.5, 14.3, 14.5]
}

class MourningDovePopulationPredictor:
    def __init__(self):
        self.pop_df = pd.DataFrame(population_data)
        self.breeding_df = pd.read_csv(csv_file) if csv_file.exists() else None
        self.models = {}
        self.predictions = {}
        
    def plot_population_trends(self):
        """Create comprehensive population trend visualizations"""
        
        print("📊 Skipping plot generation for now (matplotlib backend issue)")
        print("   Population trends analysis:")
        print(f"   2005: {self.pop_df['birds_counted_sd'].iloc[0]:,} birds")
        print(f"   2024: {self.pop_df['birds_counted_sd'].iloc[-1]:,} birds") 
        print(f"   Growth: {self.pop_df['birds_counted_sd'].iloc[-1] / self.pop_df['birds_counted_sd'].iloc[0]:.1f}x")
        
        # Calculate birds per observation for return value
        birds_per_obs = self.pop_df['birds_counted_sd'] / self.pop_df['observations_sd']
        return birds_per_obs
    
    def fit_regression_models(self):
        """Fit various regression models to predict future populations"""
        
        X = self.pop_df['year'].values.reshape(-1, 1)
        
        # 1. Linear Regression for Birds Counted
        linear_model = LinearRegression()
        linear_model.fit(X, self.pop_df['birds_counted_sd'])
        self.models['birds_linear'] = linear_model
        
        # 2. Polynomial Regression for Birds Counted (degree 2)
        poly_model = Pipeline([
            ('poly', PolynomialFeatures(degree=2)),
            ('linear', LinearRegression())
        ])
        poly_model.fit(X, self.pop_df['birds_counted_sd'])
        self.models['birds_poly'] = poly_model
        
        # 3. Linear Regression for Observations
        obs_model = LinearRegression()
        obs_model.fit(X, self.pop_df['observations_sd'])
        self.models['observations'] = obs_model
        
        # Calculate R² scores for model evaluation
        print("🔍 Model Performance (R² scores):")
        for name, model in self.models.items():
            if 'birds' in name:
                y_pred = model.predict(X)
                r2 = r2_score(self.pop_df['birds_counted_sd'], y_pred)
                print(f"  {name}: {r2:.3f}")
            elif 'observations' in name:
                y_pred = model.predict(X)
                r2 = r2_score(self.pop_df['observations_sd'], y_pred)
                print(f"  {name}: {r2:.3f}")
        
        return self.models
    
    def predict_future_populations(self, years_ahead=3):
        """Predict future populations using the best-fitting model"""
        
        future_years = list(range(2025, 2025 + years_ahead))
        future_X = np.array(future_years).reshape(-1, 1)
        
        # Use polynomial model for birds (usually fits growth better)
        future_birds = self.models['birds_poly'].predict(future_X)
        future_observations = self.models['observations'].predict(future_X)
        
        # Create predictions dataframe
        predictions_df = pd.DataFrame({
            'year': future_years,
            'predicted_birds': np.round(future_birds).astype(int),
            'predicted_observations': np.round(future_observations).astype(int),
            'birds_per_observation': future_birds / future_observations
        })
        
        print(f"\n🔮 Population Predictions ({2025}-{2024 + years_ahead}):")
        print(predictions_df.to_string(index=False))
        
        self.predictions = predictions_df
        return predictions_df
    
    def analyze_breeding_patterns(self):
        """Analyze breeding code patterns from your CSV data"""
        
        if self.breeding_df is None:
            print("⚠️  No breeding data CSV found, using default patterns")
            return self.get_default_breeding_patterns()
        
        print("\n🐣 Analyzing Breeding Code Patterns:")
        
        # Seasonal breeding patterns
        seasonal_breeding = self.breeding_df.groupby(['season', 'breeding_code'])['observations'].sum().reset_index()
        
        # Calculate probabilities for each season
        breeding_patterns = {}
        for season in ['Spring', 'Summer', 'Fall', 'Winter']:
            season_data = seasonal_breeding[seasonal_breeding['season'] == season]
            if not season_data.empty:
                total_obs = season_data['observations'].sum()
                season_probs = {}
                
                print(f"\n{season} Breeding Codes:")
                for _, row in season_data.iterrows():
                    probability = row['observations'] / total_obs
                    season_probs[row['breeding_code']] = probability
                    print(f"  {row['breeding_code']}: {probability:.3f} ({row['observations']} obs)")
                
                breeding_patterns[season] = season_probs
            else:
                breeding_patterns[season] = self.get_default_breeding_patterns()[season]
        
        return breeding_patterns
    
    def get_default_breeding_patterns(self):
        """Default breeding patterns if CSV data unavailable"""
        return {
            'Spring': {'S7': 0.4, 'C': 0.3, 'ON': 0.2, 'NB': 0.1},
            'Summer': {'S7': 0.5, 'FL': 0.3, 'C': 0.2},
            'Fall': {'FL': 0.4, 'ON': 0.3, 'NY': 0.3},
            'Winter': {'C': 0.5, 'S7': 0.3, 'ON': 0.2}
        }
    
    def generate_synthetic_observations(self, target_year=2025, num_observations=500):
        """Generate synthetic observations based on regression predictions and breeding patterns"""
        
        if target_year not in self.predictions['year'].values:
            print(f"⚠️  Year {target_year} not in predictions, using 2025")
            target_year = 2025
        
        # Get predicted values for target year
        year_pred = self.predictions[self.predictions['year'] == target_year].iloc[0]
        predicted_birds = year_pred['predicted_birds']
        predicted_obs_count = year_pred['predicted_observations']
        avg_birds_per_obs = year_pred['birds_per_observation']
        
        print(f"\n🤖 Generating {num_observations} synthetic observations for {target_year}")
        print(f"   Predicted total birds: {predicted_birds:,}")
        print(f"   Predicted observations: {predicted_obs_count:,}")
        print(f"   Average birds per observation: {avg_birds_per_obs:.1f}")
        
        # Get breeding patterns
        breeding_patterns = self.analyze_breeding_patterns()
        
        synthetic_observations = []
        
        for i in range(num_observations):
            # Generate random date in target year
            start_date = datetime(target_year, 1, 1)
            days_in_year = 366 if target_year % 4 == 0 else 365
            random_day = random.randint(0, days_in_year - 1)
            obs_date = start_date + timedelta(days=random_day)
            
            # Determine season
            month = obs_date.month
            if 3 <= month <= 5:
                season = 'Spring'
            elif 6 <= month <= 8:
                season = 'Summer'
            elif 9 <= month <= 11:
                season = 'Fall'
            else:
                season = 'Winter'
            
            # Generate count based on predicted average with variation
            base_count = max(1, int(np.random.normal(avg_birds_per_obs, avg_birds_per_obs * 0.3)))
            
            # 3% chance of creating an anomaly for testing
            if random.random() < 0.03:
                count = base_count * random.randint(8, 20)  # 8-20x normal
                is_anomaly = True
            else:
                count = base_count
                is_anomaly = False
            
            # Select breeding code based on seasonal patterns
            season_codes = breeding_patterns.get(season, {'UN': 1.0})
            codes = list(season_codes.keys())
            weights = list(season_codes.values())
            breeding_code = random.choices(codes, weights=weights)[0]
            
            # Generate San Diego coordinates
            # San Diego County rough bounds
            lat_min, lat_max = 32.53, 33.51
            lon_min, lon_max = -117.60, -116.07
            
            latitude = random.uniform(lat_min, lat_max)
            longitude = random.uniform(lon_min, lon_max)
            
            # Create synthetic observation
            synthetic_obs = {
                'id': f"SYNTH_{target_year}_{i:06d}",
                'commonName': 'Mourning Dove',
                'count': count,
                'observationDate': obs_date.isoformat(),
                'latitude': round(latitude, 6),
                'longitude': round(longitude, 6),
                'county': 'San Diego',
                'observerId': f'SYNTH_OBSERVER_{random.randint(1, 200)}',
                'season': season,
                'breedingCode': breeding_code,
                'dataType': 'SYNTHETIC',
                'isAnomaly': is_anomaly,
                'predictedYear': target_year,
                'basePrediction': int(avg_birds_per_obs),
                'timestamp': datetime.now().isoformat()
            }
            
            synthetic_observations.append(synthetic_obs)
            
            if is_anomaly:
                print(f"  🚨 Anomaly generated: {count} birds in {season} (normal: ~{base_count})")
        
        return synthetic_observations
    
    def save_results(self, synthetic_data):
        """Save all results: plots, predictions, and synthetic data"""
        
        # Save predictions
        pred_path = project_root / "data" / "population_predictions.csv"
        self.predictions.to_csv(pred_path, index=False)
        print(f"📈 Predictions saved to: {pred_path}")
        
        # Save synthetic data
        synth_path = project_root / "data" / "synthetic_observations_regression.json"
        with open(synth_path, 'w') as f:
            json.dump(synthetic_data, f, indent=2)
        print(f"🤖 Synthetic data saved to: {synth_path}")
        
        # Save summary statistics
        summary = {
            'generation_date': datetime.now().isoformat(),
            'total_observations': len(synthetic_data),
            'anomalies': sum(1 for obs in synthetic_data if obs['isAnomaly']),
            'seasons': pd.DataFrame(synthetic_data)['season'].value_counts().to_dict(),
            'breeding_codes': pd.DataFrame(synthetic_data)['breedingCode'].value_counts().to_dict(),
            'prediction_model': 'polynomial_regression_degree_2',
            'data_source': 'San Diego County eBird 2005-2024'
        }
        
        summary_path = project_root / "data" / "synthetic_generation_summary.json"
        with open(summary_path, 'w') as f:
            json.dump(summary, f, indent=2)
        print(f"📋 Summary saved to: {summary_path}")

# Run the complete analysis
if __name__ == "__main__":
    print("🐦 Mourning Dove Population Analysis & Synthetic Data Generation")
    print("=" * 60)
    
    predictor = MourningDovePopulationPredictor()
    
    # 1. Plot population trends
    print("\n1️⃣ Plotting population trends...")
    predictor.plot_population_trends()
    
    # 2. Fit regression models
    print("\n2️⃣ Fitting regression models...")
    predictor.fit_regression_models()
    
    # 3. Predict future populations
    print("\n3️⃣ Predicting future populations...")
    predictor.predict_future_populations(years_ahead=3)
    
    # 4. Generate synthetic data
    print("\n4️⃣ Generating synthetic observations...")
    synthetic_data = predictor.generate_synthetic_observations(target_year=2025, num_observations=1000)
    
    # 5. Save everything
    print("\n5️⃣ Saving results...")
    predictor.save_results(synthetic_data)
    
    print(f"\n✅ Complete! Generated {len(synthetic_data)} synthetic observations")
    print(f"   Anomalies: {sum(1 for obs in synthetic_data if obs['isAnomaly'])}")
    print(f"   Ready for Kafka streaming!")
    
    # Print next steps
    print("\n🚀 Next Steps:")
    print("1. Start Kafka: docker-compose up -d")
    print("2. Create topics: ./scripts/create-topics.sh")
    print("3. Run Java DataStreamProducer")
    print("4. Run Java BirdStreamProcessor")
    print("5. Watch for anomaly alerts! 🚨")