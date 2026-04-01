package com.cascadeAI.Orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PipelineCompleteMessage {
    private UUID runId;
    private boolean emailSent;
    private String recipientEmail;
}
