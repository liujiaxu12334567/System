package com.project.system.mapper;

import com.project.system.entity.Exam;
import com.project.system.entity.ExamRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ExamMapper {

    // 【新增方法 1】创建考试 (供组长/教师使用)
    void insertExam(Exam exam);

    // 【新增方法 2】更新考试信息 (例如修改标题、时间、内容)
    void updateExam(Exam exam);

    // 【新增方法 3】删除考试
    void deleteExamById(Long id);

    // --- 原有方法（保留）---

    // 4. 根据课程ID获取所有考试
    List<Exam> selectExamsByCourseId(Long courseId);

    // 5. 根据ID获取单个考试详情
    Exam findExamById(Long id);

    // 6. 提交考试记录
    void insertExamRecord(ExamRecord record);

    // 7. 根据用户ID和考试ID查询考试记录
    ExamRecord findRecordByUserIdAndExamId(@Param("userId") Long userId, @Param("examId") Long examId);

    // 8. 根据考试ID查询所有考试记录 (供教师查看成绩)
    List<ExamRecord> selectExamRecordsByExamId(Long examId);

    // 9. 学生端：获取“我的考试”（包含已答/未答，带课程信息与提交信息）
    List<Map<String, Object>> selectStudentExamOverview(@Param("userId") Long userId, @Param("classId") Long classId);
}
