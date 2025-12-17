package com.project.system.service;

import com.project.system.dto.CourseTimetableResponse;
import com.project.system.entity.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface StudentService {
    List<Notification> getMyNotifications();

    // 【新增】标记通知为已读
    void markNotificationAsRead(Long id);
    // ...
    boolean doCheckIn(Long courseId);

    // 【核心修复】不仅检查签到是否开启，还检查学生是否已签
    // 请确保 StudentService 接口中定义了此方法: Map<String, Object> getCheckInStatus(Long courseId);
    // 如果接口定义的是 boolean isCheckInActive(Long courseId)，请去修改接口定义
    Map<String, Object> getCheckInStatus(Long courseId);

    boolean isCheckInActive(Long courseId);

    Map<String, Object> getClassroomStatus(Long courseId);
    List<Map<String, Object>> getPendingTasks();
    Course getCourseInfo(Long courseId);
    List<Material> getCourseMaterials(Long courseId);
    List<Course> getPersonalLearningCourses();
    CourseTimetableResponse getStudentCourseSchedule();
    void submitQuiz(Long materialId, Integer score, String userAnswers, String textAnswer, List<MultipartFile> files);
    String chatWithAiTutor(Long materialId, List<Map<String, String>> history);
    QuizRecord getQuizRecord(Long materialId);
    List<Exam> getCourseExams(Long courseId);
    List<Map<String, Object>> getMyExams(Integer limit);
    void submitExam(Long examId, Integer score, String userAnswers, Integer cheatCount);
    Object getExamRecord(Long examId);
    List<Map<String, Object>> getRecentActivities();

    // 【新增】在线问答
    List<OnlineQuestion> listOnlineQuestions(Long courseId);
    OnlineAnswer answerOnlineQuestion(Long questionId, String answerText);
    OnlineAnswer handRaise(Long questionId);
    OnlineAnswer raceAnswer(Long questionId);
    List<OnlineAnswer> listAnswers(Long questionId);

    // 【新增】课堂聊天
    List<CourseChat> listCourseChat(Long courseId, int limit);
    CourseChat sendCourseChat(Long courseId, String content);
}
