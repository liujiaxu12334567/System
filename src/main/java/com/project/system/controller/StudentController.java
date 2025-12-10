package com.project.system.controller;

import com.project.system.dto.PaginationResponse;
import com.project.system.dto.PasswordChangeRequest;
import com.project.system.entity.Course;
import com.project.system.entity.Exam;
import com.project.system.entity.Material;
import com.project.system.entity.QuizRecord;
import com.project.system.entity.Task;
import com.project.system.entity.User;
import com.project.system.service.StudentService; // 假设已创建学生服务层
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学生用户相关的API接口控制器
 * 统一处理 /student 路径下的请求
 */
@RestController
@RequestMapping("/student")
public class StudentController {

    private final StudentService studentService;

    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    /**
     * 私有辅助方法：从安全上下文中获取当前登录用户的ID
     * 假设 Principal 是 com.project.system.entity.User 类型，且包含 getId() 方法
     */
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User) {
            return ((User) authentication.getPrincipal()).getId();
        }
        // 在生产环境中，应该抛出更具体的异常，或由 Spring Security 拦截未授权请求
        throw new IllegalStateException("用户未认证或身份信息无效");
    }

    // ====================================================================
    // 1. 个人资料管理 (Profile Management)
    // ====================================================================

    /**
     * 获取当前学生的个人信息
     * GET /student/profile
     * @return 学生的个人信息
     */
    @GetMapping("/profile")
    public ResponseEntity<User> getMyProfile() {
        try {
            Long studentId = getCurrentUserId();
            User user = studentService.getStudentProfile(studentId);
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            // 通常由服务层抛出，例如用户不存在
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 修改密码
     * PUT /student/password
     * @param request 包含旧密码和新密码的请求体
     * @return 成功或失败的响应消息
     */
    @PutMapping("/password")
    public ResponseEntity<String> changePassword(@RequestBody PasswordChangeRequest request) {
        try {
            Long studentId = getCurrentUserId();
            studentService.changeStudentPassword(
                    studentId,
                    request.getOldPassword(),
                    request.getNewPassword()
            );
            return ResponseEntity.ok("密码修改成功");
        } catch (IllegalArgumentException e) {
            // 例如：旧密码不匹配、新密码格式不正确
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("密码修改失败: " + e.getMessage());
        }
    }

    // ====================================================================
    // 2. 课程管理 (Course Management)
    // ====================================================================

    /**
     * 获取学生已选课程列表
     * GET /student/courses/my
     * @return 已选课程列表
     */
    @GetMapping("/courses/my")
    public ResponseEntity<List<Course>> getMyEnrolledCourses() {
        Long studentId = getCurrentUserId();
        List<Course> courses = studentService.getEnrolledCourses(studentId);
        return ResponseEntity.ok(courses);
    }

    /**
     * 获取所有可供选修的课程列表（支持分页和名称搜索）
     * GET /student/courses/all?pageNum=1&pageSize=10&courseName=Java
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param courseName 课程名称关键字 (可选)
     * @return 分页响应对象
     */
    @GetMapping("/courses/all")
    public ResponseEntity<PaginationResponse<Course>> getAllAvailableCourses(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String courseName) {

        PaginationResponse<Course> response = studentService.getAvailableCourses(pageNum, pageSize, courseName);
        return ResponseEntity.ok(response);
    }

    /**
     * 选课/申请课程
     * POST /student/courses/{courseId}/enroll
     * @param courseId 课程ID
     * @return 成功消息
     */
    @PostMapping("/courses/{courseId}/enroll")
    public ResponseEntity<String> enrollCourse(@PathVariable Long courseId) {
        try {
            Long studentId = getCurrentUserId();
            studentService.enrollCourse(studentId, courseId);
            return ResponseEntity.status(HttpStatus.CREATED).body("选课成功或申请已提交");
        } catch (IllegalStateException e) {
            // 例如：课程不存在，或已选该课程
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("选课失败: " + e.getMessage());
        }
    }

    /**
     * 查看课程详细信息
     * GET /student/courses/{courseId}
     * @param courseId 课程ID
     * @return 课程详情
     */
    @GetMapping("/courses/{courseId}")
    public ResponseEntity<Course> getCourseDetail(@PathVariable Long courseId) {
        try {
            Course course = studentService.getCourseDetail(courseId);
            return ResponseEntity.ok(course);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    // ====================================================================
    // 3. 课程学习内容 (Course Content)
    // ====================================================================

    /**
     * 获取课程的学习资料列表
     * GET /student/courses/{courseId}/materials
     * @param courseId 课程ID
     * @return 学习资料列表
     */
    @GetMapping("/courses/{courseId}/materials")
    public ResponseEntity<List<Material>> getCourseMaterials(@PathVariable Long courseId) {
        // 在服务层应检查学生是否已选该课程
        List<Material> materials = studentService.getMaterialsByCourse(courseId);
        return ResponseEntity.ok(materials);
    }

    /**
     * 获取课程的作业/任务列表
     * GET /student/courses/{courseId}/tasks
     * @param courseId 课程ID
     * @return 作业/任务列表
     */
    @GetMapping("/courses/{courseId}/tasks")
    public ResponseEntity<List<Task>> getCourseTasks(@PathVariable Long courseId) {
        // 在服务层应检查学生是否已选该课程
        List<Task> tasks = studentService.getTasksByCourse(courseId);
        return ResponseEntity.ok(tasks);
    }

    // ====================================================================
    // 4. 考试和测验管理 (Exam and Quiz Management)
    // ====================================================================

    /**
     * 获取学生待考/已考的考试列表
     * GET /student/exams/my
     * @return 考试列表
     */
    @GetMapping("/exams/my")
    public ResponseEntity<List<Exam>> getMyExams() {
        Long studentId = getCurrentUserId();
        List<Exam> exams = studentService.getExamsForStudent(studentId);
        return ResponseEntity.ok(exams);
    }

    /**
     * 查看考试详情（如试题列表）
     * GET /student/exams/{examId}
     * @param examId 考试ID
     * @return 考试详情
     */
    @GetMapping("/exams/{examId}")
    public ResponseEntity<Exam> getExamDetail(@PathVariable Long examId) {
        try {
            Exam exam = studentService.getExamDetails(examId);
            return ResponseEntity.ok(exam);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * 提交考试
     * POST /student/exams/{examId}/submit
     * @param examId 考试ID
     * @param examSubmissionDto 考试提交内容（应替换为实际的 DTO）
     * @return 成功消息
     */
    @PostMapping("/exams/{examId}/submit")
    public ResponseEntity<String> submitExam(@PathVariable Long examId, @RequestBody Object examSubmissionDto) {
        try {
            Long studentId = getCurrentUserId();
            // 假设 examSubmissionDto 是一个包含答案的 DTO
            studentService.submitExam(studentId, examId, examSubmissionDto);
            return ResponseEntity.ok("考试提交成功");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("考试提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生的测验记录列表
     * GET /student/quizzes/my
     * @return 测验记录列表
     */
    @GetMapping("/quizzes/my")
    public ResponseEntity<List<QuizRecord>> getMyQuizRecords() {
        Long studentId = getCurrentUserId();
        List<QuizRecord> records = studentService.getQuizRecordsForStudent(studentId);
        return ResponseEntity.ok(records);
    }

    /**
     * 获取特定测验的记录详情
     * GET /student/quizzes/{quizRecordId}
     * @param quizRecordId 测验记录ID
     * @return 测验记录详情
     */
    @GetMapping("/quizzes/{quizRecordId}")
    public ResponseEntity<QuizRecord> getQuizRecordDetail(@PathVariable Long quizRecordId) {
        try {
            QuizRecord record = studentService.getQuizRecordDetail(quizRecordId);
            return ResponseEntity.ok(record);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}