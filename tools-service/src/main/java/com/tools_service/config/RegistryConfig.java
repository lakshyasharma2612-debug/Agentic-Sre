package com.tools_service.config;


import java.util.Map;

public class RegistryConfig {

    private String activeTarget;
    private Map<String, TargetReference> targets;

    public String getActiveTarget() {
        return activeTarget;
    }

    public void setActiveTarget(String activeTarget) {
        this.activeTarget = activeTarget;
    }

    public Map<String, TargetReference> getTargets() {
        return targets;
    }

    public void setTargets(Map<String, TargetReference> targets) {
        this.targets = targets;
    }
}