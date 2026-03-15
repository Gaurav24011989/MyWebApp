variable "resource_group_name" {
  default = "aks-demo-rg"
}

variable "location" {
  default = "South India"
}

variable "cluster_name" {
  default = "spring-boot-aks"
}

variable "acr_name" {
  description = "Name of the existing ACR"
  default     = "springbootdemogb2026"
}

variable "acr_resource_group" {
  description = "Resource Group where ACR is located"
  default     = "gb-resource-group"
}
