# Observability and IaC Implementation Plan

Enhance the application with monitoring and create a reproducible infrastructure definition.

## 1. Application Changes (Observability)

### Dependencies (`pom.xml`)
- `spring-boot-starter-actuator`: For health and metrics.
- `micrometer-registry-prometheus`: To expose metrics (standard for K8s/ACA).

### Configuration (`application.properties`)
- Enable `management.endpoints.web.exposure.include=health,info,prometheus`
- Enable K8s probes: `management.endpoint.health.probes.enabled=true`

### Dockerfile
- No changes needed (uses JAR).

## 2. Infrastructure as Code (Azure Bicep)

Since the target is **Azure Container Apps**, **Bicep** is the preferred "better mechanism" over Helm (which is for raw K8s).

### Resources to Define
- **Log Analytics Workspace**: For monitoring logs.
- **Container Apps Environment**: Hosted in the workspace.
- **Azure Container Registry**: For images.
- **Container App**:
  - **Resources**: CPU: 0.25, Memory: 0.5Gi (Minimal/Optimal).
  - **Probes**:
    - Liveness: `/actuator/health/liveness`
    - Readiness: `/actuator/health/readiness`
  - **Autoscaling**: KEDA rules (HTTP scaling).
  - **Env Vars**: Java options if needed.

## 3. Verification
- Local: Run app, check `/actuator/health`.
- IaC: Validate Bicep file with `az deployment group validate`.
