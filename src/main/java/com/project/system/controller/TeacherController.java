package com.project.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Application;
import com.project.system.entity.ExamRecord;
import com.project.system.entity.Course;
import com.project.system.entity.Material;
import com.project.system.entity.QuizRecord;
import com.project.system.entity.User;
import com.project.system.entity.Notification;

import com.project.system.mapper.ApplicationMapper;
import com.project.system.mapper.ExamMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.MaterialMapper;
import com.project.system.mapper.QuizRecordMapper;
import com.project.system.mapper.UserMapper;
import com.project.system.mapper.NotificationMapper;

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
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

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


    // 1. 获取该老师执教班级的学生列表 (支持分页和筛选)
    @GetMapping("/students")
    public ResponseEntity<?> listStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        String classesStr = teacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        List<String> validClassIds = Arrays.stream(classesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

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

    // 2. 提交申请 (核心功能：增/删/改学生信息, 延长截止时间)
    @PostMapping("/apply")
    public ResponseEntity<?> submitApplication(@RequestBody Application app) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        app.setTeacherId(teacher.getUserId());
        app.setTeacherName(teacher.getRealName());

        if (app.getType() == null || app.getReason() == null) {
            return ResponseEntity.badRequest().body("申请信息不完整，请填写类型和理由。");
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
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User teacher = userMapper.findByUsername(currentUsername);

        List<String> validClassIds = Arrays.stream(teacher.getTeachingClasses() != null ? teacher.getTeachingClasses().split(",") : new String[0])
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (validClassIds.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Material> allMaterials = new ArrayList<>();
        List<Long> classIds = validClassIds.stream().map(Long::parseLong).collect(Collectors.toList());

        for (Long classId : classIds) {
            List<Course> courses = courseMapper.selectAllCourses().stream()
                    .filter(c -> c.getClassId() != null && c.getClassId().equals(classId))
                    .collect(Collectors.toList());

            for (Course course : courses) {
                List<Material> materials = materialMapper.selectByCourseId(course.getId());
                materials.forEach(m -> m.setFileName(course.getName() + " - " + m.getFileName()));
                allMaterials.addAll(materials);
            }
        }

        return ResponseEntity.ok(allMaterials.stream()
                .filter(m -> Arrays.asList("测验", "作业", "项目").contains(m.getType()))
                .collect(Collectors.toList()));
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

        QuizRecord originalRecord = quizRecordMapper.findById(record.getId());
        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.updateScoreAndFeedback(record);
        if (rows > 0) {
            // 【实际通知】: 插入通知到数据库
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("GRADE_SUCCESS");

            String[] fileNameParts = material.getFileName().split(" - ");
            String materialName = fileNameParts.length > 1 ? fileNameParts[1] : material.getFileName();

            notification.setTitle(material.getType() + "已批改：[" + materialName + "]");
            notification.setMessage("您的提交已获得 " + record.getScore() + " 分！请前往查看评语。");
            notificationMapper.insert(notification);

            return ResponseEntity.ok("批改成功，分数已更新。");
        } else {
            return ResponseEntity.status(404).body("找不到对应的提交记录或更新失败。");
        }
    }

    // 7. 教师打回学生提交的作业/测验 (允许学生重新提交)
    @PostMapping("/reject-submission/{recordId}")
    @Transactional
    public ResponseEntity<?> rejectSubmission(@PathVariable Long recordId) {
        if (recordId == null) {
            return ResponseEntity.badRequest().body("记录ID不能为空");
        }

        QuizRecord originalRecord = quizRecordMapper.findById(recordId);
        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.deleteById(recordId);

        if (rows > 0) {
            // 【实际通知】: 插入通知到数据库
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("REJECT_SUBMISSION");

            String[] fileNameParts = material.getFileName().split(" - ");
            String materialName = fileNameParts.length > 1 ? fileNameParts[1] : material.getFileName();

            notification.setTitle(material.getType() + "被打回：[" + materialName + "]");
            notification.setMessage("您的提交已被教师打回重做，请尽快修改后重新提交。");
            notificationMapper.insert(notification);

            return ResponseEntity.ok("作业提交记录已打回，学生可以重新提交。");
        } else {
            return ResponseEntity.status(404).body("找不到对应的提交记录或打回失败。");
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
}