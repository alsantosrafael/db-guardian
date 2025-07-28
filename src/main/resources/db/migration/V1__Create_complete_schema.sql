-- Create complete analysis_runs table with all current fields
-- This represents the final schema state for clean deployments

CREATE TABLE analysis_runs (
    id UUID PRIMARY KEY,
    mode VARCHAR(20) NOT NULL CHECK (mode IN ('STATIC', 'DYNAMIC')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    
    -- Embedded config fields
    dialect VARCHAR(50) NOT NULL,
    migration_paths TEXT,
    schema_path VARCHAR(500),
    code_path VARCHAR(500),
    source VARCHAR(500) NOT NULL,
    
    -- S3 report storage
    report_s3_bucket VARCHAR(255),
    report_s3_key VARCHAR(1000),
    
    -- Summary metrics
    total_issues INTEGER DEFAULT 0,
    critical_issues INTEGER DEFAULT 0,
    warning_issues INTEGER DEFAULT 0,
    info_issues INTEGER DEFAULT 0,
    files_analyzed INTEGER DEFAULT 0,
    queries_analyzed INTEGER DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_analysis_runs_status ON analysis_runs(status);
CREATE INDEX idx_analysis_runs_created_at ON analysis_runs(created_at);
CREATE INDEX idx_analysis_runs_mode ON analysis_runs(mode);
CREATE INDEX idx_analysis_runs_total_issues ON analysis_runs(total_issues);
CREATE INDEX idx_analysis_runs_critical_issues ON analysis_runs(critical_issues);
CREATE INDEX idx_analysis_runs_report_bucket ON analysis_runs(report_s3_bucket);