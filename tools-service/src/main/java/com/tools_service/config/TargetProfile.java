package com.tools_service.config;


public class TargetProfile {

    private String id;
    private KubernetesConfig kubernetes;
    private PrometheusConfig prometheus;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public KubernetesConfig getKubernetes() {
        return kubernetes;
    }

    public void setKubernetes(KubernetesConfig kubernetes) {
        this.kubernetes = kubernetes;
    }

    public PrometheusConfig getPrometheus() {
        return prometheus;
    }

    public void setPrometheus(PrometheusConfig prometheus) {
        this.prometheus = prometheus;
    }
}
