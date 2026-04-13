package com.cascadeAI.Orchestrator.repository;

import com.cascadeAI.Orchestrator.model.Requirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RequirementRepository extends JpaRepository<Requirement, String> {
    List<Requirement> findByRun_RunId(String runId);
}
