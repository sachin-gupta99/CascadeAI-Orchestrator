package com.cascadeAI.Orchestrator.controller;

import com.cascadeAI.Orchestrator.model.*;
import com.cascadeAI.Orchestrator.service.PipelineService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService service;

    // ── Pipeline Runs ─────────────────────────────────────────────────────

    @GetMapping("/runs")
    public List<PipelineRun> getAllRuns() {
        return service.getAllRuns();
    }

    @GetMapping("/runs/{id}")
    public PipelineRun getRun(@PathVariable UUID id) {
        return service.getRun(id);
    }

    @PostMapping("/runs")
    public PipelineRun startRun(@RequestBody StartRunRequest req) {
        return service.startRun(req.getTranscriptFileId(), req.getTranscriptFileName());
    }

    // ── Requirements ──────────────────────────────────────────────────────

    @GetMapping("/runs/{id}/requirements")
    public List<Requirement> getRequirements(@PathVariable UUID id) {
        return service.getRequirements(id);
    }

    // ── Approval Gate ─────────────────────────────────────────────────────

    @PostMapping("/runs/{id}/approve")
    public ResponseEntity<Void> approve(
            @PathVariable UUID id,
            @RequestBody ApprovalRequest req) {
        service.approveRequirements(id, req.getReviewerEmail(), req.getNotes());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/runs/{id}/reject")
    public ResponseEntity<Void> reject(
            @PathVariable UUID id,
            @RequestBody ApprovalRequest req) {
        service.rejectRequirements(id, req.getReviewerEmail(), req.getNotes());
        return ResponseEntity.ok().build();
    }

    // ── Health ────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public String health() { return "ok"; }

    // ── Request bodies ────────────────────────────────────────────────────

    @Data public static class StartRunRequest {
        private String transcriptFileId;
        private String transcriptFileName;
    }

    @Data public static class ApprovalRequest {
        private String reviewerEmail;
        private String notes;
    }
}
