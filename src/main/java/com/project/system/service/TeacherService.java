package com.project.system.service;

import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Application;
import com.project.system.entity.QuizRecord;
import org.springframework.http.ResponseEntity;
import java.util.Map;

public interface TeacherService {
    PaginationResponse<?> listStudents(String keyword, String classId, int pageNum, int pageSize);
    void submitApplication(Application app);
    Object listTeachingMaterials();
    Object getSubmissionsForMaterial(Long materialId);
    void gradeSubmission(QuizRecord record);
    void rejectSubmission(Long recordId);
    void sendNotification(String title, String content); // 将使用 RabbitMQ
    Object getExamCheatingRecords(Long examId);
}