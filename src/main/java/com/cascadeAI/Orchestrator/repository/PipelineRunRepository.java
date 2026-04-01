package com.cascadeAI.Orchestrator.repository;

import com.cascadeAI.Orchestrator.model.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    List<PipelineRun> findAllByOrderByCreatedAtDesc();

    List<PipelineRun> findByStatusOrderByCreatedAtDesc(PipelineRun.RunStatus status);
}
