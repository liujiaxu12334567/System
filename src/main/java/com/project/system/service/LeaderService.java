package com.project.system.service;

import com.project.system.entity.Course;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

public interface LeaderService {
    /** 发布考试 */
    void publishCourseExam(Long courseId, Map<String, Object> examData);

    /** 获取团队成员 */
    List<Object> listMyTeamMembers();

    /** 获取负责的课程 */
    List<Course> listMyCourses();

    /** 上传/发布资料 */
    void uploadCourseMaterial(Long courseId, String type, String title, String content, String deadline, MultipartFile file);

    /** 发送通知 (MQ) */
    void sendNotification(String title, String content, List<String> targetUsernames);

    /** 更新课程 */
    void updateCourse(Course course);

    /** 删除课程 */
    void deleteCourse(Long id);

    /** 批量分配课程 */
    void batchAssignCourse(String name, String semester, List<String> teacherNames, List<Object> rawClassIds);

    /** 更新资料截止时间 */
    void updateMaterialDeadline(Long materialId, String newDeadline);

    /** 获取待审核延期申请 */
    List<Object> listPendingApplications();

    /** 审核申请 */
    void reviewApplication(Long appId, String status);
}