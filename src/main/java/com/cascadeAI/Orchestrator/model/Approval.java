package com.cascadeAI.Orchestrator.model;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import com.fasterxml.jackson.annotation.JsonBackReference;

import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@ToString(exclude = { "run", "requirement" })
@EqualsAndHashCode(exclude = { "run", "requirement" })
public class Approval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    @JsonBackReference
    PipelineRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id")
    @JsonBackReference
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
