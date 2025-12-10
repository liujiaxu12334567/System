package com.project.system.service.impl;

import com.project.system.dto.PaginationResponse;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import com.project.system.service.TeacherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate; // RabbitMQ
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TeacherServiceImpl implements TeacherService {

    @Autowired private UserMapper userMapper;
    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private QuizRecordMapper quizRecordMapper;
    @Autowired private ExamMapper examMapper;
    @Autowired private NotificationMapper notificationMapper;

    @Autowired private RabbitTemplate rabbitTemplate; // 注入 RabbitTemplate

    // 辅助方法：获取当前登录教师
    private User getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    @Override
    public PaginationResponse<?> listStudents(String keyword, String classId, int pageNum, int pageSize) {
        User teacher = getCurrentTeacher();
        String classesStr = teacher.getTeachingClasses();

        if (classesStr == null || classesStr.isEmpty()) {
            return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
        }

        List<String> validClassIds = Arrays.stream(classesStr.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        if (validClassIds.isEmpty()) {
            return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
        }

        String finalClassId = null;
        if (classId != null && !classId.isEmpty()) {
            if (!validClassIds.contains(classId)) {
                return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
            }
            finalClassId = classId;
        }

        int offset = (pageNum - 1) * pageSize;
        long total = userMapper.countStudentsByTeachingClasses(keyword, finalClassId, validClassIds);

        if (total == 0) return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);

        List<User> list = userMapper.selectStudentsByTeachingClasses(keyword, finalClassId, validClassIds, offset, pageSize);
        return new PaginationResponse<>(list, total, pageNum, pageSize);
    }

    @Override
    @Transactional
    public void submitApplication(Application app) {
        User teacher = getCurrentTeacher();
        app.setTeacherId(teacher.getUserId());
        app.setTeacherName(teacher.getRealName());
        applicationMapper.insert(app);

        // 此处可以添加发给管理员的通知逻辑，暂时保留控制台输出
        System.out.println("【System】教师申请已提交: " + app.getType());
    }

    @Override
    public List<Object> listTeachingMaterials() {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = Arrays.stream(teacher.getTeachingClasses() != null ? teacher.getTeachingClasses().split(",") : new String[0])
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        if (validClassIds.isEmpty()) return Collections.emptyList();

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

        return allMaterials.stream()
                .filter(m -> Arrays.asList("测验", "作业", "项目").contains(m.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> getSubmissionsForMaterial(Long materialId) {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = Arrays.stream(teacher.getTeachingClasses() != null ? teacher.getTeachingClasses().split(",") : new String[0])
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

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
        return result;
    }

    @Override
    @Transactional
    public void gradeSubmission(QuizRecord record) {
        QuizRecord originalRecord = quizRecordMapper.findById(record.getId());
        if (originalRecord == null) throw new RuntimeException("记录不存在");

        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.updateScoreAndFeedback(record);
        if (rows > 0) {
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("GRADE_SUCCESS");

            String[] fileNameParts = material.getFileName().split(" - ");
            String materialName = fileNameParts.length > 1 ? fileNameParts[1] : material.getFileName();

            notification.setTitle(material.getType() + "已批改：[" + materialName + "]");
            notification.setMessage("您的提交已获得 " + record.getScore() + " 分！请前往查看评语。");
            notificationMapper.insert(notification);
        } else {
            throw new RuntimeException("更新数据库失败");
        }
    }

    @Override
    @Transactional
    public void rejectSubmission(Long recordId) {
        QuizRecord originalRecord = quizRecordMapper.findById(recordId);
        if (originalRecord == null) throw new RuntimeException("记录不存在");

        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.deleteById(recordId);
        if (rows > 0) {
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("REJECT_SUBMISSION");

            String[] fileNameParts = material.getFileName().split(" - ");
            String materialName = fileNameParts.length > 1 ? fileNameParts[1] : material.getFileName();

            notification.setTitle(material.getType() + "被打回：[" + materialName + "]");
            notification.setMessage("您的提交已被教师打回重做，请尽快修改后重新提交。");
            notificationMapper.insert(notification);
        } else {
            throw new RuntimeException("打回操作失败");
        }
    }

    // ★★★ RabbitMQ 生产者：异步发送通知 ★★★
    @Override
    public void sendNotification(String title, String content) {
        User teacher = getCurrentTeacher();

        Map<String, Object> msg = new HashMap<>();
        msg.put("title", title);
        msg.put("content", content);
        // 将教师的执教班级列表放入消息中，消费者会根据这个列表查找所有学生
        msg.put("teachingClasses", teacher.getTeachingClasses());

        // 发送消息到 RabbitMQ 队列 "notification.queue"
        rabbitTemplate.convertAndSend("notification.queue", msg);

        System.out.println("【MQ】通知任务已发送至消息队列，由消费者处理批量插入。");
    }

    @Override
    public List<Map<String, Object>> getExamCheatingRecords(Long examId) {
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
        return cheatingList;
    }
}