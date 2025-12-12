-- 新增用于教师端仪表盘的数据统计表

CREATE TABLE IF NOT EXISTS teacher_interaction (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  class_id BIGINT,
  teacher_id BIGINT,
  date DATE NOT NULL,
  talk_time_seconds INT DEFAULT 0,
  student_talk_seconds INT DEFAULT 0,
  group_talk_seconds INT DEFAULT 0,
  interaction_count INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS attendance_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  class_id BIGINT,
  date DATE NOT NULL,
  expected INT DEFAULT 0,
  present INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS assignment_summary (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  class_id BIGINT,
  date DATE NOT NULL,
  total INT DEFAULT 0,
  submitted INT DEFAULT 0,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 可选：课程-班级关联，长远替代 responsible_class_ids
CREATE TABLE IF NOT EXISTS course_class (
  course_id BIGINT NOT NULL,
  class_id BIGINT NOT NULL,
  PRIMARY KEY(course_id, class_id)
);
