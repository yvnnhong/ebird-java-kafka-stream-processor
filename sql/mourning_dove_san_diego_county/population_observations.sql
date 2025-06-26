-- Mourning Dove Population Analysis: San Diego County (2005-2024)
--
-- Analyzes yearly bird counts and observation patterns in San Diego County
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
        AND "COUNTY" = 'San Diego'
),

-- Get statewide totals from all counties (not just San Diego)
statewide_data AS (
    SELECT 
        EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) as observation_year,
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
    FROM statewide_data
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

county_with_rankings AS (
    SELECT 
        observation_year,
        "COUNTY",
        mourning_doves_in_county,
        number_of_observations,
        ROW_NUMBER() OVER (PARTITION BY observation_year ORDER BY mourning_doves_in_county DESC) as county_rank
    FROM county_breakdown
)

SELECT 
    cwr.observation_year as year,
    cwr.mourning_doves_in_county as birds_counted_sd,
    cwr.number_of_observations as observations_sd,
    yt.total_mourning_doves_seen as total_birds_statewide,
    ROUND(100.0 * cwr.mourning_doves_in_county / yt.total_mourning_doves_seen, 1) as percent_of_state_total,
    
    -- Year-over-year analysis
    LAG(cwr.mourning_doves_in_county) OVER (ORDER BY cwr.observation_year) as prev_year_birds,
    ROUND(100.0 * (cwr.mourning_doves_in_county - LAG(cwr.mourning_doves_in_county) OVER (ORDER BY cwr.observation_year)) 
          / LAG(cwr.mourning_doves_in_county) OVER (ORDER BY cwr.observation_year), 1) as percent_change_from_prev_year
    
FROM county_with_rankings cwr
JOIN yearly_totals yt ON cwr.observation_year = yt.observation_year
ORDER BY cwr.observation_year;