# Kubernetes and Helm Deployment Guide (Verified)

This guide documents the successful deployment of the Spring Boot application using **Helm** (application) and **Terraform** (infrastructure).

## 1. Infrastructure Provisioning (Terraform)
The infrastructure was provisioned using Terraform in the `terraform/` directory.

- **Cluster Name**: `spring-boot-aks`
- **Resource Group**: `aks-demo-rg`
- **VM Size**: `Standard_B2s_v2` (Chosen for compatibility in `southindia`)

### Key Commands:
```powershell
cd terraform
terraform init
terraform apply -auto-approve
```

## 2. Application Deployment (Helm)
The application was deployed using the Helm chart in `charts/spring-boot-demo`.

### Image Pull Secret
To ensure immediate pull from ACR without waiting for AAD propagation, a secret was created:
```powershell
# Create the secret (Admin credentials used)
kubectl create secret docker-registry acr-secret `
  --docker-server=springbootdemogb2026.azurecr.io `
  --docker-username=springbootdemogb2026 `
  --docker-password=<ACR_PASSWORD>
```

### Installation:
```powershell
helm upgrade --install spring-boot-demo ./charts/spring-boot-demo
```

## 3. Verification

### Status Check:
- **Pod Status**: `Running` / `Ready 1/1`
- **Service Type**: `LoadBalancer`
- **External IP**: `20.219.73.5`

### Test Endpoint:
```powershell
Invoke-RestMethod -Uri "http://20.219.73.5/hello/K8sUser"
```
**Output:** `hello K8sUser`

---
## Summary of "Write Once, Run Anywhere"
- **Helm Chart**: Standard K8s YAML. Can be deployed to AWS EKS or GCP GKE without changes.
- **Terraform**: Azure-specific infrastructure code.
- **Observability**: Spring Actuator endpoints enabled and verified.
