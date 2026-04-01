package com.cascadeAI.Orchestrator.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class PipelineStartMessage {
    private UUID runId;
    private String transcriptFileId;
    private String transcriptFileName;
}
