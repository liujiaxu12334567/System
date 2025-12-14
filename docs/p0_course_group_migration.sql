-- P0: 课程组/授课分配模型迁移（MySQL 8.0+）
-- 目标：
-- 1) 同一学期/同一课程名 = 1 个课程组（唯一）
-- 2) 每个课程(=班级实例)绑定 group_id + leader_id + teacher_id
-- 3) 强约束：同一课程组下同一班级只能有 1 条记录（避免“同班同科目多老师”）

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) 新增课程组表（唯一键：name+semester）
CREATE TABLE IF NOT EXISTS `sys_course_group` (
  `group_id` BIGINT NOT NULL AUTO_INCREMENT,
  `name` VARCHAR(100) NOT NULL,
  `semester` VARCHAR(50) NOT NULL,
  `leader_id` BIGINT NULL,
  `leader_name` VARCHAR(64) NULL,
  `create_time` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_course_group_name_semester` (`name`, `semester`),
  KEY `idx_course_group_leader` (`leader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 扩展 sys_course：增加 group_id/teacher_id/leader_id（幂等：已存在则跳过）
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'group_id') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `group_id` BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'teacher_id') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `teacher_id` BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'leader_id') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `leader_id` BIGINT NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 回填 teacher_id（按 real_name 匹配 role_type=2/3 的组长/教师）
UPDATE `sys_course` c
JOIN `sys_user` u
  ON u.`real_name` = c.`teacher` AND u.`role_type` IN (2, 3)
SET c.`teacher_id` = u.`user_id`
WHERE c.`teacher_id` IS NULL AND c.`teacher` IS NOT NULL;

-- 4) 回填 leader_id（按 manager_name 匹配 role_type=2 的组长）
UPDATE `sys_course` c
JOIN `sys_user` u
  ON u.`real_name` = c.`manager_name` AND u.`role_type` = 2
SET c.`leader_id` = u.`user_id`
WHERE c.`leader_id` IS NULL AND c.`manager_name` IS NOT NULL;

-- 5) 生成课程组（同名同学期归并；leader 取当前数据的最小 leader_id/manager_name）
INSERT INTO `sys_course_group` (`name`, `semester`, `leader_id`, `leader_name`)
SELECT
  c.`name`,
  c.`semester`,
  MIN(c.`leader_id`) AS `leader_id`,
  MIN(c.`manager_name`) AS `leader_name`
FROM `sys_course` c
WHERE c.`name` IS NOT NULL AND c.`semester` IS NOT NULL
GROUP BY c.`name`, c.`semester`
ON DUPLICATE KEY UPDATE
  `leader_id` = COALESCE(`sys_course_group`.`leader_id`, VALUES(`leader_id`)),
  `leader_name` = COALESCE(`sys_course_group`.`leader_name`, VALUES(`leader_name`));

-- 6) 关联课程到 group_id
UPDATE `sys_course` c
JOIN `sys_course_group` g
  ON g.`name` = c.`name` AND g.`semester` = c.`semester`
SET c.`group_id` = g.`group_id`
WHERE c.`group_id` IS NULL AND c.`name` IS NOT NULL AND c.`semester` IS NOT NULL;

-- 7) 加唯一约束（避免“同班同科目重复记录”）
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND INDEX_NAME = 'uk_course_group_class') = 0,
  'ALTER TABLE `sys_course` ADD UNIQUE KEY `uk_course_group_class` (`group_id`, `class_id`)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
