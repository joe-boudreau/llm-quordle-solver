docker run -d \
  --env-file .env-local \
  -v $(pwd)/output:/output \
  -v ~/.aws:/root/.aws:ro \
  quordle-solver:latest
