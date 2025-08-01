# Build the Docker image
docker build -t quordle-solver .

# Run the container
docker run -it --rm quordle-solver

# If you need to persist generated files, mount a volume:
docker run -it --rm -v $(pwd)/output:/app/output quordle-solver

# For debugging, you can run with bash:
docker run -it --rm --entrypoint /bin/bash quordle-solver
