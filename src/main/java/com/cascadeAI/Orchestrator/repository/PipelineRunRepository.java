package com.cascadeAI.Orchestrator.repository;

import com.cascadeAI.Orchestrator.model.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PipelineRunRepository extends JpaRepository<PipelineRun, String> {

    PipelineRun findByRunId(String runId);

    List<PipelineRun> findAllByOrderByCreatedAtDesc();

    List<PipelineRun> findByStatusOrderByCreatedAtDesc(PipelineRun.RunStatus status);
}
