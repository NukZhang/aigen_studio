#!/bin/bash

echo "Starting AIGen Studio Frontend..."

cd "$(dirname "$0")/.."

cd frontend

if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

echo "Starting Vue development server..."
npm run dev