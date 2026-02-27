#!/bin/bash

set -euo pipefail

echo "Starting AIGen Studio (Backend + Frontend)..."

# Start backend in background
echo "Starting backend..."
cd "$(dirname "$0")/../backend"
echo "Performing clean compile for backend..."
mvn -q -DskipTests clean compile
mvn -q spring-boot:run &
BACKEND_PID=$!

# Wait for backend to start
echo "Waiting for backend to start..."
sleep 10

# Start frontend in background
echo "Starting frontend..."
cd "$(dirname "$0")/../frontend"
npm run dev &
FRONTEND_PID=$!

echo "============================================"
echo "AIGen Studio started successfully!"
echo "============================================"
echo "Backend: http://localhost:8080"
echo "Frontend: http://localhost:3000"
echo "API Docs: http://localhost:8080/api/swagger-ui.html"
echo "============================================"
echo ""
echo "Press Ctrl+C to stop all services"

# Trap Ctrl+C to kill both processes
trap "echo 'Stopping services...'; kill $BACKEND_PID $FRONTEND_PID; exit" INT TERM

# Wait for both processes
wait
