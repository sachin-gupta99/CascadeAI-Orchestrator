package com.cascadeAI.Orchestrator.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class CodingApprovedMessage {
    private UUID runId;
    private UUID requirementId;
    private Object requirementsSpec;
}
