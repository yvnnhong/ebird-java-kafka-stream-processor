// prototyping/regression_prototype.js
// Initial prototype for regression-based population prediction
// Used to validate approach before Python + Java implementation

console.log("Mourning Dove Population Regression Prototype");
console.log("================================================");

// San Diego County historical data (2005-2024)
const populationData = {
    year: [2005, 2006, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014, 
           2015, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2023, 2024],
    birds_counted_sd: [2345, 3299, 4579, 7797, 9723, 12585, 13266, 16554, 19796, 30668,
                      41104, 60324, 88534, 96460, 105462, 145591, 157672, 153099, 164134, 174422],
    observations_sd: [484, 701, 959, 1431, 1861, 2450, 2880, 3615, 4799, 6947,
                     7966, 10350, 13048, 16520, 18619, 25367, 30266, 32025, 34293, 34963]
};

// Simple linear regression implementation
function linearRegression(x, y) {
    const n = x.length;
    const sumX = x.reduce((a, b) => a + b, 0);
    const sumY = y.reduce((a, b) => a + b, 0);
    const sumXY = x.reduce((acc, xi, i) => acc + xi * y[i], 0);
    const sumXX = x.reduce((acc, xi) => acc + xi * xi, 0);
    
    const slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    const intercept = (sumY - slope * sumX) / n;
    
    return { slope, intercept };
}

// Calculate R-squared for model evaluation
function calculateRSquared(x, y, model) {
    const predictions = x.map(xi => model.slope * xi + model.intercept);
    const yMean = y.reduce((a, b) => a + b, 0) / y.length;
    
    const ssRes = y.reduce((acc, yi, i) => acc + Math.pow(yi - predictions[i], 2), 0);
    const ssTot = y.reduce((acc, yi) => acc + Math.pow(yi - yMean, 2), 0);
    
    return 1 - (ssRes / ssTot);
}

// Prototype analysis
console.log("\nData Summary:");
console.log(`Years: ${populationData.year[0]} - ${populationData.year[populationData.year.length-1]}`);
console.log(`Population growth: ${populationData.birds_counted_sd[0].toLocaleString()} → ${populationData.birds_counted_sd[populationData.birds_counted_sd.length-1].toLocaleString()}`);
console.log(`Growth factor: ${(populationData.birds_counted_sd[populationData.birds_counted_sd.length-1] / populationData.birds_counted_sd[0]).toFixed(1)}x`);

// Fit regression models
const birdsModel = linearRegression(populationData.year, populationData.birds_counted_sd);
const obsModel = linearRegression(populationData.year, populationData.observations_sd);

console.log("\nRegression Models:");
console.log(`Birds: y = ${birdsModel.slope.toFixed(1)}x + ${birdsModel.intercept.toFixed(0)}`);
console.log(`Observations: y = ${obsModel.slope.toFixed(1)}x + ${obsModel.intercept.toFixed(0)}`);

// Calculate model performance
const birdsR2 = calculateRSquared(populationData.year, populationData.birds_counted_sd, birdsModel);
const obsR2 = calculateRSquared(populationData.year, populationData.observations_sd, obsModel);

console.log("\nModel Performance:");
console.log(`Birds R²: ${birdsR2.toFixed(3)} (${birdsR2 > 0.9 ? 'Excellent' : birdsR2 > 0.8 ? 'Good' : 'Fair'} fit)`);
console.log(`Observations R²: ${obsR2.toFixed(3)} (${obsR2 > 0.9 ? 'Excellent' : obsR2 > 0.8 ? 'Good' : 'Fair'} fit)`);

// Future predictions (2025-2027)
const futureYears = [2025, 2026, 2027];
const predictions = futureYears.map(year => {
    const predictedBirds = Math.round(birdsModel.slope * year + birdsModel.intercept);
    const predictedObs = Math.round(obsModel.slope * year + obsModel.intercept);
    return {
        year,
        predicted_birds: predictedBirds,
        predicted_observations: predictedObs,
        birds_per_observation: (predictedBirds / predictedObs).toFixed(1)
    };
});

console.log("\nFuture Predictions:");
predictions.forEach(pred => {
    console.log(`${pred.year}: ${pred.predicted_birds.toLocaleString()} birds, ${pred.predicted_observations.toLocaleString()} observations (${pred.birds_per_observation} birds/obs)`);
});

// Year-over-year growth analysis
console.log("\nGrowth Pattern Analysis:");
const growthRates = [];
for (let i = 1; i < populationData.year.length; i++) {
    const rate = ((populationData.birds_counted_sd[i] - populationData.birds_counted_sd[i-1]) / populationData.birds_counted_sd[i-1] * 100);
    growthRates.push(rate);
    if (rate > 30) {
        console.log(`${populationData.year[i]}: +${rate.toFixed(1)}% spike (${populationData.birds_counted_sd[i-1].toLocaleString()} → ${populationData.birds_counted_sd[i].toLocaleString()})`);
    }
}

const avgGrowthRate = growthRates.reduce((a, b) => a + b, 0) / growthRates.length;
console.log(`Average annual growth: ${avgGrowthRate.toFixed(1)}%`);

// Synthetic data generation prototype
console.log("\nSynthetic Data Generation Prototype:");

function generateSyntheticObservation(targetYear = 2025) {
    const prediction = predictions.find(p => p.year === targetYear);
    const avgBirdsPerObs = parseFloat(prediction.birds_per_observation);
    
    // Breeding patterns by season
    const breedingPatterns = {
        'Spring': { 'S7': 0.4, 'C': 0.3, 'ON': 0.2, 'NB': 0.1 },
        'Summer': { 'S7': 0.5, 'FL': 0.3, 'C': 0.2 },
        'Fall': { 'FL': 0.4, 'ON': 0.3, 'NY': 0.3 },
        'Winter': { 'C': 0.5, 'S7': 0.3, 'ON': 0.2 }
    };
    
    // Random date
    const dayOfYear = Math.floor(Math.random() * 365);
    const date = new Date(targetYear, 0, dayOfYear + 1);
    const month = date.getMonth() + 1;
    
    // Determine season
    let season;
    if (month >= 3 && month <= 5) season = 'Spring';
    else if (month >= 6 && month <= 8) season = 'Summer';
    else if (month >= 9 && month <= 11) season = 'Fall';
    else season = 'Winter';
    
    // Generate count
    let count = Math.max(1, Math.round(avgBirdsPerObs + (Math.random() - 0.5) * avgBirdsPerObs * 0.6));
    const isAnomaly = Math.random() < 0.03; // 3% anomaly rate
    if (isAnomaly) {
        count = count * (8 + Math.floor(Math.random() * 12)); // 8-20x normal
    }
    
    // Select breeding code
    const seasonCodes = Object.keys(breedingPatterns[season]);
    const breedingCode = seasonCodes[Math.floor(Math.random() * seasonCodes.length)];
    
    // San Diego coordinates
    const latitude = 32.53 + Math.random() * 0.98;
    const longitude = -117.60 + Math.random() * 1.53;
    
    return {
        id: `PROTO_${targetYear}_${Math.random().toString(36).substr(2, 6)}`,
        commonName: 'Mourning Dove',
        count: count,
        observationDate: date.toISOString().split('T')[0],
        latitude: Math.round(latitude * 1000000) / 1000000,
        longitude: Math.round(longitude * 1000000) / 1000000,
        county: 'San Diego',
        season: season,
        breedingCode: breedingCode,
        dataType: 'SYNTHETIC_PROTOTYPE',
        isAnomaly: isAnomaly,
        predictedYear: targetYear,
        basePrediction: Math.round(avgBirdsPerObs)
    };
}

// Generate sample synthetic observations
console.log("Sample synthetic observations:");
for (let i = 0; i < 5; i++) {
    const obs = generateSyntheticObservation(2025);
    const anomalyFlag = obs.isAnomaly ? ' (ANOMALY)' : '';
    console.log(`  ${obs.id}: ${obs.count} birds, ${obs.season}, ${obs.breedingCode}${anomalyFlag}`);
}

console.log("\nPrototype validation complete!");
console.log(" Key findings:");
console.log(`   • Linear regression achieves R² = ${birdsR2.toFixed(3)} for population prediction`);
console.log(`   • 2025 prediction: ${predictions[0].predicted_birds.toLocaleString()} birds (${predictions[0].birds_per_observation} per observation)`);
console.log(`   • Synthetic data generation feasible with seasonal breeding patterns`);
console.log(`   • Ready for production implementation in Python + Java`);

// Export results for production implementation
const prototypeResults = {
    model_performance: { birds_r2: birdsR2, observations_r2: obsR2 },
    predictions: predictions,
    breeding_patterns: {
        'Spring': { 'S7': 0.4, 'C': 0.3, 'ON': 0.2, 'NB': 0.1 },
        'Summer': { 'S7': 0.5, 'FL': 0.3, 'C': 0.2 },
        'Fall': { 'FL': 0.4, 'ON': 0.3, 'NY': 0.3 },
        'Winter': { 'C': 0.5, 'S7': 0.3, 'ON': 0.2 }
    },
    san_diego_bounds: {
        lat_min: 32.53, lat_max: 33.51,
        lon_min: -117.60, lon_max: -116.07
    },
    prototype_date: new Date().toISOString(),
    next_steps: [
        "Implement full regression analysis in Python",
        "Generate 1000+ synthetic observations",
        "Integrate with Java Kafka streaming pipeline",
        "Deploy anomaly detection algorithms"
    ]
};

console.log("\nPrototype results exported for production implementation");
console.log(`Results object contains ${Object.keys(prototypeResults).length} key components`);

// This prototype validates the approach before building the full Python + Java pipeline