package com.cascadeAI.Orchestrator.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PrCreatedMessage {
    private UUID runId;
    private String prUrl;
    private Integer prNumber;
    private String branchName;
    private String repoFullName;
    private String diffSummary;
}
