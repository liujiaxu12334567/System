package com.project.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.dto.PaginationResponse;
import com.project.system.dto.StudentPortraitResponse;
import com.project.system.entity.*;

import com.project.system.mapper.ApplicationMapper;
import com.project.system.mapper.ExamMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.MaterialMapper;
import com.project.system.mapper.QuizRecordMapper;
import com.project.system.mapper.UserMapper;
import com.project.system.mapper.NotificationMapper; // 引入 NotificationMapper

import com.project.system.service.TeacherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Objects;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/teacher")
public class TeacherController {
    @Autowired
    private TeacherService teacherService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ApplicationMapper applicationMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private NotificationMapper notificationMapper; // 【新增注入】

    // 根据课程表推导教师负责的班级（优先 responsibleClassIds，其次 classId）
    private Set<Long> getTeacherClassIds(String teacherName) {
        List<Course> courses = courseMapper.selectAllCourses();
        Set<Long> classIds = new HashSet<>();
        if (teacherName == null) return classIds;
        String normalized = teacherName.replaceAll("\\s+", "");
        for (Course c : courses) {
            String tName = c.getTeacher() == null ? "" : c.getTeacher().replaceAll("\\s+", "");
            if (!tName.contains(normalized)) continue;
            if (c.getResponsibleClassIds() != null && !c.getResponsibleClassIds().trim().isEmpty()) {
                for (String s : c.getResponsibleClassIds().split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        try { classIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (c.getClassId() != null) {
                classIds.add(c.getClassId());
            }
        }
        return classIds;
    }

    @GetMapping("/classroom/performance")
    public ResponseEntity<?> getClassroomPerformance(@RequestParam Long courseId) {
        try {
            return ResponseEntity.ok(teacherService.getClassroomPerformance(courseId));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("获取统计失败: " + e.getMessage());
        }
    }

    // 1. 获取该老师执教班级的学生列表 (支持分页和筛选)
    @GetMapping("/students")
    public ResponseEntity<?> listStudents(
            @RequestParam(required = false) String keyword, // 模糊查询关键字
            @RequestParam(required = false) String classId, // 班级ID筛选
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        Set<Long> classIdSet = getTeacherClassIds(teacher.getRealName());
        if (classIdSet.isEmpty()) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        List<String> validClassIds = classIdSet.stream().map(String::valueOf).collect(Collectors.toList());

        if (validClassIds.isEmpty()) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        int offset = (pageNum - 1) * pageSize;

        String finalClassId = null;
        if (classId != null && !classId.isEmpty()) {
            if (!validClassIds.contains(classId)) {
                return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
            }
            finalClassId = classId;
        }

        long total = userMapper.countStudentsByTeachingClasses(keyword, finalClassId, validClassIds);

        if (total == 0) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        List<User> list = userMapper.selectStudentsByTeachingClasses(keyword, finalClassId, validClassIds, offset, pageSize);

        return ResponseEntity.ok(new PaginationResponse<>(list, total, pageNum, pageSize));
    }

    // 【新增】学生AI综合画像（按班级/课程聚合出勤/提交/互动）
    @GetMapping("/student-portraits")
    public ResponseEntity<?> getStudentPortraits(
            @RequestParam("classId") Long classId,
            @RequestParam("studentIds") String studentIds) {
        if (classId == null) return ResponseEntity.badRequest().body("classId 不能为空");
        if (studentIds == null || studentIds.trim().isEmpty()) return ResponseEntity.ok(Collections.emptyList());

        List<Long> ids = Arrays.stream(studentIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Long.parseLong(s); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        List<StudentPortraitResponse> res = teacherService.getStudentPortraits(classId, ids);
        return ResponseEntity.ok(res);
    }

    // 2. 提交申请 (核心功能：增/删/改学生信息, 延长截止时间)
    @PostMapping("/apply")
    public ResponseEntity<?> submitApplication(@RequestBody Application app) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        app.setTeacherId(teacher.getUserId());
        app.setTeacherName(teacher.getRealName());

        if (app.getType() == null || app.getReason() == null || app.getReason().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("申请信息不完整，请填写类型和理由。");
        }
        if ("DEADLINE_EXTENSION".equals(app.getType())) {
            if (app.getTargetId() == null) {
                return ResponseEntity.badRequest().body("延期申请缺少资料ID（targetId）");
            }
            if (app.getContent() == null || !app.getContent().contains("延长至:")) {
                return ResponseEntity.badRequest().body("延期申请内容缺少“延长至:”字段");
            }
        }

        applicationMapper.insert(app);

        System.out.println("【通知】新的教师申请已提交，等待审核。类型: " + app.getType() + ", 申请人: " + teacher.getRealName());

        return ResponseEntity.ok("学生管理/资料修改申请已提交，请等待组长/管理员审核。");
    }

    // 3. 查看我的申请记录
    @GetMapping("/my-applications")
    public ResponseEntity<?> myApplications() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        List<Application> list = applicationMapper.findByTeacherId(teacher.getUserId());
        return ResponseEntity.ok(list);
    }

    // 4. 获取该老师执教班级的所有作业/测验资料
    @GetMapping("/materials")
    public ResponseEntity<?> listTeachingMaterials() {
        return ResponseEntity.ok(teacherService.listTeachingMaterials());
    }

    // 5. 获取某个资料的所有提交记录 (仅限该老师班级的学生)
    @GetMapping("/material/{materialId}/submissions")
    public ResponseEntity<?> getSubmissionsForMaterial(@PathVariable Long materialId) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        List<String> validClassIds = Arrays.stream(teacher.getTeachingClasses() != null ? teacher.getTeachingClasses().split(",") : new String[0])
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        List<User> studentsInClass = userMapper.selectAllUsers(null, "4", null, 0, Integer.MAX_VALUE).stream()
                .filter(s -> s.getClassId() != null && validClassIds.contains(String.valueOf(s.getClassId())))
                .collect(Collectors.toList());

        Set<Long> studentIds = studentsInClass.stream().map(User::getUserId).collect(Collectors.toSet());
        Map<Long, User> studentMap = studentsInClass.stream().collect(Collectors.toMap(User::getUserId, s -> s));

        List<QuizRecord> allRecords = quizRecordMapper.findByMaterialId(materialId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizRecord record : allRecords) {
            if (studentIds.contains(record.getUserId())) {
                Map<String, Object> item = new HashMap<>();
                item.put("record", record);
                User student = studentMap.get(record.getUserId());
                item.put("studentName", student.getRealName());
                item.put("studentUsername", student.getUsername());
                item.put("classId", student.getClassId());
                result.add(item);
            }
        }

        return ResponseEntity.ok(result);
    }

    // 6. 批改并更新作业/项目分数
    @PostMapping("/grade")
    public ResponseEntity<?> gradeSubmission(@RequestBody QuizRecord record) {
        if (record.getId() == null || record.getScore() == null) {
            return ResponseEntity.badRequest().body("记录ID和分数不能为空");
        }
        try {
            // 【修改：委托给 Service 层处理，Service 会设置正确的发送者姓名】
            teacherService.gradeSubmission(record);
            return ResponseEntity.ok("批改成功，分数已更新。");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("批改失败: " + e.getMessage());
        }
    }

    // 7. 教师打回学生提交的作业/测验 (允许学生重新提交)
    @PostMapping("/reject-submission/{recordId}")
    public ResponseEntity<?> rejectSubmission(@PathVariable Long recordId) {
        if (recordId == null) {
            return ResponseEntity.badRequest().body("记录ID不能为空");
        }
        try {
            // 【修改：委托给 Service 层处理，Service 会设置正确的发送者姓名】
            teacherService.rejectSubmission(recordId);
            return ResponseEntity.ok("作业提交记录已打回，学生可以重新提交。");
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("打回失败: " + e.getMessage());
        }
    }

    // 8. 教师下发通知
    @PostMapping("/notification/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> data) {
        String title = (String) data.get("title");
        String content = (String) data.get("content");

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body("通知标题和内容不能为空");
        }

        // 查找该教师执教的所有学生
        List<String> validClassIds = Arrays.stream(teacher.getTeachingClasses() != null ? teacher.getTeachingClasses().split(",") : new String[0])
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        List<User> studentsInClass = userMapper.selectAllUsers(null, "4", null, 0, Integer.MAX_VALUE).stream()
                .filter(s -> s.getClassId() != null && validClassIds.contains(String.valueOf(s.getClassId())))
                .collect(Collectors.toList());

        int count = 0;
        for(User student : studentsInClass) {
            Notification notification = new Notification();
            notification.setUserId(student.getUserId());
            notification.setType("GENERAL_NOTICE");
            notification.setTitle(title);
            notification.setMessage(content);
            notificationMapper.insert(notification);
            count++;
        }

        return ResponseEntity.ok("消息通知已成功下发给 " + count + " 位学生。");
    }

    // 9. 查看考试作弊记录
    @GetMapping("/exam/{examId}/cheating-records")
    public ResponseEntity<?> getExamCheatingRecords(@PathVariable Long examId) {
        List<ExamRecord> allRecords = examMapper.selectExamRecordsByExamId(examId);

        List<Map<String, Object>> cheatingList = new ArrayList<>();
        List<User> allStudents = userMapper.selectUsersByRole("4");
        Map<Long, User> studentMap = allStudents.stream().collect(Collectors.toMap(User::getUserId, s -> s));

        for (ExamRecord record : allRecords) {
            if (record.getCheatCount() != null && record.getCheatCount() > 0) {
                User student = studentMap.get(record.getUserId());

                if (student != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("record", record);
                    item.put("studentName", student.getRealName());
                    item.put("studentUsername", student.getUsername());
                    item.put("classId", student.getClassId());
                    cheatingList.add(item);
                }
            }
        }

        return ResponseEntity.ok(cheatingList);
    }
    // 【新增】获取通知列表
    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getMyNotifications() {
        return ResponseEntity.ok(teacherService.getMyNotifications());
    }
    // 【新增接口】获取仪表盘数据
    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboardStats() {
        try {
            return ResponseEntity.ok(teacherService.getDashboardStats());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("获取数据失败: " + e.getMessage());
        }
    }

    // 【新增接口】获取教师课程列表（带多班级与人数）
    @GetMapping("/courses")
    public ResponseEntity<?> listMyCoursesV2() {
        try {
            return ResponseEntity.ok(teacherService.getMyCourses());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("获取课程失败: " + e.getMessage());
        }
    }

    // 【新增接口】获取考试列表
    @GetMapping("/exams")
    public ResponseEntity<List<Exam>> getExams() {
        return ResponseEntity.ok(teacherService.getTeacherExams());
    }

    // ================= 在线课堂 =================
    @PostMapping("/classroom/{courseId}/start")
    public ResponseEntity<?> startClassroom(@PathVariable Long courseId) {
        try {
            return ResponseEntity.ok(teacherService.startClassroom(courseId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("开课失败: " + e.getMessage());
        }
    }

    @PostMapping("/classroom/{courseId}/end")
    public ResponseEntity<?> endClassroom(@PathVariable Long courseId) {
        try {
            return ResponseEntity.ok(teacherService.endClassroom(courseId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("下课失败: " + e.getMessage());
        }
    }

    @GetMapping("/classroom/{courseId}/status")
    public ResponseEntity<?> classroomStatus(@PathVariable Long courseId) {
        return ResponseEntity.ok(teacherService.getClassroomStatus(courseId));
    }

    @PostMapping("/classroom/question")
    public ResponseEntity<?> createClassroomQuestion(@RequestBody Map<String, Object> payload) {
        try {
            OnlineQuestion q = new OnlineQuestion();
            q.setCourseId(Long.valueOf(payload.get("courseId").toString()));
            q.setTitle((String) payload.getOrDefault("title", ""));
            q.setContent((String) payload.getOrDefault("content", ""));
            q.setMode((String) payload.getOrDefault("mode", "broadcast"));
            if (payload.get("assignStudentId") != null && !payload.get("assignStudentId").toString().isEmpty()) {
                q.setAssignStudentId(Long.valueOf(payload.get("assignStudentId").toString()));
            }
            if (payload.get("deadline") != null && !payload.get("deadline").toString().isEmpty()) {
                q.setDeadline(java.time.LocalDateTime.parse(payload.get("deadline").toString().replace(" ", "T")));
            }
            return ResponseEntity.ok(teacherService.createOnlineQuestion(q));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("发布失败: " + e.getMessage());
        }
    }

    @GetMapping("/classroom/questions")
    public ResponseEntity<List<OnlineQuestion>> classroomQuestions(@RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(teacherService.listOnlineQuestions(courseId));
    }

    @GetMapping("/classroom/question/{questionId}/queue")
    public ResponseEntity<List<OnlineAnswer>> classroomQueue(@PathVariable Long questionId) {
        return ResponseEntity.ok(teacherService.listQueue(questionId));
    }

    @PostMapping("/classroom/answer/{answerId}/call")
    public ResponseEntity<?> callAnswer(@PathVariable Long answerId) {
        teacherService.callAnswer(answerId);
        return ResponseEntity.ok("已点名/允许发言");
    }

    // 【新增】发布在线问题
    @PostMapping("/online-question")
    public ResponseEntity<?> createOnlineQuestion(@RequestBody Map<String, Object> payload) {
        try {
            OnlineQuestion q = new OnlineQuestion();
            q.setCourseId(Long.valueOf(payload.get("courseId").toString()));
            q.setTitle((String) payload.getOrDefault("title", ""));
            q.setContent((String) payload.getOrDefault("content", ""));
            q.setMode((String) payload.getOrDefault("mode", "broadcast"));
            if (payload.get("assignStudentId") != null && !payload.get("assignStudentId").toString().isEmpty()) {
                q.setAssignStudentId(Long.valueOf(payload.get("assignStudentId").toString()));
            }
            if (payload.get("deadline") != null && !payload.get("deadline").toString().isEmpty()) {
                q.setDeadline(java.time.LocalDateTime.parse(payload.get("deadline").toString().replace(" ", "T")));
            }
            return ResponseEntity.ok(teacherService.createOnlineQuestion(q));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("发布问题失败: " + e.getMessage());
        }
    }

    // 【新增】老师查看自己课程的在线问题列表
    @GetMapping("/online-questions")
    public ResponseEntity<List<OnlineQuestion>> listOnlineQuestions(@RequestParam(required = false) Long courseId) {
        return ResponseEntity.ok(teacherService.listOnlineQuestions(courseId));
    }

    // 【新增】查看问题的全部回答
    @GetMapping("/online-question/{questionId}/answers")
    public ResponseEntity<List<OnlineAnswer>> listOnlineAnswers(@PathVariable Long questionId) {
        return ResponseEntity.ok(teacherService.listOnlineAnswers(questionId));
    }

    @GetMapping("/classroom/{courseId}/chat")
    public ResponseEntity<List<CourseChat>> listCourseChat(@PathVariable Long courseId, @RequestParam(defaultValue = "200") int limit) {
        return ResponseEntity.ok(teacherService.listCourseChat(courseId, limit));
    }

    @PostMapping("/classroom/{courseId}/chat")
    public ResponseEntity<?> sendCourseChat(@PathVariable Long courseId, @RequestBody Map<String, String> payload) {
        String content = payload.get("content");
        if (content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("内容不能为空");
        }
        return ResponseEntity.ok(teacherService.sendCourseChat(courseId, content.trim()));
    }

    // 【新增】重置课堂，清空聊天与在线提问/回答
    @PostMapping("/classroom/{courseId}/reset")
    public ResponseEntity<?> resetClassroom(@PathVariable Long courseId) {
        teacherService.resetClassroom(courseId);
        return ResponseEntity.ok("课堂数据已重置");
    }

    // 【新增】在线测试表现
    @GetMapping("/classroom/{courseId}/performance")
    public ResponseEntity<?> classroomPerformance(@PathVariable Long courseId) {
        return ResponseEntity.ok(teacherService.getClassroomPerformance(courseId));
    }
    // 【新增】回复通知
    @PostMapping("/notification/reply")
    public ResponseEntity<?> replyNotification(@RequestBody Map<String, Object> data) {
        Long id = Long.valueOf(data.get("id").toString());
        String reply = (String) data.get("reply");
        teacherService.replyNotification(id, reply);
        return ResponseEntity.ok("回复提交成功");
    }
    @PostMapping("/checkin/start")
    public ResponseEntity<?> startCheckIn(@RequestBody Map<String, Long> payload) {
        String batchId = teacherService.startCheckIn(payload.get("courseId"));
        return ResponseEntity.ok(Map.of("message", "签到已开启", "batchId", batchId));
    }

    @PostMapping("/checkin/stop")
    public ResponseEntity<?> stopCheckIn(@RequestBody Map<String, Long> payload) {
        teacherService.stopCheckIn(payload.get("courseId"));
        return ResponseEntity.ok("签到已结束");
    }

    @GetMapping("/checkin/status/{courseId}")
    public ResponseEntity<?> getCheckInStatus(@PathVariable Long courseId) {
        return ResponseEntity.ok(teacherService.getCheckInStatus(courseId));
    }
}
