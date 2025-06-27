#!/usr/bin/env python3

import pandas as pd
import os
from pathlib import Path
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

def setup_paths():
    """Setup file paths relative to script location"""
    script_dir = Path(__file__).parent
    project_root = script_dir.parent
    
    csv_dir = project_root / "data" / "results_csv"
    output_file = csv_dir / "mourning_dove_baseline_complete.csv"
    
    return csv_dir, output_file

def get_baseline_files(csv_dir):
    """Get all baseline CSV files in chronological order"""
    baseline_files = [
        "baseline_2005_2009.csv",
        "baseline_2010_2014.csv", 
        "baseline_2015_2017.csv",
        "baseline_2018_2019.csv",
        "baseline_2020_2022.csv",
        "baseline_2023_2024.csv"
    ]
    
    # Verify all files exist
    missing_files = []
    for filename in baseline_files:
        file_path = csv_dir / filename
        if not file_path.exists():
            missing_files.append(filename)
    
    if missing_files:
        raise FileNotFoundError(f"Missing baseline files: {missing_files}")
    
    return baseline_files

def load_and_combine_data(csv_dir, baseline_files):
    """Load all CSV files and combine into single DataFrame"""
    dataframes = []
    
    for filename in baseline_files:
        file_path = csv_dir / filename
        logger.info(f"Loading {filename}")
        
        try:
            df = pd.read_csv(file_path)
            
            # Validate required columns
            required_cols = ['year', 'season', 'breeding_code', 'observations', 'percentage_of_season', 'rank']
            missing_cols = [col for col in required_cols if col not in df.columns]
            if missing_cols:
                raise ValueError(f"Missing columns in {filename}: {missing_cols}")
            
            # Add source file for tracking
            df['source_file'] = filename
            df['period'] = filename.replace('baseline_', '').replace('.csv', '')
            
            dataframes.append(df)
            logger.info(f"  → Loaded {len(df)} rows from {filename}")
            
        except Exception as e:
            logger.error(f"Error loading {filename}: {e}")
            raise
    
    # Combine all dataframes
    combined_df = pd.concat(dataframes, ignore_index=True)
    logger.info(f"Combined total: {len(combined_df)} rows")
    
    return combined_df

def clean_and_validate_data(df):
    """Clean and validate the combined dataset"""
    logger.info("Cleaning and validating data...")
    
    original_rows = len(df)
    
    # Remove any duplicate rows
    df = df.drop_duplicates()
    if len(df) < original_rows:
        logger.info(f"  → Removed {original_rows - len(df)} duplicate rows")
    
    # Validate data types
    df['year'] = pd.to_numeric(df['year'], errors='coerce')
    df['observations'] = pd.to_numeric(df['observations'], errors='coerce')
    df['percentage_of_season'] = pd.to_numeric(df['percentage_of_season'], errors='coerce')
    df['rank'] = pd.to_numeric(df['rank'], errors='coerce')
    
    # Check for any NaN values after conversion
    nan_rows = df.isnull().any(axis=1).sum()
    if nan_rows > 0:
        logger.warning(f"Found {nan_rows} rows with NaN values")
        df = df.dropna()
        logger.info(f"  → Removed rows with NaN values")
    
    # Validate year range
    min_year, max_year = df['year'].min(), df['year'].max()
    if min_year < 2005 or max_year > 2024:
        logger.warning(f"Year range outside expected bounds: {min_year}-{max_year}")
    
    # Validate seasons
    valid_seasons = ['Spring', 'Summer', 'Fall', 'Winter']
    invalid_seasons = df[~df['season'].isin(valid_seasons)]['season'].unique()
    if len(invalid_seasons) > 0:
        logger.warning(f"Invalid seasons found: {invalid_seasons}")
    
    logger.info(f"Data validation complete: {len(df)} rows retained")
    return df

def add_metadata_columns(df):
    """Add useful metadata columns for analysis"""
    logger.info("Adding metadata columns...")
    
    # Add period groupings for analysis
    conditions = [
        (df['year'] <= 2009),
        (df['year'] >= 2010) & (df['year'] <= 2014),
        (df['year'] >= 2015) & (df['year'] <= 2017),
        (df['year'] >= 2018) & (df['year'] <= 2019),
        (df['year'] >= 2020) & (df['year'] <= 2022),
        (df['year'] >= 2023) & (df['year'] <= 2024)
    ]
    
    period_labels = [
        'Early_eBird_2005_2009',
        'Growth_2010_2014', 
        'Expansion_2015_2017',
        'Maturation_2018_2019',
        'COVID_Impact_2020_2022',
        'Recent_2023_2024'
    ]
    
    df['analysis_period'] = pd.cut(df['year'], 
                                  bins=[2004, 2009, 2014, 2017, 2019, 2022, 2024], 
                                  labels=period_labels, 
                                  include_lowest=True)
    
    # Add decade column
    df['decade'] = (df['year'] // 10) * 10
    
    # Add breeding success indicators
    success_codes = ['FL', 'NY', 'NE', 'ON']  # Fledged young, nest with young, nest with eggs, occupied nest
    df['breeding_success_indicator'] = df['breeding_code'].isin(success_codes)
    
    # Add territorial behavior indicators  
    territorial_codes = ['S7', 'T', 'C']  # Singing 7+ days, territorial, courtship
    df['territorial_behavior'] = df['breeding_code'].isin(territorial_codes)
    
    logger.info("  → Added analysis_period, decade, breeding_success_indicator, territorial_behavior columns")
    return df

def generate_summary_stats(df):
    """Generate and log summary statistics"""
    logger.info("\n" + "="*50)
    logger.info("MOURNING DOVE BASELINE SUMMARY STATISTICS")
    logger.info("="*50)
    
    # Overall stats
    logger.info(f"Total observations: {len(df):,}")
    logger.info(f"Year range: {df['year'].min()}-{df['year'].max()}")
    logger.info(f"Unique breeding codes: {df['breeding_code'].nunique()}")
    logger.info(f"Total birds observed: {df['observations'].sum():,}")
    
    # By period
    logger.info("\nObservations by period:")
    period_counts = df.groupby('analysis_period')['observations'].agg(['count', 'sum'])
    for period, data in period_counts.iterrows():
        logger.info(f"  {period}: {data['count']} records, {data['sum']:,} birds")
    
    # Top breeding codes
    logger.info("\nTop 5 breeding codes:")
    top_codes = df.groupby('breeding_code')['observations'].sum().sort_values(ascending=False).head()
    for code, count in top_codes.items():
        percentage = (count / df['observations'].sum()) * 100
        logger.info(f"  {code}: {count:,} observations ({percentage:.1f}%)")
    
    # Seasonal patterns
    logger.info("\nSeasonal distribution:")
    seasonal = df.groupby('season')['observations'].sum().sort_values(ascending=False)
    for season, count in seasonal.items():
        percentage = (count / df['observations'].sum()) * 100
        logger.info(f"  {season}: {count:,} observations ({percentage:.1f}%)")
    
    logger.info("="*50)

def save_combined_data(df, output_file):
    """Save the combined dataset to CSV"""
    logger.info(f"Saving combined dataset to: {output_file}")
    
    # Sort by year, season, then by observations (descending)
    season_order = ['Spring', 'Summer', 'Fall', 'Winter']
    df['season_order'] = df['season'].map({season: i for i, season in enumerate(season_order)})
    df_sorted = df.sort_values(['year', 'season_order', 'observations'], ascending=[True, True, False])
    df_final = df_sorted.drop('season_order', axis=1)
    
    try:
        df_final.to_csv(output_file, index=False)
        logger.info(f"  → Successfully saved {len(df_final)} rows to {output_file}")
        
        # Verify file was created and get size
        file_size = output_file.stat().st_size / 1024  # KB
        logger.info(f"  → File size: {file_size:.1f} KB")
        
    except Exception as e:
        logger.error(f"Error saving file: {e}")
        raise

def main():
    """Main execution function"""
    try:
        logger.info("Starting Mourning Dove Baseline Data Combination")
        logger.info("=" * 60)
        
        # Setup paths
        csv_dir, output_file = setup_paths()
        logger.info(f"CSV directory: {csv_dir}")
        logger.info(f"Output file: {output_file}")
        
        # Get baseline files
        baseline_files = get_baseline_files(csv_dir)
        logger.info(f"Found {len(baseline_files)} baseline files")
        
        # Load and combine data
        combined_df = load_and_combine_data(csv_dir, baseline_files)
        
        # Clean and validate
        clean_df = clean_and_validate_data(combined_df)
        
        # Add metadata
        final_df = add_metadata_columns(clean_df)
        
        # Generate summary
        generate_summary_stats(final_df)
        
        # Save combined dataset
        save_combined_data(final_df, output_file)
        
        logger.info("\n SUCCESS: Baseline combination completed!")
        logger.info(f"Output: {output_file}")
        logger.info(f"Total records: {len(final_df):,}")
        
    except Exception as e:
        logger.error(f"ERROR: {e}")
        raise

if __name__ == "__main__":
    main()