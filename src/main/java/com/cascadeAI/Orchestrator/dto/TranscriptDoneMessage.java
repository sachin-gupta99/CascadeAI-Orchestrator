package com.cascadeAI.Orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TranscriptDoneMessage {
    private String runId;
    private String transcript;
    private Object speakerMap;
    private List<String> actionItems;
}
