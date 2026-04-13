package com.cascadeAI.Orchestrator.config;

import lombok.Data;
import lombok.experimental.FieldDefaults;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@Data
@ConfigurationProperties("db")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class PostgreDBProperties {

    private String url;
    private String username;
    private String password;
    private String driverClassName;
}
