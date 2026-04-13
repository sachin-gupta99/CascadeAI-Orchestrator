package com.cascadeAI.Orchestrator.controller;

import com.cascadeAI.Orchestrator.model.*;
import com.cascadeAI.Orchestrator.service.GoogleDriveService;
import com.cascadeAI.Orchestrator.service.PipelineService;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService service;
    private final GoogleDriveService driveService;

    // ── File Upload to Google Drive ──────────────────────────────────────

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file)
            throws IOException {
        var uploaded = driveService.uploadFile(file);
        return ResponseEntity.ok(Map.of(
                "fileId", uploaded.getId(),
                "fileName", uploaded.getName(),
                "webViewLink", uploaded.getWebViewLink()));
    }

    // ── Pipeline Runs ─────────────────────────────────────────────────────

    @GetMapping("/runs")
    public List<PipelineRun> getAllRuns() {
        return service.getAllRuns();
    }

    @GetMapping("/runs/{id}")
    public PipelineRun getRun(@PathVariable String id) {
        return service.getRun(id);
    }

    @PostMapping("/start")
    public void startPipeline(@RequestBody StartRunRequest req) {
        service.onTranscriptStart(req.getRunId());
    }

    // ── Requirements ──────────────────────────────────────────────────────

    @GetMapping("/runs/{id}/requirements")
    public List<Requirement> getRequirements(@PathVariable String id) {
        return service.getRequirements(id);
    }

    @PutMapping("/runs/{runId}/requirements/{reqId}")
    public Requirement updateRequirement(@PathVariable String runId, @PathVariable String reqId,
            @RequestBody UpdateRequirementRequest req) {
        return service.updateRequirement(runId, reqId,
                req.getTitle(), req.getDescription(), req.getPriority(),
                req.getAcceptanceCriteria());
    }

    // ── Approval Gate ─────────────────────────────────────────────────────

    @PostMapping("/runs/{id}/requirements/approve")
    public ResponseEntity<?> approve(@PathVariable String id, @RequestBody ApprovalRequest req) {
        service.approveRequirements(id, req.getReviewerEmail(), req.getNotes(), req.getRepoFullName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/runs/{id}/requirements/reject")
    public ResponseEntity<Void> reject(@PathVariable String id, @RequestBody ApprovalRequest req) {
        service.rejectRequirements(id, req.getReviewerEmail(), req.getNotes());
        return ResponseEntity.ok().build();
    }

    // ── PR Gate ────────────────────────────────────────────────────────────

    @PostMapping("/runs/{id}/pr/verify")
    public ResponseEntity<Void> verifyPr(@PathVariable String id, @RequestBody ApprovalRequest req) {
        service.verifyPr(id, req.getReviewerEmail(), req.getNotes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/runs/{id}/pr/reject")
    public ResponseEntity<Void> rejectPr(@PathVariable String id, @RequestBody ApprovalRequest req) {
        service.rejectPr(id, req.getReviewerEmail(), req.getNotes());
        return ResponseEntity.ok().build();
    }

    // ── Health ────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    // ── Request bodies ────────────────────────────────────────────────────

    @Data
    public static class StartRunRequest {
        private String runId;
    }

    @Data
    public static class ApprovalRequest {
        private String reviewerEmail;
        private String notes;
        private String repoFullName;
    }

    @Data
    public static class UpdateRequirementRequest {
        private String title;
        private String description;
        private Requirement.Priority priority;
        private List<String> acceptanceCriteria;
    }
}
