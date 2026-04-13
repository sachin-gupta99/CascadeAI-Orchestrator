package com.cascadeAI.Orchestrator.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties("aws.iam-user")
public class AwsIAMConfig {

    private String accessKey;
    private String secretKey;
}
