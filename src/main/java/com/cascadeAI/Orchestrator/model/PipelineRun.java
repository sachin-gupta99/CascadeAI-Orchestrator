package com.cascadeAI.Orchestrator.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "pipeline_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(exclude = "transcript")
@EqualsAndHashCode(exclude = "transcript")
public class PipelineRun {

    @Id
    @Column(unique = true, nullable = false, name = "run_id")
    String runId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    RunStatus status = RunStatus.PENDING;

    @Column(name = "pipeline_name")
    String pipelineName;

    @OneToOne(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    Transcript transcript;

    Instant meetingDate;

    @Column(columnDefinition = "TEXT")
    String errorMessage;

    @Column(name = "pull_request_url")
    String pullRequestUrl;

    @CreationTimestamp
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;

    public enum RunStatus {
        PENDING, TRANSCRIBING, ANALYZING,
        AWAITING_APPROVAL, CODING, PULL_REQUEST_CREATED,
        MAILING, COMPLETE, FAILED
    }
}
