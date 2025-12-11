package com.project.system.service;

import com.project.system.entity.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface StudentService {
    List<Notification> getMyNotifications();

    // 【新增】标记通知为已读
    void markNotificationAsRead(Long id);

    List<Map<String, Object>> getPendingTasks();
    Course getCourseInfo(Long courseId);
    List<Material> getCourseMaterials(Long courseId);
    void submitQuiz(Long materialId, Integer score, String userAnswers, String textAnswer, List<MultipartFile> files);
    String chatWithAiTutor(Long materialId, List<Map<String, String>> history);
    QuizRecord getQuizRecord(Long materialId);
    List<Exam> getCourseExams(Long courseId);
    void submitExam(Long examId, Integer score, String userAnswers, Integer cheatCount);
    Object getExamRecord(Long examId);
    List<Map<String, Object>> getRecentActivities();
}