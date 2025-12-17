-- 班级课表排课明细表：sys_course_schedule（支持同一课程一周/一天多次排课 + 节次准确回显）
-- MySQL 8.0+

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS `sys_course_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `class_id` BIGINT NOT NULL COMMENT '班级ID（与 sys_course.class_id 对齐）',
  `day_of_week` TINYINT NOT NULL COMMENT '1=周一...7=周日（与 Java DayOfWeek#getValue 对齐）',
  `period_index` TINYINT NOT NULL COMMENT '节次（1..5）',
  `start_time` TIME NOT NULL,
  `end_time` TIME NULL,
  `course_id` BIGINT NULL COMMENT '课程ID（sys_course.id），为空表示无课',
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_day_period` (`class_id`, `day_of_week`, `period_index`),
  KEY `idx_course_day_time` (`course_id`, `day_of_week`, `start_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 可选：从旧版 sys_course(day_of_week/start_time/end_time) 迁移到新表（无法 100% 还原“第几节”，采用“同一天按 start_time 排序后依次映射到 1..5 节”的策略）
-- 若你已全量使用新课表网格保存过，可跳过这段。
INSERT INTO `sys_course_schedule` (`class_id`, `day_of_week`, `period_index`, `start_time`, `end_time`, `course_id`)
SELECT
  t.class_id,
  t.day_of_week,
  t.rn AS period_index,
  t.start_time,
  t.end_time,
  t.id AS course_id
FROM (
  SELECT
    c.id,
    c.class_id,
    c.day_of_week,
    c.start_time,
    c.end_time,
    ROW_NUMBER() OVER (PARTITION BY c.class_id, c.day_of_week ORDER BY c.start_time ASC, c.id ASC) AS rn
  FROM sys_course c
  WHERE c.class_id IS NOT NULL
    AND c.day_of_week IS NOT NULL
    AND c.start_time IS NOT NULL
) t
WHERE t.rn <= 5
ON DUPLICATE KEY UPDATE
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  course_id = VALUES(course_id),
  update_time = CURRENT_TIMESTAMP;

SET FOREIGN_KEY_CHECKS = 1;

