# Infrastructure and Observability Guide

## 1. Observability Features

The application now includes Spring Boot Actuator and Micrometer Prometheus.

### Endpoints
- **Health**: `/actuator/health` (Used by Azure for Liveness/Readiness probes)
- **Metrics**: `/actuator/prometheus` (Exposes metrics for scraping)
- **Info**: `/actuator/info`

### Configuration
Managed in `src/main/resources/application.properties`:
```properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.probes.enabled=true
```

## 2. Infrastructure as Code (Bicep)

A Bicep template (`main.bicep`) has been created to provision the entire stack.

### Resources Provisioned
- **Log Analytics Workspace**: For centralized logging.
- **Azure Container Apps Environment**: The hosting platform.
- **Container App**: configured with:
  - 0.25 vCPU / 0.5 Gi Memory (Optimal/Minimal).
  - Liveness & Readiness probes pointing to Actuator.
  - Autoscaling (0-10 replicas) based on HTTP traffic.

### How to Deploy
Run the following command to deploy or update the infrastructure:

```powershell
az deployment group create `
  --resource-group gb-resource-group `
  --template-file main.bicep `
  --parameters acrName=springbootdemogb2026 `
               acrUsername=springbootdemogb2026 `
               acrPassword=<YOUR_ACR_PASSWORD>
```

*Note: If you have an existing environment in the same region on a free trial, you may hit a limit. You might need to delete the old environment first or use a different region.*
