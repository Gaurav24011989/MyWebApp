# Azure Container Apps Deployment Plan

Deploy the Spring Boot `demo` application to Azure Container Apps (ACA).

## Prerequisites
- Azure CLI installed (Verified: v2.70.0).
- User logged in (`az login`).

## Proposed Steps

### 1. Preparation
- **Extension**: Ensure `containerapp` extension is installed.
  ```powershell
  az extension add --name containerapp --upgrade
  ```
- **Login**:
  ```powershell
  az login
  ```

### 2. Resource Creation
Variables:
- Resource Group: `gb-resource-group`
- Location: `southindia`
- ACR Name: `springbootdemoacr<random>` (needs to be globally unique)
- ACA Environment: `spring-boot-demo-env`
- App Name: `spring-boot-demo`

Commands:
```powershell
# Create Container App Environment (User provided RG)
az containerapp env create --name spring-boot-demo-env --resource-group gb-resource-group --location southindia
```

### 3. Build and Deploy
Option A: **Source to Cloud** (Simpler)
Use `az containerapp up` which handles build (using Azure Build Service) and deployment.
```powershell
az containerapp up --name spring-boot-demo --resource-group spring-boot-demo-rg --environment spring-boot-demo-env --source . --ingress external --target-port 8080 --query properties.configuration.ingress.fqdn
```
*Note: This requires the source code to be uploaded and built in the cloud.*

Option B: **Manual Build & Push** (More control)
1. Create ACR.
2. `az acr build` (or local docker build + push).
3. `az containerapp create`.

**Recommendation**: Start with **Option A** (`az containerapp up`) for simplicity.

## Verification
- Access the returned FQDN (Fully Qualified Domain Name) with `/hello/Antigravity`.
