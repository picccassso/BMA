#!/bin/bash

# Test script for BMA server endpoints

SERVER="http://localhost:8008"

echo "Testing BMA Server at $SERVER"
echo "================================"

# Test health endpoint
echo -n "Testing /health endpoint... "
if curl -s "$SERVER/health" | grep -q "healthy"; then
    echo "✓ OK"
else
    echo "✗ Failed"
fi

# Test info endpoint
echo -n "Testing /info endpoint... "
if curl -s "$SERVER/info" | grep -q "BMA Music Server"; then
    echo "✓ OK"
    echo "Server info:"
    curl -s "$SERVER/info" | python3 -m json.tool
else
    echo "✗ Failed"
fi

# Test songs endpoint
echo -e "\nTesting /songs endpoint... "
curl -s "$SERVER/songs" | python3 -m json.tool

echo -e "\nServer test complete!" 