package com.project.system.service;

import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Application;
import com.project.system.entity.QuizRecord;

import java.util.List;
import java.util.Map;

public interface TeacherService {

    /**
     * 获取执教班级的学生列表 (分页)
     */
    PaginationResponse<?> listStudents(String keyword, String classId, int pageNum, int pageSize);

    /**
     * 提交申请 (增/删/改/延期)
     */
    void submitApplication(Application app);

    /**
     * 获取教师执教班级的资料列表
     */
    List<Object> listTeachingMaterials();

    /**
     * 获取某个资料的提交记录
     */
    List<Map<String, Object>> getSubmissionsForMaterial(Long materialId);

    /**
     * 批改作业
     */
    void gradeSubmission(QuizRecord record);

    /**
     * 打回作业
     */
    void rejectSubmission(Long recordId);

    /**
     * 发送通知 (集成 RabbitMQ)
     */
    void sendNotification(String title, String content);

    /**
     * 获取考试作弊记录
     */
    List<Map<String, Object>> getExamCheatingRecords(Long examId);
}