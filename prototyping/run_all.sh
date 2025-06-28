# script to run all prototypes
# prototyping/run_all.sh
#!/bin/bash
echo "Running JavaScript prototypes..."
node regression_prototype.js
echo "---"
node population_analysis.js
echo "Prototyping complete!"