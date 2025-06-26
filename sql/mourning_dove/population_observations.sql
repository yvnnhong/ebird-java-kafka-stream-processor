-- Mourning Dove Population Analysis: California Counties (2005-2024)
--
-- Analyzes yearly bird counts and county distribution patterns
-- to assess population stability for synthetic data generation

WITH mourning_dove_data AS (
    SELECT 
        EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) as observation_year,
        "COUNTY",
        
        -- Parse observation count (handle 'X' values)
        CASE 
            WHEN UPPER(TRIM("OBSERVATION COUNT")) = 'X' THEN 1
            WHEN TRY_CAST("OBSERVATION COUNT" AS INTEGER) IS NOT NULL 
                 AND CAST("OBSERVATION COUNT" AS INTEGER) > 0 
                 AND CAST("OBSERVATION COUNT" AS INTEGER) < 1000
            THEN CAST("OBSERVATION COUNT" AS INTEGER)
            ELSE NULL
        END as parsed_count
        
    FROM read_csv_auto('C:/Data/Birds/ebird_observation_data.txt',
                       max_line_size=25000000,
                       strict_mode=false,
                       ignore_errors=true)
    WHERE
        -- Target Mourning Dove
        (LOWER("COMMON NAME") LIKE '%mourning dove%'
         OR LOWER("SCIENTIFIC NAME") LIKE '%zenaida macroura%')
        
        AND "STATE" = 'California'
        AND EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) BETWEEN 2005 AND 2024
        AND "OBSERVATION COUNT" IS NOT NULL
        AND "OBSERVATION COUNT" != ''
        AND parsed_count IS NOT NULL
        AND "COUNTY" IS NOT NULL
        AND "COUNTY" != ''
),

yearly_totals AS (
    SELECT 
        observation_year,
        SUM(parsed_count) as total_mourning_doves_seen
    FROM mourning_dove_data
    GROUP BY observation_year
),

county_breakdown AS (
    SELECT 
        observation_year,
        "COUNTY",
        SUM(parsed_count) as mourning_doves_in_county,
        COUNT(*) as number_of_observations
    FROM mourning_dove_data
    GROUP BY observation_year, "COUNTY"
),

county_pivot AS (
    SELECT 
        observation_year,
        STRING_AGG(
            CONCAT("COUNTY", ': ', mourning_doves_in_county, ' birds (', number_of_observations, ' obs)'),
            ' | '
            ORDER BY mourning_doves_in_county DESC
        ) as county_breakdown_detail
    FROM county_breakdown
    GROUP BY observation_year
)

SELECT 
    yt.observation_year as year,
    yt.total_mourning_doves_seen as total_birds_statewide,
    cp.county_breakdown_detail as county_breakdown
    
FROM yearly_totals yt
LEFT JOIN county_pivot cp ON yt.observation_year = cp.observation_year
ORDER BY yt.observation_year;