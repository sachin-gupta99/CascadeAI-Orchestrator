package com.cascadeAI.Orchestrator.dto;

import lombok.Data;

@Data
public class CodingApprovedMessage {
    private String runId;
    private String requirementId;
    private Object requirementsSpec;
    private String repoFullName;
}
