# Kubernetes and Helm Implementation Plan

Implement a cloud-agnostic deployment strategy using Helm Charts, with Azure AKS as the reference implementation.

## 1. Helm Chart (`charts/spring-boot-demo`)
Create a standard Helm chart to package the application.

### Resources
- **Deployment**:
  - Image: `springbootdemogb2026.azurecr.io/spring-boot-demo:latest`
  - Resources: 0.25 vCPU, 512Mi Memory.
  - Probes: Liveness/Readiness pointing to `/actuator/health/*`.
- **Service**: Type `LoadBalancer` (to expose publicly).
- **HorizontalPodAutoscaler (HPA)**: Scale on CPU usage (50%).

## 2. Infrastructure (Terraform for AKS)
Create a `terraform/` folder with scripts to provision K8s on Azure.
*Note: This is the "write once per cloud" part. You'd need a separate one for AWS/GCP, but the Helm chart stays the same.*

### Resources
- Resource Group
- Azure Kubernetes Service (AKS) Cluster
- ACR attachment (to pull images)

## 3. Verification
- **Template Verify**: `helm template` to ensure YAML generation is correct.
- **Verification Plan**:
  1.  Provision AKS (Terraform).
  2.  Deploy App (Helm Upgrade/Install).
  3.  Verify Endpoint.
