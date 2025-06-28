# scripts/analyze_seasonal_patterns.py
import pandas as pd
from pathlib import Path

# Get script directory and find project root
script_dir = Path(__file__).parent
project_root = script_dir.parent
csv_file = project_root / "data" / "results_csv" / "mourning_dove_baseline_complete.csv"

# Load the data
df = pd.read_csv(csv_file)

# Get top 3 breeding codes per season (across all years)
seasonal_patterns = df.groupby(['season', 'breeding_code'])['observations'].sum().reset_index()
top_codes_by_season = seasonal_patterns.groupby('season').apply(
    lambda x: x.nlargest(3, 'observations')
).reset_index(drop=True)

print("Top 3 Breeding Codes by Season (2005-2024):")
print(top_codes_by_season)

# Note: Streaming system should weight recent patterns more heavily 
# (s7 baseline becomes more reliable/frequent as years progress)