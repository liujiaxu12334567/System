package com.project.system.controller;

import com.project.system.entity.*;
import com.project.system.mapper.NotificationMapper;
import com.project.system.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private StudentService studentService;
    @Autowired
    private NotificationMapper notificationMapper;
    // 1. 获取课程详情 (Service 层已集成 Redis 缓存)
    @GetMapping("/course/{courseId}/info")
    public ResponseEntity<?> getCourseInfo(@PathVariable Long courseId) {
        Course course = studentService.getCourseInfo(courseId);
        if (course == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(course);
    }

    // 2. 获取课程资料列表
    @GetMapping("/course/{courseId}/materials")
    public ResponseEntity<List<Material>> getCourseMaterials(@PathVariable Long courseId) {
        return ResponseEntity.ok(studentService.getCourseMaterials(courseId));
    }

    // 3. 提交测验/作业
    @PostMapping(value = "/quiz/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitQuiz(
            @RequestParam("materialId") Long materialId,
            @RequestParam(value = "score", required = false, defaultValue = "0") Integer score,
            @RequestParam(value = "userAnswers", required = false) String userAnswers,
            @RequestParam(value = "textAnswer", required = false) String textAnswer,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        try {
            studentService.submitQuiz(materialId, score, userAnswers, textAnswer, files);
            return ResponseEntity.ok("提交成功！");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 4. AI 助教对话 (支持上下文)
    @PostMapping("/quiz/chat")
    public ResponseEntity<?> chatWithAiTutor(@RequestBody Map<String, Object> payload) {
        Long materialId = Long.valueOf(payload.get("materialId").toString());
        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history");

        try {
            String reply = studentService.chatWithAiTutor(materialId, history);
            return ResponseEntity.ok(reply);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 5. 获取测验记录
    @GetMapping("/quiz/record/{materialId}")
    public ResponseEntity<?> getQuizRecord(@PathVariable Long materialId) {
        QuizRecord record = studentService.getQuizRecord(materialId);
        return record == null ? ResponseEntity.ok(Collections.emptyMap()) : ResponseEntity.ok(record);
    }

    // 6. 获取课程考试列表 (Service 层已处理状态动态计算)
    @GetMapping("/course/{courseId}/exams")
    public ResponseEntity<List<Exam>> getCourseExams(@PathVariable Long courseId) {
        return ResponseEntity.ok(studentService.getCourseExams(courseId));
    }

    // 7. 提交考试
    @PostMapping("/exam/submit")
    public ResponseEntity<?> submitExam(@RequestBody Map<String, Object> submission) {
        try {
            Long examId = Long.valueOf(submission.get("examId").toString());
            Integer score = (Integer) submission.getOrDefault("score", 0);
            String userAnswers = (String) submission.get("userAnswers");
            Integer cheatCount = (Integer) submission.getOrDefault("cheatCount", 0);

            studentService.submitExam(examId, score, userAnswers, cheatCount);
            return ResponseEntity.ok("考试提交成功！切屏次数: " + cheatCount);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("考试提交失败：" + e.getMessage());
        }
    }

    // 8. 获取考试记录详情
    @GetMapping("/exam/record/{examId}")
    public ResponseEntity<?> getExamRecord(@PathVariable Long examId) {
        Object result = studentService.getExamRecord(examId);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    // 9. 获取最近活动 (通知)
    @GetMapping("/recent-activities")
    public ResponseEntity<List<Map<String, Object>>> getRecentActivities() {
        return ResponseEntity.ok(studentService.getRecentActivities());
    }
    // 【新增】获取右上角通知列表
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getMyNotifications() {
        return ResponseEntity.ok(studentService.getMyNotifications());
    }

    // 【新增】获取首页待办任务
    @GetMapping("/pending-tasks")
    public ResponseEntity<List<Map<String, Object>>> getPendingTasks() {
        return ResponseEntity.ok(studentService.getPendingTasks());
    }
    // ...
    @PostMapping("/notification/reply")
    public ResponseEntity<?> replyNotification(@RequestBody Map<String, Object> data) {
        Long id = Long.valueOf(data.get("id").toString());
        String reply = (String) data.get("reply");

        notificationMapper.updateReply(id, reply);
        return ResponseEntity.ok("回复提交成功");
    }
    // ...
}