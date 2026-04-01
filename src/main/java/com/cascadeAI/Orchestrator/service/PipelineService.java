package com.cascadeAI.Orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cascadeAI.Orchestrator.dto.*;
import com.cascadeAI.Orchestrator.model.*;
import com.cascadeAI.Orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final PipelineRunRepository runRepo;
    private final RequirementRepository requirementRepo;
    private final RedisTemplate<String, Object> redis;
    private final ObjectMapper objectMapper;

    @Value("${pipeline.queue.start}")
    private String queueStart;

    @Value("${pipeline.queue.coding-approved}")
    private String queueCodingApproved;

    // ── Trigger a new pipeline run ────────────────────────────────────────

    @Transactional
    public PipelineRun startRun(String transcriptFileId, String transcriptFileName) {
        PipelineRun run = PipelineRun.builder()
                .status(PipelineRun.RunStatus.PENDING)
                .transcriptFileId(transcriptFileId)
                .transcriptFileName(transcriptFileName)
                .build();
        run = runRepo.save(run);

        // Publish to Python agent via Redis
        PipelineStartMessage msg = new PipelineStartMessage();
        msg.setRunId(run.getId());
        msg.setTranscriptFileId(transcriptFileId);
        msg.setTranscriptFileName(transcriptFileName);
        redis.convertAndSend(queueStart, msg);

        log.info("Pipeline run started: {}", run.getId());
        return run;
    }

    // ── Handle: transcript agent done ─────────────────────────────────────

    @Transactional
    public void onTranscriptDone(TranscriptDoneMessage msg) {
        updateStatus(msg.getRunId(), PipelineRun.RunStatus.ANALYZING);
        log.info("Transcript done for run {}", msg.getRunId());
    }

    // ── Handle: requirements agent done ──────────────────────────────────

    @Transactional
    public void onRequirementsDone(RequirementsDoneMessage msg) {
        PipelineRun run = getRunOrThrow(msg.getRunId());
        var spec = msg.getRequirementsSpec();

        Requirement req = Requirement.builder()
                .run(run)
                .title(spec.getTitle())
                .description(spec.getDescription())
                .priority(Requirement.Priority.valueOf(
                        spec.getPriority() != null ? spec.getPriority() : "MEDIUM"))
                .affectedFiles(spec.getAffectedFiles())
                .acceptanceCriteria(spec.getAcceptanceCriteria())
                .rawSpec(toJson(spec))
                .build();
        requirementRepo.save(req);

        // Create pending approval gate
        updateStatus(run.getId(), PipelineRun.RunStatus.AWAITING_APPROVAL);
        log.info("Requirements saved, awaiting approval for run {}", run.getId());
    }

    // ── Approve requirements (human-in-the-loop) ──────────────────────────

    @Transactional
    public void approveRequirements(UUID runId, String reviewerEmail, String notes) {
        PipelineRun run = getRunOrThrow(runId);
        List<Requirement> reqs = requirementRepo.findByRunId(runId);
        if (reqs.isEmpty()) throw new IllegalStateException("No requirements found for run: " + runId);

        Requirement req = reqs.get(0);
        updateStatus(runId, PipelineRun.RunStatus.CODING);

        // Unblock coding agent via Redis
        CodingApprovedMessage msg = new CodingApprovedMessage();
        msg.setRunId(runId);
        msg.setRequirementId(req.getId());
        msg.setRequirementsSpec(objectMapper.convertValue(req, Object.class));
        redis.convertAndSend(queueCodingApproved, msg);

        log.info("Run {} approved by {}, coding agent unblocked", runId, reviewerEmail);
    }

    // ── Reject requirements ───────────────────────────────────────────────

    @Transactional
    public void rejectRequirements(UUID runId, String reviewerEmail, String notes) {
        updateStatus(runId, PipelineRun.RunStatus.FAILED);
        PipelineRun run = getRunOrThrow(runId);
        run.setErrorMessage("Rejected by " + reviewerEmail + ": " + notes);
        runRepo.save(run);
        log.info("Run {} rejected by {}", runId, reviewerEmail);
    }

    // ── Handle: PR created ────────────────────────────────────────────────

    @Transactional
    public void onPrCreated(PrCreatedMessage msg) {
        updateStatus(msg.getRunId(), PipelineRun.RunStatus.SUPERVISING);
        log.info("PR created for run {}: {}", msg.getRunId(), msg.getPrUrl());
    }

    // ── Handle: pipeline complete ─────────────────────────────────────────

    @Transactional
    public void onPipelineComplete(PipelineCompleteMessage msg) {
        updateStatus(msg.getRunId(), PipelineRun.RunStatus.COMPLETE);
        log.info("Pipeline complete for run {}. Email sent: {}", msg.getRunId(), msg.isEmailSent());
    }

    // ── Handle: pipeline failed ───────────────────────────────────────────

    @Transactional
    public void onPipelineFailed(PipelineFailedMessage msg) {
        PipelineRun run = getRunOrThrow(msg.getRunId());
        run.setStatus(PipelineRun.RunStatus.FAILED);
        run.setErrorMessage("[" + msg.getAgent() + "] " + msg.getError());
        runRepo.save(run);
        log.error("Pipeline failed at agent {} for run {}: {}", msg.getAgent(), msg.getRunId(), msg.getError());
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<PipelineRun> getAllRuns() {
        return runRepo.findAllByOrderByCreatedAtDesc();
    }

    public PipelineRun getRun(UUID id) {
        return getRunOrThrow(id);
    }

    public List<Requirement> getRequirements(UUID runId) {
        return requirementRepo.findByRunId(runId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PipelineRun getRunOrThrow(UUID id) {
        return runRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));
    }

    private void updateStatus(UUID runId, PipelineRun.RunStatus status) {
        PipelineRun run = getRunOrThrow(runId);
        run.setStatus(status);
        runRepo.save(run);
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
