docker build --platform linux/amd64 --provenance=false -f ./Dockerfile-AWS -t quordle-solver:lambda .
aws ecr get-login-password --region ca-central-1 | docker login --username AWS --password-stdin 868512170571.dkr.ecr.ca-central-1.amazonaws.com
docker tag quordle-solver:lambda 868512170571.dkr.ecr.ca-central-1.amazonaws.com/daily-quordle-solver:lambda
docker push 868512170571.dkr.ecr.ca-central-1.amazonaws.com/daily-quordle-solver:lambda
