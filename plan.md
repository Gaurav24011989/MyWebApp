# MyWebApp — Azure AKS Deployment Plan

This document describes how to deploy **MyWebApp** on Microsoft Azure using **Azure Kubernetes Service (AKS)**. The repository currently contains only a project placeholder (`README.md`). This plan assumes a typical stateless web application (HTTP API and/or frontend) that will be containerized and run on AKS.

---

## 1. Goals and Scope

| Item | Decision |
|------|----------|
| **Target platform** | Azure Kubernetes Service (AKS) |
| **Application** | MyWebApp (containerized web service) |
| **Environments** | Dev → Staging → Production |
| **Ingress** | Azure Application Gateway or NGINX Ingress Controller |
| **Container registry** | Azure Container Registry (ACR) |
| **Secrets** | Azure Key Vault + CSI Secret Store driver |
| **CI/CD** | GitHub Actions (repo is on GitHub) |

**Out of scope for initial release:** multi-region active-active, service mesh (Istio/Linkerd), and custom DNS beyond a single domain.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Azure Subscription                          │
│                                                                          │
│  ┌──────────────┐     ┌─────────────────────────────────────────────┐   │
│  │ GitHub       │     │              Resource Group: rg-mywebapp-prod │   │
│  │ Actions CI/CD│────▶│  ┌─────────┐    ┌─────────────────────────┐  │   │
│  └──────────────┘     │  │   ACR   │───▶│          AKS            │  │   │
│                       │  │ (images)│    │  ┌─────┐ ┌─────┐ ┌─────┐ │  │   │
│                       │  └─────────┘    │  │ Pod │ │ Pod │ │ Pod │ │  │   │
│                       │                 │  └─────┘ └─────┘ └─────┘ │  │   │
│                       │                 │         ▲                  │  │   │
│                       │  ┌──────────────┴─────────┴──────────────┐  │   │
│                       │  │     Ingress / Application Gateway     │  │   │
│                       │  └──────────────────┬────────────────────┘  │   │
│                       └─────────────────────┼──────────────────────────┘   │
│                                             │                              │
│  ┌──────────────┐  ┌──────────────┐  ┌─────▼──────┐  ┌────────────────┐  │
│  │ Key Vault    │  │ Log Analytics│  │ Public IP  │  │ Azure Database │  │
│  │ (secrets)    │  │ / Monitor    │  │ + DNS      │  │ (optional)     │  │
│  └──────────────┘  └──────────────┘  └────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

**Traffic flow:** Internet → Azure Load Balancer / Application Gateway → Ingress → Kubernetes Service → MyWebApp Pods.

---

## 3. Prerequisites

### Azure

- Active Azure subscription with permissions to create resource groups, AKS, ACR, Key Vault, and networking resources.
- [Azure CLI](https://learn.microsoft.com/en-us/cli/azure/install-azure-cli) (`az`) version 2.50+.
- [kubectl](https://kubernetes.io/docs/tasks/tools/) aligned with the target Kubernetes version.
- [Helm](https://helm.sh/) 3.x (optional, for ingress and add-ons).

### Local / CI

- [Docker](https://docs.docker.com/get-docker/) for building images locally.
- GitHub repository access with permission to configure Actions secrets.

### Application (to be added to this repo)

Before deployment, the repo should include at minimum:

| Artifact | Purpose |
|----------|---------|
| `Dockerfile` | Multi-stage build for production image |
| `src/` or app source | Application code |
| `k8s/` or `deploy/` | Kubernetes manifests or Helm chart |
| `.dockerignore` | Smaller, faster image builds |
| Health endpoint | e.g. `GET /health` for liveness/readiness probes |

---

## 4. Azure Resources

### 4.1 Naming Convention

Use a consistent prefix (example: `mywebapp`):

| Resource | Example name |
|----------|----------------|
| Resource group | `rg-mywebapp-prod` |
| AKS cluster | `aks-mywebapp-prod` |
| Container registry | `acrmywebappprod` (ACR names must be alphanumeric, no hyphens) |
| Key Vault | `kv-mywebapp-prod` |
| Log Analytics workspace | `law-mywebapp-prod` |
| Managed identity | `id-mywebapp-aks` |

### 4.2 Resource Group

```bash
az group create \
  --name rg-mywebapp-prod \
  --location eastus
```

Choose a region close to users and compliant with data residency requirements.

### 4.3 Azure Container Registry (ACR)

```bash
az acr create \
  --resource-group rg-mywebapp-prod \
  --name acrmywebappprod \
  --sku Standard \
  --admin-enabled false
```

- Use **Standard** or **Premium** (Premium required for geo-replication and private link).
- Disable admin user; authenticate via managed identity or service principal.

### 4.4 Log Analytics Workspace

Required for AKS monitoring and Container Insights.

```bash
az monitor log-analytics workspace create \
  --resource-group rg-mywebapp-prod \
  --workspace-name law-mywebapp-prod
```

### 4.5 AKS Cluster

```bash
az aks create \
  --resource-group rg-mywebapp-prod \
  --name aks-mywebapp-prod \
  --node-count 2 \
  --node-vm-size Standard_D2s_v3 \
  --enable-managed-identity \
  --attach-acr acrmywebappprod \
  --network-plugin azure \
  --enable-addons monitoring \
  --workspace-resource-id <LOG_ANALYTICS_WORKSPACE_ID> \
  --generate-ssh-keys \
  --kubernetes-version 1.29
```

**Recommendations:**

| Setting | Dev | Production |
|---------|-----|------------|
| Node count | 1–2 | 3+ (spread across availability zones) |
| VM size | `Standard_B2s` | `Standard_D2s_v3` or larger |
| Autoscaling | Optional | Enable cluster autoscaler |
| Kubernetes version | Latest supported patch | Pin minor version; upgrade deliberately |

Enable cluster autoscaler for production:

```bash
az aks update \
  --resource-group rg-mywebapp-prod \
  --name aks-mywebapp-prod \
  --enable-cluster-autoscaler \
  --min-count 2 \
  --max-count 6
```

### 4.6 Azure Key Vault

```bash
az keyvault create \
  --name kv-mywebapp-prod \
  --resource-group rg-mywebapp-prod \
  --location eastus \
  --enable-rbac-authorization true
```

Store application secrets (connection strings, API keys) in Key Vault. Mount them into pods using the [Secrets Store CSI Driver](https://learn.microsoft.com/en-us/azure/aks/csi-secrets-store-driver).

### 4.7 Optional: Azure Database

If MyWebApp needs persistent data, provision managed data services instead of running databases inside AKS:

- **Azure Database for PostgreSQL Flexible Server** — relational data
- **Azure Cache for Redis** — caching and sessions
- **Azure Storage** — blobs and static assets

Use private endpoints and restrict AKS egress to these services where possible.

---

## 5. Application Containerization

### 5.1 Dockerfile (template)

Add a production-oriented `Dockerfile` to the repo root:

```dockerfile
# Build stage
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Runtime stage
FROM node:20-alpine
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app/dist ./dist
COPY --from=build /app/node_modules ./node_modules
EXPOSE 8080
USER node
CMD ["node", "dist/server.js"]
```

Adjust base image and build steps for your stack (.NET, Python, Go, etc.).

### 5.2 Image tagging strategy

| Tag | When |
|-----|------|
| `latest` | Dev only (avoid in production deploys) |
| `<git-sha>` | Every CI build |
| `v1.2.3` | Release tags |

Example full image reference:

```
acrmywebappprod.azurecr.io/mywebapp:97a10d6
```

### 5.3 Build and push (manual)

```bash
az acr login --name acrmywebappprod
docker build -t acrmywebappprod.azurecr.io/mywebapp:$(git rev-parse --short HEAD) .
docker push acrmywebappprod.azurecr.io/mywebapp:$(git rev-parse --short HEAD)
```

---

## 6. Kubernetes Manifests

Create a `k8s/` directory with the following resources.

### 6.1 Namespace

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: mywebapp
```

### 6.2 Deployment

Key settings for MyWebApp:

- **Replicas:** 2+ in production for high availability
- **Resource requests/limits:** prevent noisy-neighbor issues
- **Probes:** liveness (`/health`) and readiness (`/ready`)
- **Security:** run as non-root, read-only root filesystem where possible

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mywebapp
  namespace: mywebapp
spec:
  replicas: 2
  selector:
    matchLabels:
      app: mywebapp
  template:
    metadata:
      labels:
        app: mywebapp
    spec:
      containers:
        - name: mywebapp
          image: acrmywebappprod.azurecr.io/mywebapp:TAG
          ports:
            - containerPort: 8080
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
          livenessProbe:
            httpGet:
              path: /health
              port: 8080
            initialDelaySeconds: 10
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /ready
              port: 8080
            initialDelaySeconds: 5
            periodSeconds: 10
          envFrom:
            - secretRef:
                name: mywebapp-secrets
```

### 6.3 Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: mywebapp
  namespace: mywebapp
spec:
  selector:
    app: mywebapp
  ports:
    - port: 80
      targetPort: 8080
  type: ClusterIP
```

### 6.4 Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: mywebapp
  namespace: mywebapp
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: mywebapp
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

### 6.5 Ingress

**Option A — NGINX Ingress Controller**

```bash
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
helm install ingress-nginx ingress-nginx/ingress-nginx \
  --namespace ingress-nginx --create-namespace
```

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: mywebapp
  namespace: mywebapp
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  ingressClassName: nginx
  tls:
    - hosts:
        - app.example.com
      secretName: mywebapp-tls
  rules:
    - host: app.example.com
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: mywebapp
                port:
                  number: 80
```

**Option B — Application Gateway Ingress Controller (AGIC)**

Better integration with Azure WAF, SSL termination, and enterprise networking. Requires an Application Gateway instance and AGIC add-on on AKS.

---

## 7. Networking and Security

### 7.1 Network model

- Use the **Azure CNI** network plugin (already in `az aks create` above) for production workloads that need predictable pod IPs or integration with Azure networking.
- Define Kubernetes **NetworkPolicies** to restrict pod-to-pod traffic (deny all by default, allow only required paths).

### 7.2 TLS / HTTPS

1. Register a domain (e.g. `app.example.com`).
2. Point DNS A record to the ingress public IP.
3. Use **cert-manager** with Let's Encrypt, or upload a certificate to Key Vault and reference it from ingress.

### 7.3 Identity and access

| Component | Approach |
|-----------|----------|
| AKS → ACR | Managed identity (`--attach-acr`) |
| Pods → Key Vault | Workload Identity or pod-managed identity + CSI driver |
| CI/CD → Azure | GitHub OIDC federation (no long-lived secrets) |
| Human access | Azure RBAC + `az aks get-credentials` with AAD integration |

Enable AAD integration on AKS for production:

```bash
az aks update \
  --resource-group rg-mywebapp-prod \
  --name aks-mywebapp-prod \
  --enable-aad \
  --aad-admin-group-object-ids <AAD_GROUP_ID>
```

### 7.4 Security checklist

- [ ] Non-root containers
- [ ] Image scanning in ACR (Microsoft Defender for Containers)
- [ ] Pod Security Standards (baseline or restricted)
- [ ] Secrets in Key Vault, not in manifests or env files in git
- [ ] WAF on Application Gateway (if used)
- [ ] Regular AKS and node image upgrades

---

## 8. CI/CD Pipeline (GitHub Actions)

Add `.github/workflows/deploy-aks.yml`:

```yaml
name: Build and Deploy to AKS

on:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  id-token: write
  contents: read

env:
  ACR_NAME: acrmywebappprod
  IMAGE_NAME: mywebapp
  AKS_CLUSTER: aks-mywebapp-prod
  AKS_RESOURCE_GROUP: rg-mywebapp-prod
  K8S_NAMESPACE: mywebapp

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Azure Login (OIDC)
        uses: azure/login@v2
        with:
          client-id: ${{ secrets.AZURE_CLIENT_ID }}
          tenant-id: ${{ secrets.AZURE_TENANT_ID }}
          subscription-id: ${{ secrets.AZURE_SUBSCRIPTION_ID }}

      - name: Build and push image to ACR
        run: |
          az acr build \
            --registry $ACR_NAME \
            --image $IMAGE_NAME:${{ github.sha }} \
            .

      - name: Set AKS context
        uses: azure/aks-set-context@v4
        with:
          resource-group: ${{ env.AKS_RESOURCE_GROUP }}
          cluster-name: ${{ env.AKS_CLUSTER }}

      - name: Deploy to AKS
        run: |
          kubectl set image deployment/mywebapp \
            mywebapp=$ACR_NAME.azurecr.io/$IMAGE_NAME:${{ github.sha }} \
            -n $K8S_NAMESPACE
          kubectl rollout status deployment/mywebapp -n $K8S_NAMESPACE
```

**GitHub secrets / variables to configure:**

| Name | Description |
|------|-------------|
| `AZURE_CLIENT_ID` | App registration for OIDC |
| `AZURE_TENANT_ID` | Azure AD tenant |
| `AZURE_SUBSCRIPTION_ID` | Target subscription |

Prefer [Azure OIDC federation](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure) over storing `AZURE_CREDENTIALS` JSON.

---

## 9. Observability

### 9.1 Azure Monitor / Container Insights

Enabled via `--enable-addons monitoring` during cluster creation. Provides:

- Pod and node metrics
- Container logs in Log Analytics
- Recommended alerts (CPU, memory, restart count)

### 9.2 Application logging

- Emit structured JSON logs to stdout/stderr (Kubernetes captures these).
- Correlate requests with a trace ID header.
- Optionally export OpenTelemetry traces to Azure Monitor Application Insights.

### 9.3 Recommended alerts

| Alert | Threshold |
|-------|-----------|
| Pod restart rate | > 3 in 15 minutes |
| Node NotReady | Any node |
| HTTP 5xx rate | > 1% over 5 minutes |
| AKS cluster autoscaler failures | Any failure event |

---

## 10. Deployment Phases

### Phase 1 — Foundation (Week 1)

1. Create resource group, ACR, Log Analytics, and AKS (dev cluster).
2. Add `Dockerfile`, application skeleton, and health endpoints to this repo.
3. Build and push first image manually; verify pull from AKS.

### Phase 2 — Kubernetes baseline (Week 1–2)

1. Add `k8s/` manifests (namespace, deployment, service).
2. Deploy to dev AKS with `kubectl apply -f k8s/`.
3. Validate pods, logs, and internal service connectivity (`kubectl port-forward`).

### Phase 3 — Ingress and TLS (Week 2)

1. Install ingress controller.
2. Configure DNS and TLS certificate.
3. Verify external HTTPS access.

### Phase 4 — Secrets and data (Week 2–3)

1. Provision Key Vault and store secrets.
2. Install Secrets Store CSI driver and wire pod volumes.
3. Add Azure Database (if needed) with private networking.

### Phase 5 — CI/CD and production (Week 3–4)

1. Configure GitHub Actions with OIDC.
2. Create production resource group and AKS (or promote dev → prod via separate overlay/Helm values).
3. Enable autoscaling, alerts, and backup policies.
4. Run load test and document rollback procedure.

---

## 11. Rollout and Rollback

### Deploy a new version

```bash
kubectl set image deployment/mywebapp \
  mywebapp=acrmywebappprod.azurecr.io/mywebapp:<NEW_TAG> \
  -n mywebapp
kubectl rollout status deployment/mywebapp -n mywebapp
```

### Rollback

```bash
kubectl rollout undo deployment/mywebapp -n mywebapp
kubectl rollout status deployment/mywebapp -n mywebapp
```

Kubernetes keeps ReplicaSet revision history (default: 10 revisions).

---

## 12. Cost Estimation (approximate, East US)

| Resource | Dev (monthly) | Prod (monthly) |
|----------|---------------|----------------|
| AKS control plane | Free | Free |
| 2× Standard_D2s_v3 nodes | ~$140 | ~$210 (3 nodes) |
| ACR Standard | ~$20 | ~$20 |
| Log Analytics (5 GB/month) | ~$10 | ~$30+ |
| Public IP + bandwidth | ~$5 | ~$20+ |
| Key Vault | ~$1 | ~$1 |
| **Total (estimate)** | **~$175** | **~$280+** |

Use [Azure Pricing Calculator](https://azure.microsoft.com/en-us/pricing/calculator/) for accurate estimates. Dev clusters can use spot node pools and smaller VMs to reduce cost.

---

## 13. Repository Checklist

Before first production deploy, ensure this repo contains:

- [ ] Application source code
- [ ] `Dockerfile` and `.dockerignore`
- [ ] `k8s/` manifests or Helm chart
- [ ] `.github/workflows/deploy-aks.yml`
- [ ] `README.md` updated with local dev and deploy instructions
- [ ] Environment variable documentation (non-secret values only)

---

## 14. Next Steps

1. **Choose application stack** (.NET, Node.js, Python, etc.) and scaffold the app in this repository.
2. **Provision dev AKS** using the commands in Section 4.
3. **Implement Phase 1–2** from Section 10 and validate a working deployment.
4. **Iterate** through ingress, secrets, CI/CD, and production hardening.

For questions or changes to this plan (multi-region, private AKS cluster, GitOps with Flux/Argo CD), update this document and re-review before production cutover.
