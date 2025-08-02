docker run -d \
  --env-file .env-local \
  -v $(pwd)/output:/output \
  quordle-solver:latest
