CREATE TABLE IF NOT EXISTS attendance_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    date DATE NOT NULL,
    present TINYINT(1) NOT NULL DEFAULT 0,
    batch_id VARCHAR(64) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_attendance_class_course_date ON attendance_record (class_id, course_id, date);
CREATE INDEX idx_attendance_student ON attendance_record (student_id);
