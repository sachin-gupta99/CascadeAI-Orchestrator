package com.cascadeAI.Orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RequirementsDoneMessage {
    private String runId;
    private RequirementsSpec requirementsSpec;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequirementsSpec {
        private String title;
        private String description;
        private String priority;
        private List<String> acceptanceCriteria;
    }
}
