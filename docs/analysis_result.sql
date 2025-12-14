CREATE TABLE IF NOT EXISTS analysis_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    metric VARCHAR(64) NOT NULL,
    value_json JSON NOT NULL,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    event_id VARCHAR(64) UNIQUE
);

CREATE INDEX idx_analysis_course_metric ON analysis_result (course_id, metric);
