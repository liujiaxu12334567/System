-- 为在线课堂题目增加正确答案字段（用于判题/统计）
-- MySQL

ALTER TABLE online_question
  ADD COLUMN correct_answer VARCHAR(512) NULL AFTER content;

