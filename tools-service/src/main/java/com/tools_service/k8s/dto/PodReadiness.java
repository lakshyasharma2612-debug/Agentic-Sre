package com.tools_service.k8s.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@AllArgsConstructor
@Getter
@ToString
public class PodReadiness {
    private String name;
    private String phase;
    private boolean ready;
    private int readyContainers;
    private int totalContainers;
    private String reason;
}