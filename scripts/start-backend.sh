#!/bin/bash

echo "Starting AIGen Studio Backend..."

cd "$(dirname "$0")/.."

cd backend

if [ ! -d "target" ]; then
    echo "Building project..."
    mvn clean install
fi

echo "Starting Spring Boot application..."
mvn spring-boot:run