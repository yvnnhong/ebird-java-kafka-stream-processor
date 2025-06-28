# scripts/analyze_seasonal_patterns.py
#note for tomorrow: double check the output (just did this nvm)
#note for tomorrow: make sure that the streaming logic will weight the 
#s7 more heavily as the years go by
# streaming system should weight recent patterns 
# more heavily (s7 baselin becomes more reliable/frequent)
import pandas as pd

df = pd.read_csv('../data/results_csv/mourning_dove_baseline_complete.csv')

# Get top 3 breeding codes per season (across all years)
seasonal_patterns = df.groupby(['season', 'breeding_code'])['observations'].sum().reset_index()
top_codes_by_season = seasonal_patterns.groupby('season').apply(
    lambda x: x.nlargest(3, 'observations')
).reset_index(drop=True)

print("Top 3 Breeding Codes by Season (2005-2024):")
print(top_codes_by_season)