package com.project.system.service;

import com.project.system.dto.TeacherAnalysisResponse;
import com.project.system.entity.Course;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface LeaderService {
    void publishCourseExam(Long courseId, Map<String, Object> examData);

    List<Object> listMyTeamMembers();

    List<Course> listMyCourses();

    List<TeacherAnalysisResponse> listTeacherAnalysis(String metric);

    void uploadCourseMaterial(Long courseId, String type, String title, String content, String deadline, MultipartFile file);

    void sendNotification(String title, String content, List<String> targetUsernames);

    void updateCourse(Course course);

    void deleteCourse(Long id);

    void batchAssignCourse(String name, String semester, List<String> teacherNames, List<Object> rawClassIds);

    void updateMaterialDeadline(Long materialId, String newDeadline);

    void batchPublishExam(Map<String, Object> examData, List<String> courseNames);

    List<Object> listPendingApplications();

    void batchSendMaterialToTeachers(String type, String title, String content, String deadline, MultipartFile file, List<String> courseNames);

    void reviewApplication(Long appId, String status);
}

