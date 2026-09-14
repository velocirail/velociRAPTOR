#!/bin/bash
set -e

# Configuration
REGION="${AWS_REGION:-eu-west-1}"
ACCOUNT="${AWS_ACCOUNT_ID}"
ECR_REPO="${ECR_REPO_NAME:-velociraptor}"
TAG="${IMAGE_TAG:-$(date +%Y%m%d-%H%M%S)}"

if [ -z "$ACCOUNT" ]; then
  echo "Error: AWS_ACCOUNT_ID environment variable not set"
  echo "Usage: AWS_ACCOUNT_ID=123456789012 ./build-and-push.sh"
  exit 1
fi

echo "Building velociRAPTOR Docker image..."
echo "  Region: $REGION"
echo "  Account: $ACCOUNT"
echo "  Repository: $ECR_REPO"
echo "  Tag: $TAG"
echo ""

# Login to ECR
echo "Logging in to ECR..."
aws ecr get-login-password --region "$REGION" | \
  docker login --username AWS --password-stdin "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com"

# Build image
echo ""
echo "Building image..."
docker build -t "$ECR_REPO:$TAG" -t "$ECR_REPO:latest" .

# Tag for ECR
echo ""
echo "Tagging for ECR..."
docker tag "$ECR_REPO:$TAG" "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$TAG"
docker tag "$ECR_REPO:latest" "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:latest"

# Push to ECR
echo ""
echo "Pushing to ECR..."
docker push "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$TAG"
docker push "$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:latest"

echo ""
echo "✓ Successfully pushed:"
echo "  $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$TAG"
echo "  $ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:latest"
echo ""
echo "To deploy this image, update CloudFormation parameter:"
echo "  ContainerImage=$ACCOUNT.dkr.ecr.$REGION.amazonaws.com/$ECR_REPO:$TAG"
