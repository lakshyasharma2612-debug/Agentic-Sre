package com.tools_service.config;

import java.util.List;

public class KubernetesConfig {

    private String namespace;
    private List<String> deployments;
    private List<String> services;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public List<String> getDeployments() {
        return deployments;
    }

    public void setDeployments(List<String> deployments) {
        this.deployments = deployments;
    }

    public List<String> getServices() {
        return services;
    }

    public void setServices(List<String> services) {
        this.services = services;
    }
}