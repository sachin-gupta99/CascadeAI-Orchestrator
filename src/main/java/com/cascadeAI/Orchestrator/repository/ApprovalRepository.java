package com.cascadeAI.Orchestrator.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.cascadeAI.Orchestrator.model.Approval;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, String> {

}
