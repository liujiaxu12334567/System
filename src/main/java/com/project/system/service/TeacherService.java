package com.project.system.service;

import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Application;
import com.project.system.entity.Exam;
import com.project.system.entity.Notification;
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

    String startCheckIn(Long courseId);

    /**
     * 结束课堂签到
     */
    void stopCheckIn(Long courseId);

    /**
     * 获取实时签到数据
     */
    Map<String, Object> getCheckInStatus(Long courseId);

    void sendNotification(String title, String content);

    /**
     * 获取考试作弊记录
     */
    List<Map<String, Object>> getExamCheatingRecords(Long examId);
    List<Notification> getMyNotifications();
    // 【新增】获取仪表盘统计数据 (学生数、出勤率、图表数据等)
    Map<String, Object> getDashboardStats();

    // 【新增】获取教师关联的考试列表 (用于下拉框)
    List<Exam> getTeacherExams();
    // 【新增】回复通知
    void replyNotification(Long notificationId, String content);

    // 【新增】获取当前教师的课程列表（含多班级与人数统计）
    List<com.project.system.entity.Course> getMyCourses();

    // 【新增】在线提问（老师发布、查看问答）
    com.project.system.entity.OnlineQuestion createOnlineQuestion(com.project.system.entity.OnlineQuestion question);
    java.util.List<com.project.system.entity.OnlineQuestion> listOnlineQuestions(Long courseId);
    java.util.List<com.project.system.entity.OnlineAnswer> listOnlineAnswers(Long questionId);

    // 【新增】课堂控制：队列与点名
    java.util.List<com.project.system.entity.OnlineAnswer> listQueue(Long questionId);
    void callAnswer(Long answerId);

    // 【新增】课堂聊天
    java.util.List<com.project.system.entity.CourseChat> listCourseChat(Long courseId, int limit);
    com.project.system.entity.CourseChat sendCourseChat(Long courseId, String content);
}
