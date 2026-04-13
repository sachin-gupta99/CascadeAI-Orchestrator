package com.cascadeAI.Orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cascadeAI.Orchestrator.config.RedisStreams;
import com.cascadeAI.Orchestrator.dto.*;
import com.cascadeAI.Orchestrator.model.*;
import com.cascadeAI.Orchestrator.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineService {

    private final PipelineRunRepository runRepo;
    private final RequirementRepository requirementRepo;
    private final ApprovalRepository approvalRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisStreams redisStreams;

    @Value("${pipeline.senior-engineer-email}")
    private String seniorEngineerEmail;

    // ── Trigger a new pipeline run ────────────────────────────────────────

    @Transactional
    public void onPipelineStart(PipelineStartMessage msg) {
        PipelineRun run = PipelineRun.builder()
                .runId(msg.getRunId())
                .pipelineName(msg.getTranscriptFileName())
                .status(PipelineRun.RunStatus.PENDING)
                .transcript(Transcript.builder()
                        .transcriptFileId(msg.getTranscriptFileId())
                        .transcriptFileName(msg.getTranscriptFileName())
                        .build())
                .build();

        run.getTranscript().setRun(run);
        runRepo.save(run);
    }

    @Transactional
    public void onTranscriptStart(String runId) {

        // System.out.println("Transcript started for run " + runId);

        PipelineRun run = runRepo.findById(runId).orElseThrow();
        // System.out.println(run);
        run.setStatus(PipelineRun.RunStatus.TRANSCRIBING);
        runRepo.save(run);

        TranscriptStartMessage msg = new TranscriptStartMessage();
        msg.setRunId(runId);
        msg.setTranscriptFileId(run.getTranscript().getTranscriptFileId());
        msg.setTranscriptFileName(run.getTranscript().getTranscriptFileName());
        publishToStream(redisStreams.getTranscriptStart(), msg);
        log.info("Transcript started for run {}", runId);
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
                .acceptanceCriteria(spec.getAcceptanceCriteria())
                .rawSpec(toJson(spec))
                .build();
        requirementRepo.save(req);

        // Create pending approval gate
        updateStatus(run.getRunId(), PipelineRun.RunStatus.AWAITING_APPROVAL);
        log.info("Requirements saved, awaiting approval for run {}", run.getRunId());
    }

    // // ── Approve requirements (human-in-the-loop) ──────────────────────────

    @Transactional
    public void approveRequirements(String runId, String reviewerEmail, String notes, String repoFullName) {
        PipelineRun run = getRunOrThrow(runId);
        List<Requirement> reqs = requirementRepo.findByRun_RunId(runId);
        if (reqs.isEmpty())
            throw new IllegalStateException("No requirements found for run: " + runId);

        Requirement req = reqs.get(0);
        updateStatus(run.getRunId(), PipelineRun.RunStatus.CODING);

        // Create approval record
        Approval approval = Approval.builder()
                .run(run)
                .requirement(req)
                .status(Approval.ApprovalStatus.APPROVED)
                .reviewerEmail(reviewerEmail)
                .notes(notes)
                .decidedAt(Instant.now())
                .build();

        approvalRepo.save(approval);

        // Unblock coding agent via Redis Stream
        CodingApprovedMessage msg = new CodingApprovedMessage();
        msg.setRunId(runId);
        msg.setRequirementId(req.getId());
        msg.setRequirementsSpec(objectMapper.convertValue(req, Object.class));
        msg.setRepoFullName(repoFullName);
        publishToStream(redisStreams.getCodingApproved(), msg);

        log.info("Run {} approved by {}, coding agent unblocked", runId, reviewerEmail);
    }

    // // ── Reject requirements ───────────────────────────────────────────────

    @Transactional
    public void rejectRequirements(String runId, String reviewerEmail, String notes) {
        updateStatus(runId, PipelineRun.RunStatus.FAILED);
        PipelineRun run = getRunOrThrow(runId);
        run.setErrorMessage("Rejected by " + reviewerEmail + ": " + notes);
        runRepo.save(run);
        log.info("Run {} rejected by {}", runId, reviewerEmail);
    }

    // // ── Handle: PR created ────────────────────────────────────────────────

    @Transactional
    public void onPrCreated(PrCreatedMessage msg) {
        PipelineRun run = getRunOrThrow(msg.getRunId());
        run.setStatus(PipelineRun.RunStatus.PULL_REQUEST_CREATED);
        run.setPullRequestUrl(msg.getPrUrl());
        runRepo.save(run);
        log.info("PR created for run {}: {}", msg.getRunId(), msg.getPrUrl());
    }

    // // ── Verify PR (human-in-the-loop) ────────────────────────────────────

    @Transactional
    public void verifyPr(String runId, String reviewerEmail, String notes) {
        PipelineRun run = getRunOrThrow(runId);
        updateStatus(runId, PipelineRun.RunStatus.MAILING);

        PrApprovedMessage msg = new PrApprovedMessage();
        msg.setRunId(runId);
        msg.setPrUrl(run.getPullRequestUrl());
        msg.setReviewerEmail(reviewerEmail);
        msg.setNotes(notes);
        msg.setSeniorEngineerEmail(seniorEngineerEmail);
        publishToStream(redisStreams.getPrApproved(), msg);

        log.info("PR verified for run {} by {}, mailing agent unblocked", runId, reviewerEmail);
    }

    // // ── Reject PR ────────────────────────────────────────────────────────

    @Transactional
    public void rejectPr(String runId, String reviewerEmail, String notes) {
        updateStatus(runId, PipelineRun.RunStatus.FAILED);
        PipelineRun run = getRunOrThrow(runId);
        run.setErrorMessage("PR rejected by " + reviewerEmail + ": " + notes);
        runRepo.save(run);
        log.info("PR rejected for run {} by {}", runId, reviewerEmail);
    }

    // // ── Handle: pipeline complete ─────────────────────────────────────────

    @Transactional
    public void onPipelineComplete(PipelineCompleteMessage msg) {
        updateStatus(msg.getRunId(), PipelineRun.RunStatus.COMPLETE);
        log.info("Pipeline complete for run {}. Email sent: {}", msg.getRunId(),
                msg.isEmailSent());
    }

    // // ── Handle: pipeline failed ───────────────────────────────────────────

    @Transactional
    public void onPipelineFailed(PipelineFailedMessage msg) {
        PipelineRun run = getRunOrThrow(msg.getRunId());
        run.setStatus(PipelineRun.RunStatus.FAILED);
        run.setErrorMessage("[" + msg.getAgent() + "] " + msg.getError());
        runRepo.save(run);
        log.error("Pipeline failed at agent {} for run {}: {}", msg.getAgent(),
                msg.getRunId(), msg.getError());
    }

    // ── Edit requirement ──────────────────────────────────────────────────

    @Transactional
    public Requirement updateRequirement(String runId, String requirementId,
            String title, String description,
            Requirement.Priority priority,
            List<String> acceptanceCriteria) {
        // Ensure the run exists
        getRunOrThrow(runId);

        Requirement req = requirementRepo.findById(requirementId)
                .orElseThrow(() -> new IllegalArgumentException("Requirement not found: " + requirementId));

        if (!req.getRun().getRunId().equals(runId)) {
            throw new IllegalArgumentException("Requirement " + requirementId + " does not belong to run " + runId);
        }

        if (title != null)
            req.setTitle(title);
        if (description != null)
            req.setDescription(description);
        if (priority != null)
            req.setPriority(priority);
        if (acceptanceCriteria != null)
            req.setAcceptanceCriteria(acceptanceCriteria);

        return requirementRepo.save(req);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public List<PipelineRun> getAllRuns() {
        return runRepo.findAllByOrderByCreatedAtDesc();
    }

    public PipelineRun getRun(String id) {
        return getRunOrThrow(id);
    }

    public List<Requirement> getRequirements(String runId) {
        return requirementRepo.findByRun_RunId(runId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private PipelineRun getRunOrThrow(String runId) {
        return runRepo.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + runId));
    }

    private void updateStatus(String runId, PipelineRun.RunStatus status) {
        PipelineRun run = getRunOrThrow(runId);
        run.setStatus(status);
        runRepo.save(run);
    }

    private void publishToStream(String streamKey, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            StringRecord record = StreamRecords.string(Map.of("payload", json))
                    .withStreamKey(streamKey);
            redis.opsForStream().add(record);
            log.debug("Published to stream {}: {}", streamKey, json.substring(0, Math.min(json.length(), 200)));
        } catch (Exception e) {
            log.error("Failed to publish to stream {}: {}", streamKey, e.getMessage(), e);
            throw new RuntimeException("Failed to publish to stream", e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
