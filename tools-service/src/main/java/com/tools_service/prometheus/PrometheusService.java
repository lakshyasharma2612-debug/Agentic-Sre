package com.tools_service.prometheus;

import com.tools_service.config.TargetProfile;
import com.tools_service.config.TargetRegistry;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor 
public class PrometheusService {

    private final RestClient.Builder restClientBuilder;
    private final TargetRegistry targetRegistry;

  

    public String query(String targetId, String query) {

        TargetProfile target =
                targetRegistry.getTarget(targetId);

        String prometheusUrl =
                target.getPrometheus().getUrl();

        RestClient client =
                restClientBuilder
                        .baseUrl(prometheusUrl)
                        .build();


        return client
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .build(query))
                .retrieve()
                .body(String.class);
                }
}