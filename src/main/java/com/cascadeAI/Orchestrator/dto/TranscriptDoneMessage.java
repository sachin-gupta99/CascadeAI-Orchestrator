package com.cascadeAI.Orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptDoneMessage {
    private UUID runId;
    private String transcript;
    private Object speakerMap;
    private List<String> actionItems;
}
