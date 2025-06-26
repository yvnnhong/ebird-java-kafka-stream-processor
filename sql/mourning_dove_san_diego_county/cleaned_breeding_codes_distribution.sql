-- All (cleaned) breeding codes distribution for San Diego County Mourning Doves (2005-2024)
SELECT 
    TRIM("BREEDING CODE") as cleaned_breeding_code,
    COUNT(*) as frequency,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) as percentage
FROM read_csv_auto('C:/Data/Birds/ebird_observation_data.txt',
                   max_line_size=25000000,
                   strict_mode=false,
                   ignore_errors=true)
WHERE 
    (LOWER("COMMON NAME") LIKE '%mourning dove%'
     OR LOWER("SCIENTIFIC NAME") LIKE '%zenaida macroura%')
    AND "STATE" = 'California'
    AND "COUNTY" = 'San Diego'
    AND EXTRACT(YEAR FROM CAST("OBSERVATION DATE" AS DATE)) BETWEEN 2005 AND 2024
    AND "BREEDING CODE" IS NOT NULL
    AND "BREEDING CODE" != ''
    AND TRIM("BREEDING CODE") NOT IN ('F', 'H', 'S', 'P')
GROUP BY TRIM("BREEDING CODE")
ORDER BY frequency DESC;