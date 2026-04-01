package com.cascadeAI.Orchestrator.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    RunStatus status = RunStatus.PENDING;

    String transcriptFileId;
    String transcriptFileName;
    Instant meetingDate;

    @Column(columnDefinition = "TEXT")
    String errorMessage;

    @CreationTimestamp
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;

    public enum RunStatus {
        PENDING, TRANSCRIBING, ANALYZING,
        AWAITING_APPROVAL, CODING, SUPERVISING,
        COMPLETE, FAILED
    }
}
