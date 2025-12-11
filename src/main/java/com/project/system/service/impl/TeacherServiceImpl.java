package com.project.system.service.impl;

import com.project.system.dto.PaginationResponse;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import com.project.system.service.TeacherService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    @Autowired private RabbitTemplate rabbitTemplate;

    // 辅助方法：获取当前登录教师
    private User getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    // 辅助方法：解析教师的执教班级字符串为 List<String>
    private List<String> getValidClassIds(User teacher) {
        String classesStr = teacher.getTeachingClasses();
        if (classesStr == null || classesStr.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(classesStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public PaginationResponse<?> listStudents(String keyword, String classId, int pageNum, int pageSize) {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = getValidClassIds(teacher);

        if (validClassIds.isEmpty()) {
            return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
        }

        // 安全检查：如果前端传了 classId，必须确保它在老师的执教范围内
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
    }

    @Override
    public List<Object> listTeachingMaterials() {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = getValidClassIds(teacher);

        if (validClassIds.isEmpty()) return Collections.emptyList();

        List<Material> allMaterials = new ArrayList<>();
        List<Long> classIds = validClassIds.stream().map(Long::parseLong).collect(Collectors.toList());

        // 优化建议：这里存在 N+1 查询问题，但暂且保持逻辑正确性
        for (Long classId : classIds) {
            // 获取该班级下的所有课程
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
        // 1. 获取资料信息
        Material material = materialMapper.findById(materialId);
        if (material == null) return new ArrayList<>();

        // 2. 获取该资料所属的课程 -> 班级
        // 假设 CourseMapper 有 selectById 方法，如果没有，这里先遍历查找
        List<Course> allCourses = courseMapper.selectAllCourses();
        Course course = allCourses.stream().filter(c -> c.getId().equals(material.getCourseId())).findFirst().orElse(null);

        if (course == null || course.getClassId() == null) return new ArrayList<>();

        // 3. 【优化】只查询该班级的学生，而不是全校学生
        List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(course.getClassId()));
        Map<Long, User> studentMap = students.stream().collect(Collectors.toMap(User::getUserId, u -> u));

        // 4. 获取提交记录
        List<QuizRecord> records = quizRecordMapper.findByMaterialId(materialId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizRecord r : records) {
            // 只有该班级的学生提交才显示（双重保险）
            User s = studentMap.get(r.getUserId());
            if (s != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("record", r);
                map.put("studentName", s.getRealName());
                map.put("studentUsername", s.getUsername());
                map.put("classId", s.getClassId());
                result.add(map);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void gradeSubmission(QuizRecord record) {
        User teacher = getCurrentTeacher(); // 【修改点】获取当前教师信息

        QuizRecord originalRecord = quizRecordMapper.findById(record.getId());
        if (originalRecord == null) throw new RuntimeException("记录不存在");

        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.updateScoreAndFeedback(record);
        if (rows > 0) {
            // 单条通知直接入库，保持即时性
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("GRADE_SUCCESS");

            String fileName = material.getFileName();
            // 处理文件名显示
            if (fileName.contains(" - ")) {
                fileName = fileName.split(" - ")[1];
            }

            notification.setTitle(material.getType() + "已批改：[" + fileName + "]");
            notification.setMessage("您的提交已获得 " + record.getScore() + " 分！请前往查看评语。");
            notification.setSenderName(teacher.getRealName()); // 【修改点】设置发送者姓名
            notificationMapper.insert(notification);
        }
    }

    @Override
    @Transactional
    public void rejectSubmission(Long recordId) {
        User teacher = getCurrentTeacher(); // 【修改点】获取当前教师信息

        QuizRecord originalRecord = quizRecordMapper.findById(recordId);
        if (originalRecord == null) throw new RuntimeException("记录不存在");

        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.deleteById(recordId);
        if (rows > 0) {
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("REJECT_SUBMISSION");

            String fileName = material.getFileName();
            if (fileName.contains(" - ")) {
                fileName = fileName.split(" - ")[1];
            }

            notification.setTitle(material.getType() + "被打回：[" + fileName + "]");
            notification.setMessage("您的提交已被教师打回重做，请尽快修改后重新提交。");
            notification.setSenderName(teacher.getRealName()); // 【修改点】设置发送者姓名
            notificationMapper.insert(notification);
        }
    }

    // ★★★ RabbitMQ 生产者：异步发送群发通知 ★★★
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
    }

    @Override
    public List<Map<String, Object>> getExamCheatingRecords(Long examId) {
        // 1. 获取考试信息以确定班级
        Exam exam = examMapper.findExamById(examId);
        if (exam == null) return Collections.emptyList();

        Course course = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getId().equals(exam.getCourseId()))
                .findFirst().orElse(null);

        if (course == null || course.getClassId() == null) return Collections.emptyList();

        // 2. 【优化】只查询该班级的学生
        List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(course.getClassId()));
        Map<Long, User> studentMap = students.stream().collect(Collectors.toMap(User::getUserId, s -> s));

        List<ExamRecord> allRecords = examMapper.selectExamRecordsByExamId(examId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ExamRecord r : allRecords) {
            // 筛选作弊且属于该班级的记录
            if (r.getCheatCount() != null && r.getCheatCount() > 0) {
                User s = studentMap.get(r.getUserId());
                if (s != null) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("record", r);
                    item.put("studentName", s.getRealName());
                    item.put("studentUsername", s.getUsername());
                    item.put("classId", s.getClassId());
                    result.add(item);
                }
            }
        }
        return result;
    }
    // 【新增】获取教师的通知
    @Override
    public List<Notification> getMyNotifications() {
        return notificationMapper.selectByUserId(getCurrentTeacher().getUserId());
    }

    // 【新增】教师回复通知
    @Override
    public void replyNotification(Long notificationId, String content) {
        // 简单校验一下是否是该用户的通知（可选）
        // notificationMapper.updateReply(notificationId, content);
        // 这里假设 Mapper 已有 updateReply 方法 (上一步已添加)
        notificationMapper.updateReply(notificationId, content);
    }
}