package com.project.system.service;

import com.project.system.entity.Application;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface QualityService {
    void submitCreditApplication(String type, String title, String desc, MultipartFile file) throws IOException;
    void submitLeaveApplication(Application app);
    List<Application> getManagedApplications();
    void sendNotificationToManagedClasses(String title, String content);
    void reviewApplication(Long id, String status);

    // 获取素质教师负责班级的出勤汇总
    List<Map<String, Object>> getManagedAttendance();

    // 获取班级-课程-学生的出勤记录明细（班级必选，课程/学生可选）
    List<com.project.system.dto.AttendanceRecordResponse> getAttendanceRecords(Long classId, Long courseId, Long studentId);
}
