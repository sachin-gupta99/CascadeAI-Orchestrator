package com.cascadeAI.Orchestrator.dto;

import lombok.Data;

@Data
public class PrApprovedMessage {
    private String runId;
    private String prUrl;
    private Integer prNumber;
    private String branchName;
    private String repoFullName;
    private String reviewerEmail;
    private String notes;
    private String seniorEngineerEmail;
}
