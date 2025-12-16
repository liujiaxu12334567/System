-- 为教师账号增加学院字段（用于分类筛选）
-- MySQL

ALTER TABLE sys_user
  ADD COLUMN college VARCHAR(128) NULL AFTER teacher_rank;

