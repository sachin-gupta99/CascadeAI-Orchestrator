package com.cascadeAI.Orchestrator.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    PipelineRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    Requirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    ApprovalStatus status = ApprovalStatus.PENDING;

    String reviewerEmail;

    @Column(columnDefinition = "TEXT")
    String notes;

    Instant decidedAt;

    @CreationTimestamp
    Instant createdAt;

    public enum ApprovalStatus {
        PENDING, APPROVED, REJECTED
    }
}
