package com.project.system.service;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Course;
import com.project.system.entity.User;
import org.springframework.web.multipart.MultipartFile;
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

    Object listClasses();
    Object listPendingApplications();
    void reviewApplication(Long appId, String status);
}