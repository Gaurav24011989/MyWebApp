# Azure Deployment Walkthrough

The Spring Boot application has been successfully deployed to Azure Container Apps.

## Deployment Details

- **App Name**: `spring-boot-demo`
- **Resource Group**: `gb-resource-group`
- **Location**: `South India`
- **Registry**: `springbootdemogb2026.azurecr.io`
- **URL**: [https://spring-boot-demo.braveground-e5569649.southindia.azurecontainerapps.io](https://spring-boot-demo.braveground-e5569649.southindia.azurecontainerapps.io)

## Steps Taken & Commands Executed

### 1. Preparation

**Create Resource Group:**
```powershell
az group create --name gb-resource-group --location southindia
```

**Create Azure Container Registry (ACR):**
```powershell
az acr create --resource-group gb-resource-group --name springbootdemogb2026 --sku Basic --admin-enabled true
```

### 2. Build & Push

**Login to ACR:**
```powershell
az acr login --name springbootdemogb2026
```

**Build Docker Image locally:**
```powershell
docker build -t springbootdemogb2026.azurecr.io/spring-boot-demo:latest .
```

**Push Image to ACR:**
```powershell
docker push springbootdemogb2026.azurecr.io/spring-boot-demo:latest
```

### 3. Deployment

**Create Container App Environment:**
```powershell
az containerapp env create --name spring-boot-demo-env --resource-group gb-resource-group --location southindia
```

**Retrieve ACR Credentials:**
```powershell
# Get the password for the deployment command
az acr credential show --name springbootdemogb2026 --query "passwords[0].value"
```

**Deploy Container App:**
```powershell
az containerapp create --name spring-boot-demo `
  --resource-group gb-resource-group `
  --environment spring-boot-demo-env `
  --image springbootdemogb2026.azurecr.io/spring-boot-demo:latest `
  --registry-server springbootdemogb2026.azurecr.io `
  --registry-username springbootdemogb2026 `
  --registry-password <YOUR_ACR_PASSWORD> `
  --ingress external `
  --target-port 8080 `
  --query properties.configuration.ingress.fqdn
```

## Verification

**Test Endpoint:**
```powershell
Invoke-RestMethod -Uri "https://spring-boot-demo.braveground-e5569649.southindia.azurecontainerapps.io/hello/Antigravity"
```
**Output:**
```
hello Antigravity
```
