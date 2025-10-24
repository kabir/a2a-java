#!/bin/bash

echo "============================================"
echo "A2A Cloud Deployment - Cleanup Script for Minikube"
echo "============================================"
echo ""

# Color codes
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}This will delete all resources in the a2a-demo namespace and the Minikube cluster${NC}"
read -p "Are you sure you want to continue? (y/N) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled"
    exit 0
fi

echo ""
echo "Deleting A2A Agent..."
kubectl delete -f ../k8s/05-agent-deployment.yaml --ignore-not-found=true

echo ""
echo "Deleting ConfigMap..."
kubectl delete -f ../k8s/04-agent-configmap.yaml --ignore-not-found=true

echo ""
echo "Deleting Kafka topic..."
kubectl delete -f ../k8s/03-kafka-topic.yaml --ignore-not-found=true

echo ""
echo "Deleting Kafka..."
kubectl delete -f ../k8s/02-kafka.yaml --ignore-not-found=true

echo ""
echo "Deleting PostgreSQL..."
kubectl delete -f ../k8s/01-postgres.yaml --ignore-not-found=true

echo ""
echo "Deleting namespace..."
kubectl delete -f ../k8s/00-namespace.yaml --ignore-not-found=true

echo ""
echo "Stopping Minikube..."
minikube stop

echo ""
echo -e "${GREEN}Cleanup completed${NC}"
echo ""
echo -e "${YELLOW}Note: Strimzi operator was not removed${NC}"
echo "To remove Strimzi operator, run:"
echo "  kubectl delete namespace kafka"
