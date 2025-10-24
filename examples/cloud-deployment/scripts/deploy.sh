#!/bin/bash
set -e

echo "========================================="
echo "A2A Cloud Deployment - Deployment Script for Minikube"
echo "========================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if Minikube is installed
if ! command -v minikube &> /dev/null; then
    echo -e "${RED}Error: Minikube is not installed${NC}"
    echo "Please install Minikube first: https://minikube.sigs.k8s.io/docs/start/"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo -e "${RED}Error: kubectl is not installed${NC}"
    echo "Please install kubectl first: https://kubernetes.io/docs/tasks/tools/"
    exit 1
fi

# Start Minikube
echo "Starting Minikube..."
if ! minikube status &>/dev/null; then
    minikube start --cpus=4 --memory=8192
    echo -e "${GREEN}✓ Minikube started${NC}"
else
    echo -e "${GREEN}✓ Minikube is already running${NC}"
fi

# Enable the registry addon
echo "Enabling registry addon..."
minikube addons enable registry
echo -e "${GREEN}✓ Registry addon enabled${NC}"

# Use Minikube's Docker daemon
eval $(minikube -p minikube docker-env)

# Build the project
echo ""
echo "Building the project..."
cd ../server
mvn clean package -DskipTests
echo -e "${GREEN}✓ Project built successfully${NC}"

# Build and push container image to local registry
echo ""
echo "Building container image..."
docker build -t localhost:5000/a2a-cloud-deployment:latest .
echo -e "${GREEN}✓ Container image built${NC}"

# Push to registry inside minikube
minikube cache add localhost:5000/a2a-cloud-deployment:latest

# Go back to scripts directory
cd ../scripts

# Install Strimzi operator if not already installed
echo ""
echo "Checking for Strimzi operator..."

# Ensure kafka namespace exists
if ! kubectl get namespace kafka > /dev/null 2>&1; then
    echo "Creating kafka namespace..."
    kubectl create namespace kafka
fi

if ! kubectl get crd kafkas.kafka.strimzi.io > /dev/null 2>&1; then
    echo "Installing Strimzi operator..."
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
    kubectl get kafka -n kafka -o wide 2>/dev/null || true
    kubectl get pods -n kafka -l strimzi.io/cluster=a2a-kafka 2>/dev/null || true

    if kubectl wait --for=condition=Ready kafka/a2a-kafka -n kafka --timeout=10s 2>/dev/null; then
        echo -e "${GREEN}✓ Kafka deployed${NC}"
        break
    fi

    if [ $i -eq 60 ]; then
        echo -e "${RED}ERROR: Timeout waiting for Kafka${NC}"
        kubectl describe kafka/a2a-kafka -n kafka
        kubectl get events -n kafka --sort-by='.lastTimestamp'
        exit 1
    fi
done

# Create Kafka Topic for event replication
echo ""
echo "Creating Kafka topic for event replication..."
kubectl apply -f ../k8s/03-kafka-topic.yaml
echo "Waiting for Kafka topic to be ready..."
kubectl wait --for=condition=Ready kafkatopic/a2a-replicated-events -n kafka --timeout=60s
echo -e "${GREEN}✓ Kafka topic created${NC}"

# Deploy Agent ConfigMap
echo ""
echo "Deploying Agent ConfigMap..."
kubectl apply -f ../k8s/04-agent-configmap.yaml
echo -e "${GREEN}✓ ConfigMap deployed${NC}"

# Deploy Agent
if [ "${SKIP_AGENT_DEPLOY}" != "true" ]; then
    echo ""
    echo "Deploying A2A Agent..."
    kubectl apply -f ../k8s/05-agent-deployment.yaml

    echo "Waiting for Agent pods to be ready..."
    kubectl wait --for=condition=Ready pod -l app=a2a-agent -n a2a-demo --timeout=120s
    echo -e "${GREEN}✓ Agent deployed${NC}"
else
    echo ""
    echo -e "${YELLOW}⚠ Skipping agent deployment (SKIP_AGENT_DEPLOY=true)${NC}"
    echo "  ConfigMap has been deployed, you can manually deploy the agent with:"
    echo "    kubectl apply -f ../k8s/05-agent-deployment.yaml"
fi


echo "Exposing service via minikube service command..."
minikube service a2a-agent-service -n a2a-demo --url

echo ""
echo -e "${GREEN}=========================================${NC}"
echo -e "${GREEN}Deployment to Minikube completed successfully!${NC}"
echo -e "${GREEN}=========================================${NC}"
echo ""
echo "The agent is now accessible at the URL printed above."

echo "To run the test client (demonstrating load balancing):"
echo "  cd ../server"
echo "  mvn test-compile exec:java -Dexec.classpathScope=test \"
    -Dexec.mainClass=\"io.a2a.examples.cloud.A2ACloudExampleClient\" \"
    -Dagent.url=<SERVICE_URL>"
