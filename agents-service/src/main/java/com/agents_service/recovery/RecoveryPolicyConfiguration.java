package com.agents_service.recovery;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RecoveryPolicyProperties.class)
public class RecoveryPolicyConfiguration {
}