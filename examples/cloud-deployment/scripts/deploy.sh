#!/bin/bash
set -e

echo "========================================="
echo "A2A Cloud Deployment - Deployment Script"
echo "========================================="
echo ""

# Color codes for output
RED='\033[0.31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse command line arguments
CONTAINER_TOOL="docker"
while [[ $# -gt 0 ]]; do
    case $1 in
        --container-tool)
            CONTAINER_TOOL="$2"
            shift 2
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Usage: $0 [--container-tool docker|podman]"
            exit 1
            ;;
    esac
done

echo "Container tool: $CONTAINER_TOOL"
echo ""

# Check if Minikube is running
if ! minikube status > /dev/null 2>&1; then
    echo -e "${RED}Error: Minikube is not running${NC}"
    echo "Please start Minikube first:"
    echo ""
    echo "With Docker:"
    echo "  minikube start --cpus=4 --memory=8192"
    echo ""
    echo "With Podman:"
    echo "  minikube start --cpus=4 --memory=8192 --driver=podman"
    exit 1
fi

echo -e "${GREEN}✓ Minikube is running${NC}"

# Detect Minikube driver
MINIKUBE_DRIVER=$(minikube profile list -o json | grep -o '"Driver":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "Minikube driver: $MINIKUBE_DRIVER"

# Set container environment to Minikube
echo ""
if [ "$CONTAINER_TOOL" = "podman" ] || [ "$MINIKUBE_DRIVER" = "podman" ]; then
    echo "Setting Podman environment to Minikube..."
    eval $(minikube podman-env)
    echo -e "${GREEN}✓ Podman environment configured${NC}"
else
    echo "Setting Docker environment to Minikube..."
    eval $(minikube docker-env)
    echo -e "${GREEN}✓ Docker environment configured${NC}"
fi

# Build the project
echo ""
echo "Building the project..."
cd ../server
mvn clean package -DskipTests
echo -e "${GREEN}✓ Project built successfully${NC}"

# Build container image
echo ""
echo "Building container image with $CONTAINER_TOOL..."
$CONTAINER_TOOL build -t a2a-cloud-deployment:latest .
echo -e "${GREEN}✓ Container image built${NC}"

# Go back to scripts directory
cd ../scripts

# Install Strimzi operator if not already installed
echo ""
echo "Checking for Strimzi operator..."
if ! kubectl get crd kafkas.kafka.strimzi.io > /dev/null 2>&1; then
    echo "Installing Strimzi operator..."
    kubectl create namespace kafka || true
    kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka

    echo "Waiting for Strimzi operator deployment to be created..."
    for i in {1..30}; do
        if kubectl get deployment strimzi-cluster-operator -n kafka > /dev/null 2>&1; then
            echo "Deployment found"
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "${RED}ERROR: Deployment not found after 30 seconds${NC}"
            exit 1
        fi
        sleep 1
    done

    echo "Waiting for Strimzi operator to be ready..."
    kubectl wait --for=condition=Available deployment/strimzi-cluster-operator -n kafka --timeout=300s
    kubectl wait --for=condition=Ready pod -l name=strimzi-cluster-operator -n kafka --timeout=300s
    echo -e "${GREEN}✓ Strimzi operator installed${NC}"
else
    echo -e "${GREEN}✓ Strimzi operator already installed${NC}"
fi

# Create namespace
echo ""
echo "Creating namespace..."
kubectl apply -f ../k8s/00-namespace.yaml
echo -e "${GREEN}✓ Namespace created${NC}"

# Deploy PostgreSQL
echo ""
echo "Deploying PostgreSQL..."
kubectl apply -f ../k8s/01-postgres.yaml
echo "Waiting for PostgreSQL to be ready..."
kubectl wait --for=condition=Ready pod -l app=postgres -n a2a-demo --timeout=120s
echo -e "${GREEN}✓ PostgreSQL deployed${NC}"

# Deploy Kafka
echo ""
echo "Deploying Kafka..."
kubectl apply -f ../k8s/02-kafka.yaml
echo "Waiting for Kafka to be ready (using KRaft mode, typically 2-3 minutes)..."

# Monitor progress while waiting
for i in {1..60}; do
    echo "Checking Kafka status (attempt $i/60)..."
    kubectl get kafka -n a2a-demo -o wide 2>/dev/null || true
    kubectl get pods -n a2a-demo -l strimzi.io/cluster=a2a-kafka 2>/dev/null || true

    if kubectl wait --for=condition=Ready kafka/a2a-kafka -n a2a-demo --timeout=10s 2>/dev/null; then
        echo -e "${GREEN}✓ Kafka deployed${NC}"
        break
    fi

    if [ $i -eq 60 ]; then
        echo -e "${RED}ERROR: Timeout waiting for Kafka${NC}"
        kubectl describe kafka/a2a-kafka -n a2a-demo
        kubectl get events -n a2a-demo --sort-by='.lastTimestamp'
        exit 1
    fi
done

# Deploy Agent ConfigMap
echo ""
echo "Deploying Agent ConfigMap..."
kubectl apply -f ../k8s/03-agent-configmap.yaml
echo -e "${GREEN}✓ ConfigMap deployed${NC}"

# Deploy Agent
echo ""
echo "Deploying A2A Agent..."
kubectl apply -f ../k8s/04-agent-deployment.yaml
echo "Waiting for Agent pods to be ready..."
kubectl wait --for=condition=Ready pod -l app=a2a-agent -n a2a-demo --timeout=120s
echo -e "${GREEN}✓ Agent deployed${NC}"

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}Deployment completed successfully!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "To verify the deployment, run:"
echo "  ./verify.sh"
echo ""
echo "To access the agent from outside the cluster:"
echo "  kubectl port-forward -n a2a-demo svc/a2a-agent-service 8080:8080"
echo ""
echo "Then access the agent at:"
echo "  http://localhost:8080/a2a/agent-card"
