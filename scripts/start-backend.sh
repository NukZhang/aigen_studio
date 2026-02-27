#!/bin/bash

set -euo pipefail

echo "Starting AIGen Studio Backend..."

cd "$(dirname "$0")/.."

cd backend

echo "Performing clean compile to avoid stale class artifacts..."
mvn -q -DskipTests clean compile

echo "Starting Spring Boot application..."
mvn -q spring-boot:run
