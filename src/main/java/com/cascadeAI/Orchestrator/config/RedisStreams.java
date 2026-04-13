package com.cascadeAI.Orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@ConfigurationProperties(prefix = "pipeline.queue")
@Data
@Configuration
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RedisStreams {
    String pipelineStart;
    String transcriptStart;
    String codingApproved;
    String transcriptDone;
    String requirementsDone;
    String prCreated;
    String prApproved;
    String complete;
    String failed;
}
