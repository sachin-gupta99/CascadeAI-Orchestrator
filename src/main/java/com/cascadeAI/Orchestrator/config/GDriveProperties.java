package com.cascadeAI.Orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.experimental.FieldDefaults;

@Configuration
@Data
@ConfigurationProperties("google.drive")
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class GDriveProperties {

    String credentialsPath;
    String folderId;
}
