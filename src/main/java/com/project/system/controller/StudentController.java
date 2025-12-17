package com.project.system.controller;

import com.project.system.entity.*;
import com.project.system.mapper.NotificationMapper;
import com.project.system.mapper.OnlineQuestionMapper;
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
    @Autowired
    private OnlineQuestionMapper onlineQuestionMapper;
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

    // 个性学习：获取所有课程组长下发课程（学生端可跨班级浏览）
    @GetMapping("/personal-learning/courses")
    public ResponseEntity<List<Course>> getPersonalLearningCourses() {
        return ResponseEntity.ok(studentService.getPersonalLearningCourses());
    }

    @GetMapping("/course-schedule")
    public ResponseEntity<?> getCourseSchedule() {
        try {
            return ResponseEntity.ok(studentService.getStudentCourseSchedule());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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

    // 6.1 学生端：我的考试列表（包含已答/未答；limit 可选，用于首页展示）
    @GetMapping("/my-exams")
    public ResponseEntity<List<Map<String, Object>>> getMyExams(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(studentService.getMyExams(limit));
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
    @PostMapping("/notification/reply")
    public ResponseEntity<?> replyNotification(@RequestBody Map<String, Object> data) {
        Long id = Long.valueOf(data.get("id").toString());
        String reply = (String) data.get("reply");

        notificationMapper.updateReply(id, reply);
        return ResponseEntity.ok("回复提交成功");
    }

    @PostMapping("/checkin")
    public ResponseEntity<?> checkIn(@RequestBody Map<String, Long> payload) {
        boolean success = studentService.doCheckIn(payload.get("courseId"));
        if (success) return ResponseEntity.ok("签到成功！");
        return ResponseEntity.badRequest().body("签到失败：老师未开启或已结束");
    }

    @GetMapping("/checkin/status/{courseId}")
    public ResponseEntity<?> getCheckInStatus(@PathVariable Long courseId) {
        return ResponseEntity.ok(studentService.getCheckInStatus(courseId));
    }

    @GetMapping("/classroom/status/{courseId}")
    public ResponseEntity<?> getClassroomStatus(@PathVariable Long courseId) {
        return ResponseEntity.ok(studentService.getClassroomStatus(courseId));
    }
    @PostMapping("/notification/read/{id}")
    public ResponseEntity<?> markNotificationAsRead(@PathVariable Long id) {
        studentService.markNotificationAsRead(id);
        return ResponseEntity.ok("标记已读成功");
    }

    // 【新增】在线问答列表（按课程）
    @GetMapping("/online-questions")
    public ResponseEntity<List<OnlineQuestion>> listOnlineQuestions(@RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(studentService.listOnlineQuestions(courseId));
    }

    // 【新增】提交在线问题回答
    @PostMapping("/online-question/{questionId}/answer")
    public ResponseEntity<?> answerOnlineQuestion(@PathVariable Long questionId, @RequestBody Map<String, String> payload) {
        try {
            String text = payload.get("answerText");
            OnlineAnswer answer = studentService.answerOnlineQuestion(questionId, text);
            OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
            return ResponseEntity.ok(Map.of(
                    "answer", answer,
                    "correct", answer == null ? null : answer.getCorrect(),
                    "correctAnswer", q == null ? null : q.getCorrectAnswer()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 【新增】举手
    @PostMapping("/classroom/question/{questionId}/hand")
    public ResponseEntity<?> handRaise(@PathVariable Long questionId) {
        try {
            return ResponseEntity.ok(studentService.handRaise(questionId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 【新增】抢答
    @PostMapping("/classroom/question/{questionId}/race")
    public ResponseEntity<?> race(@PathVariable Long questionId) {
        try {
            return ResponseEntity.ok(studentService.raceAnswer(questionId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 【新增】查看回答/发言流
    @GetMapping("/classroom/question/{questionId}/answers")
    public ResponseEntity<List<OnlineAnswer>> listAnswers(@PathVariable Long questionId) {
        return ResponseEntity.ok(studentService.listAnswers(questionId));
    }

    // 课堂聊天
    @GetMapping("/classroom/{courseId}/chat")
    public ResponseEntity<List<CourseChat>> listCourseChat(@PathVariable Long courseId, @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(studentService.listCourseChat(courseId, limit));
    }

    @PostMapping("/classroom/{courseId}/chat")
    public ResponseEntity<?> sendCourseChat(@PathVariable Long courseId, @RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("内容不能为空");
        }
        return ResponseEntity.ok(studentService.sendCourseChat(courseId, content.trim()));
    }
}
