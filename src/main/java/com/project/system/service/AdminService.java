package com.project.system.service;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Course;
import com.project.system.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AdminService {
    PaginationResponse<?> listUsers(String keyword, String roleType, String classId, int pageNum, int pageSize);
    void addUser(Map<String, Object> userMap);
    void updateUser(User user);
    void deleteUser(Long id);
    String batchEnroll(BatchEnrollmentRequest request);
    String batchEnrollFromFile(MultipartFile file, Long targetClassId, Long startId, String major);

    Object listCourses();
    void addCourse(Course course);
    void batchAssignCourse(String name, String semester, java.util.List<String> teacherNames, java.util.List<Object> rawClassIds);
    void updateCourse(Course course);
    void deleteCourse(Long id);
    // ...
    // 修改原发送接口，支持更多参数
    void sendNotification(String title, String content, String targetType, List<Long> specificUserIds, boolean needReply);

    // 获取管理员发送记录
    List<Object> getNotificationHistory();

    // 获取某条通知的统计详情
    List<Map<String, Object>> getNotificationStats(String batchId);
    // 获取通知统计汇总（阅读率/回复率等）
    Map<String, Object> getNotificationStatsSummary(String batchId);
    // ...
    Object listClasses();
    Object listPendingApplications();
    void reviewApplication(Long appId, String status);
    void sendNotificationToUsers(List<Long> userIds, String title, String content);
}
