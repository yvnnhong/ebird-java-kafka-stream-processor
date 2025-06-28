#!/bin/bash

echo "Creating Kafka topics for bird streaming pipeline..."

# Wait for Kafka to be ready
echo "Waiting for Kafka to be ready..."
sleep 10

# Create bird-observations topic
docker exec kafka kafka-topics --create \
    --topic bird-observations \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

# Create bird-alerts topic  
docker exec kafka kafka-topics --create \
    --topic bird-alerts \
    --bootstrap-server localhost:9092 \
    --partitions 3 \
    --replication-factor 1 \
    --if-not-exists

# List created topics
echo "Created topics:"
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

echo "Kafka topics created successfully!"