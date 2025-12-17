package com.project.system.service;

import com.project.system.dto.PaginationResponse;
import com.project.system.dto.AnalysisResultResponse;
import com.project.system.dto.StudentPortraitResponse;
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

    /**
     * 获取考试监控记录（包含所有已提交学生的成绩/作弊次数/最终成绩）
     */
    List<Map<String, Object>> getExamMonitorRecords(Long examId);
    List<Notification> getMyNotifications();
    void markNotificationAsRead(Long id);
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

    // 【新增】课堂会话控制：开课/下课/状态
    java.util.Map<String, Object> startClassroom(Long courseId);
    java.util.Map<String, Object> endClassroom(Long courseId);
    java.util.Map<String, Object> getClassroomStatus(Long courseId);
    java.util.Map<String, Object> getClassroomParticipants(Long courseId);

    // 【新增】重置课堂（清空聊天、在线提问与回答）
    void resetClassroom(Long courseId);

    // 【新增】在线测试表现汇总
    java.util.List<java.util.Map<String, Object>> getClassroomPerformance(Long courseId);

    AnalysisResultResponse generateClassroomAnalysisResult(Long courseId, String metric);

    // 【新增】学生AI综合画像（按班级/课程聚合出勤/提交/互动）
    java.util.List<StudentPortraitResponse> getStudentPortraits(Long classId, java.util.List<Long> studentIds);
}
