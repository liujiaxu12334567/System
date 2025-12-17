package com.project.system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Component
public class SchemaVerifier implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaVerifier.class);

    private final DataSource dataSource;

    public SchemaVerifier(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        warnIfMissingColumn("online_question", "correct_answer",
                "检测到数据库缺少 online_question.correct_answer 列：请执行 `docs/online_question_correct_answer.sql`（或手动 ALTER TABLE）后重试。");
        warnIfMissingColumn("sys_course", "day_of_week",
                "检测到数据库缺少 sys_course.day_of_week 列：请执行 `docs/sys_course_schedule.sql`（或手动 ALTER TABLE）后重试。");
        warnIfMissingColumn("sys_course", "start_time",
                "检测到数据库缺少 sys_course.start_time 列：请执行 `docs/sys_course_schedule.sql`（或手动 ALTER TABLE）后重试。");
        warnIfMissingColumn("sys_course", "end_time",
                "检测到数据库缺少 sys_course.end_time 列：请执行 `docs/sys_course_schedule.sql`（或手动 ALTER TABLE）后重试。");
        warnIfMissingColumn("sys_course_schedule", "period_index",
                "检测到数据库缺少 sys_course_schedule 表（或 period_index 列）：请执行 `docs/sys_course_schedule_table.sql`（或手动建表）后重试。");
        warnIfMissingColumn("sys_user", "college",
                "检测到数据库缺少 sys_user.college 列：请执行 `docs/sys_user_college.sql`（或手动 ALTER TABLE）后重试。");
    }

    private void warnIfMissingColumn(String tableName, String columnName, String warnMessage) {
        String sql = """
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """;
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getLong(1) == 0) {
                    log.error(warnMessage);
                }
            }
        } catch (Exception e) {
            log.warn("数据库结构检查失败：{}.{}（{}）", tableName, columnName, e.getMessage());
        }
    }
}
