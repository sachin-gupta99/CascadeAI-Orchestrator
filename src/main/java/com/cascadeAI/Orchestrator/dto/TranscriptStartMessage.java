package com.cascadeAI.Orchestrator.dto;

import lombok.Data;

@Data
public class TranscriptStartMessage {
    private String runId;
    private String transcriptFileId;
    private String transcriptFileName;
}
