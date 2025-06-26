-- Mourning Dove Seasonal Breeding Baseline: San Diego County 
-- Part 2: Years 2010-2014 (Complete baseline distribution)
-- Creates baseline distribution of top 3 breeding codes per season for anomaly detection
-- Excludes non-breeding behaviors (F, H, S, P)

WITH seasonal_breeding_data AS (
    SELECT
        EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) as observation_year,
        CASE
            WHEN EXTRACT(MONTH FROM CAST("OBSERVATION DATE" AS DATE)) IN (3,4,5) THEN 'Spring'
            WHEN EXTRACT(MONTH FROM CAST("OBSERVATION DATE" AS DATE)) IN (6,7,8) THEN 'Summer'
            WHEN EXTRACT(MONTH FROM CAST("OBSERVATION DATE" AS DATE)) IN (9,10,11) THEN 'Fall'
            WHEN EXTRACT(MONTH FROM CAST("OBSERVATION DATE" AS DATE)) IN (12,1,2) THEN 'Winter'
        END as season,
        TRIM("BREEDING CODE") as breeding_code
    FROM read_csv_auto('C:/Data/Birds/ebird_observation_data.txt',
                       max_line_size=25000000,
                       strict_mode=false,
                       ignore_errors=true)
    WHERE 
        (LOWER("COMMON NAME") LIKE '%mourning dove%'
         OR LOWER("SCIENTIFIC NAME") LIKE '%zenaida macroura%')
        AND "STATE" = 'California'
        AND "COUNTY" = 'San Diego'
        AND EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) BETWEEN 2010 AND 2014
        AND "BREEDING CODE" IS NOT NULL
        AND "BREEDING CODE" != ''
        AND TRIM("BREEDING CODE") NOT IN ('F', 'H', 'S', 'P')
),

yearly_seasonal_counts AS (
    SELECT 
        observation_year,
        season,
        breeding_code,
        COUNT(*) as observations,
        ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY observation_year, season), 1) as percentage_of_season
    FROM seasonal_breeding_data
    GROUP BY observation_year, season, breeding_code
),

ranked_codes_by_year AS (
    SELECT *,
        ROW_NUMBER() OVER (PARTITION BY observation_year, season ORDER BY observations DESC) as rank_in_season
    FROM yearly_seasonal_counts
),

top_codes_per_year_season AS (
    SELECT 
        observation_year,
        season,
        breeding_code,
        observations,
        percentage_of_season,
        rank_in_season
    FROM ranked_codes_by_year
    WHERE rank_in_season <= 3
)

SELECT 
    observation_year as year,
    season,
    breeding_code,
    observations,
    percentage_of_season,
    rank_in_season as rank
FROM top_codes_per_year_season
ORDER BY observation_year,
         CASE season 
             WHEN 'Spring' THEN 1 
             WHEN 'Summer' THEN 2 
             WHEN 'Fall' THEN 3 
             WHEN 'Winter' THEN 4 
         END,
         rank_in_season;