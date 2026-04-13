-- Pipeline Runs Table
CREATE TABLE IF NOT EXISTS pipeline_runs (
    run_id VARCHAR(255) PRIMARY KEY,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'TRANSCRIBING', 'ANALYZING', 'AWAITING_APPROVAL', 'CODING', 'PULL_REQUEST_CREATED', 'MAILING', 'COMPLETE', 'FAILED')),
    meeting_date TIMESTAMP WITH TIME ZONE,
    pull_request_url VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Transcripts Table
CREATE TABLE IF NOT EXISTS transcripts (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL UNIQUE,
    transcript_file_id VARCHAR(255),
    transcript_file_name VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transcript_pipeline_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (run_id) ON DELETE CASCADE
);

-- Requirements Table
CREATE TABLE IF NOT EXISTS requirements (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    acceptance_criteria TEXT[],
    raw_spec JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_req_pipeline_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (run_id) ON DELETE CASCADE
);

-- Approvals Table
CREATE TABLE IF NOT EXISTS approvals (
    id VARCHAR(255) PRIMARY KEY,
    run_id VARCHAR(255) NOT NULL,
    requirement_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reviewer_email VARCHAR(255),
    notes TEXT,
    decided_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appr_pipeline_run FOREIGN KEY (run_id) REFERENCES pipeline_runs (run_id) ON DELETE CASCADE,
    CONSTRAINT fk_appr_requirement FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE SET NULL
);
