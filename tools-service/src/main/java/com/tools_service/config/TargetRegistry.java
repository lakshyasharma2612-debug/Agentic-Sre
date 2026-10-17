package com.tools_service.config;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Component
public class TargetRegistry {

    private final Map<String, TargetProfile> profiles = new HashMap<>();

    private String activeTarget;

    @PostConstruct
    public void load() {
        try {
            ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

            RegistryConfig registry = loadRegistry(yamlMapper);

            validateRegistry(registry);

            this.activeTarget = registry.getActiveTarget();

            for (Map.Entry<String, TargetReference> entry : registry.getTargets().entrySet()) {
                String targetId = entry.getKey();
                String profilePath = entry.getValue().getProfile();

                TargetProfile profile = loadProfile(yamlMapper, profilePath);

                validateProfile(targetId, profile);

                profiles.put(targetId, profile);
            }
            System.out.println("Active target: " + activeTarget);

            TargetProfile profile = getActiveTarget();

            System.out.println("Namespace: " +
                    profile.getKubernetes().getNamespace());

            System.out.println("Prometheus: " +
                    profile.getPrometheus().getUrl());

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load target configuration", e
            );
        }
    }

    public TargetProfile getActiveTarget() {
        return getTarget(activeTarget);
    }

    public TargetProfile getTarget(String targetId) {
        TargetProfile profile = profiles.get(targetId);

        if (profile == null) {
            throw new IllegalArgumentException(
                    "Target not found: " + targetId
            );
        }

        return profile;
    }

    private RegistryConfig loadRegistry(ObjectMapper yamlMapper)
            throws IOException {

        ClassPathResource resource =
                new ClassPathResource("config/registry.yaml");

        try (InputStream inputStream = resource.getInputStream()) {
            return yamlMapper.readValue(inputStream, RegistryConfig.class);
        }
    }

    private TargetProfile loadProfile(
            ObjectMapper yamlMapper,
            String profilePath) throws IOException {

        ClassPathResource resource =
                new ClassPathResource(profilePath);

        try (InputStream inputStream = resource.getInputStream()) {
            return yamlMapper.readValue(inputStream, TargetProfile.class);
        }
    }

    private void validateRegistry(RegistryConfig registry) {

        if (registry.getActiveTarget() == null ||
                registry.getActiveTarget().isBlank()) {
            throw new IllegalStateException(
                    "activeTarget is missing in registry.yaml"
            );
        }

        if (registry.getTargets() == null ||
                registry.getTargets().isEmpty()) {
            throw new IllegalStateException(
                    "No targets configured in registry.yaml"
            );
        }

        if (!registry.getTargets().containsKey(registry.getActiveTarget())) {
            throw new IllegalStateException(
                    "Active target does not exist: "
                            + registry.getActiveTarget()
            );
        }
    }

    private void validateProfile(
            String targetId,
            TargetProfile profile) {

        if (profile.getId() == null ||
                profile.getId().isBlank()) {
            throw new IllegalStateException(
                    "Target profile ID is missing: " + targetId
            );
        }

        if (!targetId.equals(profile.getId())) {
            throw new IllegalStateException(
                    "Target ID mismatch. Registry: "
                            + targetId
                            + ", Profile: "
                            + profile.getId()
            );
        }

        if (profile.getKubernetes() == null) {
            throw new IllegalStateException(
                    "Kubernetes configuration missing for target: "
                            + targetId
            );
        }

        if (profile.getKubernetes().getNamespace() == null ||
                profile.getKubernetes().getNamespace().isBlank()) {
            throw new IllegalStateException(
                    "Kubernetes namespace missing for target: "
                            + targetId
            );
        }

        if (profile.getPrometheus() == null ||
                profile.getPrometheus().getUrl() == null ||
                profile.getPrometheus().getUrl().isBlank()) {
            throw new IllegalStateException(
                    "Prometheus URL missing for target: "
                            + targetId
            );
        }
    }
}