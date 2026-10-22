#!/bin/bash
# Ask the app if it is healthy. Try for about 60 seconds, then fail the deployment.
for i in $(seq 1 20); do
  if curl -sf http://localhost:8080/health > /dev/null; then
    echo "Health check passed"
    exit 0
  fi
  echo "Waiting for the app... ($i/20)"
  sleep 3
done
echo "Health check FAILED"
exit 1
