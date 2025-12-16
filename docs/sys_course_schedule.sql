-- 为课程增加排课时间字段（用于课程表/到点才能开在线课堂）
-- MySQL 8.0+
--
-- 说明：
-- - day_of_week：1=周一 ... 7=周日（与 Java DayOfWeek#getValue 对齐）
-- - start_time/end_time：仅表示当天时间段（TIME），不绑定具体日期
--
-- 兼容：
-- - 若你之前已创建过 start_time/end_time（DATETIME），此脚本会尝试自动 MODIFY 为 TIME

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1) day_of_week
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'day_of_week') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `day_of_week` TINYINT NULL COMMENT ''1=周一...7=周日'' AFTER `semester`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) start_time
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'start_time') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `start_time` TIME NULL AFTER `day_of_week`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'start_time' AND DATA_TYPE <> 'time') = 1,
  'ALTER TABLE `sys_course` MODIFY COLUMN `start_time` TIME NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) end_time
SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'end_time') = 0,
  'ALTER TABLE `sys_course` ADD COLUMN `end_time` TIME NULL AFTER `start_time`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
  (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_course' AND COLUMN_NAME = 'end_time' AND DATA_TYPE <> 'time') = 1,
  'ALTER TABLE `sys_course` MODIFY COLUMN `end_time` TIME NULL',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET FOREIGN_KEY_CHECKS = 1;
