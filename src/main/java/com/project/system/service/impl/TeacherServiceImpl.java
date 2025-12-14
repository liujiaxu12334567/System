package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import com.project.system.mq.AnalysisEventPublisher;
import com.project.system.service.TeacherService;
import com.project.system.service.support.ClassroomMemoryStore;
import com.project.system.websocket.ClassroomEvent;
import com.project.system.websocket.ClassroomEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
    @Autowired private AnalysisEventPublisher analysisEventPublisher;

    // 【新增】注入 Redis 模板用于处理签到数据
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private CourseClassMapper courseClassMapper;
    @Autowired private AttendanceSummaryMapper attendanceSummaryMapper;
    @Autowired private AssignmentSummaryMapper assignmentSummaryMapper;
    @Autowired private TeacherInteractionMapper teacherInteractionMapper;
    @Autowired private OnlineQuestionMapper onlineQuestionMapper;
    @Autowired private OnlineAnswerMapper onlineAnswerMapper;
    @Autowired private ClassroomEventPublisher classroomEventPublisher;
    @Autowired private CourseChatMapper courseChatMapper;
    @Autowired private com.project.system.mapper.AttendanceRecordMapper attendanceRecordMapper;
    @Autowired private ClassroomMemoryStore classroomMemoryStore;
    @Autowired private AnalysisResultMapper analysisResultMapper;
    @Autowired private ObjectMapper objectMapper;

    // 辅助方法：获取当前登录教师
    private User getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    // 辅助方法：从课程表推导老师负责的班级ID集合（支持 responsibleClassIds/classId）
    private List<String> getValidClassIds(User teacher) {
        String teacherName = teacher.getRealName() == null ? "" : teacher.getRealName().replaceAll("\\s+", "");
        List<Course> courses = courseMapper.selectAllCourses();
        Set<String> ids = new HashSet<>();
        for (Course c : courses) {
            String tName = c.getTeacher() == null ? "" : c.getTeacher().replaceAll("\\s+", "");
            if (!tName.contains(teacherName)) continue;
            if (c.getResponsibleClassIds() != null && !c.getResponsibleClassIds().trim().isEmpty()) {
                for (String s : c.getResponsibleClassIds().split(",")) {
                    s = s.trim();
                    if (!s.isEmpty()) ids.add(s);
                }
            } else if (c.getClassId() != null) {
                ids.add(String.valueOf(c.getClassId()));
            }
        }
        return new ArrayList<>(ids);
    }

    private Map<Long, List<Long>> getCourseClassMap(List<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return Collections.emptyMap();
        List<CourseClass> relations = courseClassMapper.selectByCourseIds(courseIds);
        Map<Long, List<Long>> map = new HashMap<>();
        for (CourseClass cc : relations) {
            map.computeIfAbsent(cc.getCourseId(), k -> new ArrayList<>()).add(cc.getClassId());
        }
        return map;
    }

    private double roundToOne(double value) {
        return Math.round(value * 10.0) / 10.0;
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
        List<Course> myCourses = getMyCourses();
        if (myCourses.isEmpty()) return Collections.emptyList();

        Map<Long, Course> courseMap = myCourses.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<Material> allMaterials = new ArrayList<>();
        for (Long courseId : courseMap.keySet()) {
            List<Material> materials = materialMapper.selectByCourseId(courseId);
            Course course = courseMap.get(courseId);
            materials.forEach(m -> m.setFileName(course.getName() + " - " + m.getFileName()));
            allMaterials.addAll(materials);
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

        // 2. 获取当前教师的课程列表，确保只看自己课程
        List<Course> myCourses = getMyCourses();
        Map<Long, Course> myCourseMap = myCourses.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        Course course = myCourseMap.get(material.getCourseId());
        if (course == null) return new ArrayList<>();

        // 3. 计算该课程对应的班级列表（course_class > responsible_class_ids > class_id）
        List<Long> classList = new ArrayList<>(getCourseClassMap(Collections.singletonList(course.getId()))
                .getOrDefault(course.getId(), Collections.emptyList()));
        if (classList.isEmpty() && course.getResponsibleClassIds() != null && !course.getResponsibleClassIds().trim().isEmpty()) {
            for (String s : course.getResponsibleClassIds().split(",")) {
                s = s.trim();
                if (s.isEmpty()) continue;
                try { classList.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
            }
        } else if (classList.isEmpty() && course.getClassId() != null) {
            classList.add(course.getClassId());
        }
        if (classList.isEmpty()) return new ArrayList<>();

        // 4. 只查询这些班级的学生
        List<User> students = userMapper.selectStudentsByClassIds(classList);
        Map<Long, User> studentMap = students.stream().collect(Collectors.toMap(User::getUserId, u -> u));

        // 4. 获取提交记录
        List<QuizRecord> records = quizRecordMapper.findByMaterialId(materialId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizRecord r : records) {
            // 只有该班级的学生提交才显示
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

    // 【新增实现】获取仪表盘统计数据
    @Override
    public Map<String, Object> getDashboardStats() {
        User teacher = getCurrentTeacher();
        List<Course> myCourses = getMyCourses();
        if (myCourses.isEmpty()) {
            return Map.of(
                    "studentCount", 0,
                    "attendanceRate", 0d,
                    "interactionIndex", 0d,
                    "submissionRate", 0d,
                    "pieChart", Collections.emptyList(),
                    "lineChart", Collections.emptyList(),
                    "radarChart", Collections.emptyList(),
                    "courses", Collections.emptyList()
            );
        }

        List<Long> courseIds = myCourses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<Long>> courseClassMap = getCourseClassMap(courseIds);

        Set<Long> classIds = new HashSet<>();
        for (Course c : myCourses) {
            List<Long> viaRelation = courseClassMap.getOrDefault(c.getId(), Collections.emptyList());
            if (!viaRelation.isEmpty()) {
                classIds.addAll(viaRelation);
            } else if (c.getResponsibleClassIds() != null && !c.getResponsibleClassIds().isEmpty()) {
                for (String s : c.getResponsibleClassIds().split(",")) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    try { classIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                }
            } else if (c.getClassId() != null) {
                classIds.add(c.getClassId());
            }
        }

        long studentCount = classIds.isEmpty() ? 0 : userMapper.countStudentsByClassIds(new ArrayList<>(classIds));

        // 出勤统计
        List<AttendanceSummary> attendanceList = courseIds.isEmpty()
                ? Collections.emptyList()
                : attendanceSummaryMapper.selectByCourseIds(courseIds);
        long presentSum = 0;
        long expectedSum = 0;
        Map<LocalDate, long[]> attendanceByDate = new TreeMap<>();
        for (AttendanceSummary a : attendanceList) {
            long p = a.getPresent() == null ? 0 : a.getPresent();
            long e = a.getExpected() == null ? 0 : a.getExpected();
            presentSum += p;
            expectedSum += e;
            if (a.getDate() != null) {
                LocalDate d = a.getDate().toLocalDate();
                long[] pair = attendanceByDate.computeIfAbsent(d, k -> new long[2]);
                pair[0] += p;
                pair[1] += e;
            }
        }
        double attendanceRate = expectedSum > 0 ? roundToOne((double) presentSum * 100 / expectedSum) : 0d;

        List<LocalDate> dateKeys = new ArrayList<>(attendanceByDate.keySet());
        List<Double> lineChart = dateKeys.stream()
                .map(d -> {
                    long[] v = attendanceByDate.getOrDefault(d, new long[]{0, 0});
                    return v[1] > 0 ? roundToOne((double) v[0] * 100 / v[1]) : 0d;
                })
                .collect(Collectors.toList());

        List<String> lineChartLabels = dateKeys.stream().map(LocalDate::toString).collect(Collectors.toList());
        if (lineChart.size() > 7) {
            lineChart = lineChart.subList(lineChart.size() - 7, lineChart.size());
            lineChartLabels = lineChartLabels.subList(lineChartLabels.size() - 7, lineChartLabels.size());
        }

        // 作业提交统计
        List<AssignmentSummary> assignmentList = courseIds.isEmpty()
                ? Collections.emptyList()
                : assignmentSummaryMapper.selectByCourseIds(courseIds);
        long submittedSum = 0;
        long totalSum = 0;
        for (AssignmentSummary a : assignmentList) {
            submittedSum += a.getSubmitted() == null ? 0 : a.getSubmitted();
            totalSum += a.getTotal() == null ? 0 : a.getTotal();
        }
        double submissionRate = totalSum > 0 ? roundToOne((double) submittedSum * 100 / totalSum) : 0d;

        // 课堂互动统计
        List<TeacherInteraction> interactionList = courseIds.isEmpty()
                ? Collections.emptyList()
                : teacherInteractionMapper.selectByCourseIds(courseIds);
        List<OnlineAnswer> qaAnswers = courseIds.isEmpty() ? Collections.emptyList()
                : enrichAnswers(onlineAnswerMapper.selectByCourseIds(courseIds));
        long qaQuestionCount = courseIds.isEmpty() ? 0 : onlineQuestionMapper.countByCourseIds(courseIds);
        long teacherTalk = 0;
        long studentTalk = 0;
        long groupTalk = 0;
        long interactionCount = 0;
        for (TeacherInteraction i : interactionList) {
            teacherTalk += i.getTalkTimeSeconds() == null ? 0 : i.getTalkTimeSeconds();
            studentTalk += i.getStudentTalkSeconds() == null ? 0 : i.getStudentTalkSeconds();
            groupTalk += i.getGroupTalkSeconds() == null ? 0 : i.getGroupTalkSeconds();
            interactionCount += i.getInteractionCount() == null ? 0 : i.getInteractionCount();
        }
        // 折算在线问答时长/互动次数
        long qaTalk = 0;
        for (OnlineAnswer a : qaAnswers) {
            String type = a.getType() == null ? "answer" : a.getType();
            if ("hand".equals(type) || "race".equals(type)) {
                qaTalk += 20;
            } else {
                qaTalk += 45;
            }
            interactionCount += 1;
        }
        long interactionSamples = interactionList.size() + qaQuestionCount;
        double interactionIndex = interactionSamples == 0 ? 0d :
                roundToOne(Math.min(10d, interactionCount / (double) interactionSamples));

        long totalTalk = teacherTalk + studentTalk + groupTalk + qaTalk;
        List<Map<String, Object>> pieChart = Arrays.asList(
                Map.of("name", "教师讲授", "value", teacherTalk),
                Map.of("name", "学生互动", "value", studentTalk),
                Map.of("name", "小组讨论", "value", groupTalk),
                Map.of("name", "在线问答", "value", qaTalk > 0 ? qaTalk : Math.max(0, totalTalk > 0 ? 0 : 1))
        );

        double lineAvg = lineChart.isEmpty() ? attendanceRate : lineChart.stream().mapToDouble(Double::doubleValue).average().orElse(attendanceRate);
        double studentShare = totalTalk > 0 ? roundToOne(studentTalk * 100.0 / totalTalk) : 0d;
        double groupShare = totalTalk > 0 ? roundToOne(groupTalk * 100.0 / totalTalk) : 0d;
        List<Double> radarChart = Arrays.asList(
                attendanceRate,
                submissionRate,
                Math.min(100d, roundToOne(interactionIndex * 10)),
                lineAvg,
                Math.min(100d, roundToOne(studentShare + groupShare))
        );

        Map<String, Object> stats = new HashMap<>();
        stats.put("studentCount", studentCount);
        stats.put("attendanceRate", attendanceRate);
        stats.put("interactionIndex", interactionIndex);
        stats.put("submissionRate", submissionRate);
        stats.put("pieChart", pieChart);
        stats.put("lineChart", lineChart);
        stats.put("lineChartLabels", lineChartLabels);
        stats.put("radarChart", radarChart);
        stats.put("courses", myCourses);
        return stats;
    }

    // 【新增实现】获取教师名下的考试
    @Override
    public List<Exam> getTeacherExams() {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = getValidClassIds(teacher);

        if (validClassIds.isEmpty()) return Collections.emptyList();

        List<Long> classIdLongs = validClassIds.stream().map(Long::parseLong).collect(Collectors.toList());

        // 1. 找到这些班级的所有课程
        List<Course> courses = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getClassId() != null && classIdLongs.contains(c.getClassId()))
                .collect(Collectors.toList());

        List<Exam> exams = new ArrayList<>();
        // 2. 找到这些课程的所有考试
        for (Course c : courses) {
            List<Exam> courseExams = examMapper.selectExamsByCourseId(c.getId());
            // 拼上课程名，方便前端展示
            courseExams.forEach(e -> e.setTitle(c.getName() + " - " + e.getTitle()));
            exams.addAll(courseExams);
        }
        return exams;
    }

    @Override
    @Transactional
    public void gradeSubmission(QuizRecord record) {
        User teacher = getCurrentTeacher();

        QuizRecord originalRecord = quizRecordMapper.findById(record.getId());
        if (originalRecord == null) throw new RuntimeException("记录不存在");

        Material material = materialMapper.findById(originalRecord.getMaterialId());

        int rows = quizRecordMapper.updateScoreAndFeedback(record);
        if (rows > 0) {
            Notification notification = new Notification();
            notification.setUserId(originalRecord.getUserId());
            notification.setRelatedId(material.getId());
            notification.setType("GRADE_SUCCESS");

            String fileName = material.getFileName();
            if (fileName.contains(" - ")) {
                fileName = fileName.split(" - ")[1];
            }

            notification.setTitle(material.getType() + "已批改：[" + fileName + "]");
            notification.setMessage("您的提交已获得 " + record.getScore() + " 分！请前往查看评语。");
            notification.setSenderName(teacher.getRealName());
            notificationMapper.insert(notification);
        }
    }

    @Override
    @Transactional
    public void rejectSubmission(Long recordId) {
        User teacher = getCurrentTeacher();

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
            notification.setSenderName(teacher.getRealName());
            notificationMapper.insert(notification);
        }
    }

    @Override
    public void sendNotification(String title, String content) {
        User teacher = getCurrentTeacher();

        Map<String, Object> msg = new HashMap<>();
        msg.put("title", title);
        msg.put("content", content);
        msg.put("teachingClasses", teacher.getTeachingClasses());

        rabbitTemplate.convertAndSend("notification.queue", msg);
    }

    @Override
    public List<Map<String, Object>> getExamCheatingRecords(Long examId) {
        Exam exam = examMapper.findExamById(examId);
        if (exam == null) return Collections.emptyList();

        Course course = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getId().equals(exam.getCourseId()))
                .findFirst().orElse(null);

        if (course == null || course.getClassId() == null) return Collections.emptyList();

        List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(course.getClassId()));
        Map<Long, User> studentMap = students.stream().collect(Collectors.toMap(User::getUserId, s -> s));

        List<ExamRecord> allRecords = examMapper.selectExamRecordsByExamId(examId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ExamRecord r : allRecords) {
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

    @Override
    public List<Notification> getMyNotifications() {
        return notificationMapper.selectByUserId(getCurrentTeacher().getUserId());
    }

    @Override
    public void replyNotification(Long notificationId, String content) {
        notificationMapper.updateReply(notificationId, content);
    }

    // ==========================================
    // 【新增模块】课堂签到功能实现
    // ==========================================

    @Override
    public String startCheckIn(Long courseId) {
        String batchId = UUID.randomUUID().toString();
        // 1. 设置课程正在签到状态 (key: course:checkin:active:{courseId}, value: batchId)
        // 有效期 2 小时，防止老师忘关
        redisTemplate.opsForValue().set("course:checkin:active:" + courseId, batchId, 2, TimeUnit.HOURS);

        // 2. 初始化/清空该批次的签到人数集合
        redisTemplate.delete("course:checkin:list:" + batchId);

        return batchId;
    }

    @Override
    public void stopCheckIn(Long courseId) {
        String activeKey = "course:checkin:active:" + courseId;
        String batchId = redisTemplate.opsForValue().get(activeKey);

        if (batchId != null) {
            Long checkedCount = redisTemplate.opsForSet().size("course:checkin:list:" + batchId);

            Course course = courseMapper.selectAllCourses().stream()
                    .filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
            long totalStudents = 0;
            Long classId = null;
            if (course != null && course.getClassId() != null) {
                classId = course.getClassId();
                totalStudents = userMapper.countStudentsByTeachingClasses(null, null, Collections.singletonList(String.valueOf(classId)));
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("courseId", courseId);
            payload.put("batchId", batchId);
            payload.put("checkedCount", checkedCount == null ? 0 : checkedCount);
            payload.put("totalCount", totalStudents);
            analysisEventPublisher.publish("analysis.attendance.closed", payload);

            // 写出勤明细
            if (classId != null) {
                List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(classId));
                Set<String> presentIds = redisTemplate.opsForSet().members("course:checkin:list:" + batchId);
                Set<Long> present = new HashSet<>();
                if (presentIds != null) {
                    for (String s : presentIds) {
                        try { present.add(Long.parseLong(s)); } catch (Exception ignored) {}
                    }
                }
                List<com.project.system.entity.AttendanceRecord> records = new ArrayList<>();
                java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                for (User s : students) {
                    com.project.system.entity.AttendanceRecord r = new com.project.system.entity.AttendanceRecord();
                    r.setClassId(classId);
                    r.setCourseId(courseId);
                    r.setStudentId(s.getUserId());
                    r.setDate(today);
                    r.setPresent(present.contains(s.getUserId()));
                    r.setBatchId(batchId);
                    records.add(r);
                }
                if (!records.isEmpty()) {
                    attendanceRecordMapper.batchInsert(records);
                }
            }
        }

        // 删除活动状态 key 即可，学生无法再获取到 batchId 进行签到
        redisTemplate.delete(activeKey);
    }

    @Override
    public Map<String, Object> getCheckInStatus(Long courseId) {
        Map<String, Object> result = new HashMap<>();
        String batchId = redisTemplate.opsForValue().get("course:checkin:active:" + courseId);

        if (batchId == null) {
            result.put("isActive", false);
            return result;
        }

        // 获取已签到人数 (Set 大小)
        Long count = redisTemplate.opsForSet().size("course:checkin:list:" + batchId);

        // 获取课程总应到人数
        Course course = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
        long totalStudents = 0;
        if (course != null && course.getClassId() != null) {
            // 统计该班级的学生总数
            totalStudents = userMapper.countStudentsByTeachingClasses(null, null, Collections.singletonList(String.valueOf(course.getClassId())));
        }

        result.put("isActive", true);
        result.put("batchId", batchId);
        result.put("checkedCount", count != null ? count : 0);
        result.put("totalCount", totalStudents);

        // 计算实时出勤率
        double rate = totalStudents > 0 ? (double) (count != null ? count : 0) / totalStudents * 100 : 0;
        result.put("rate", String.format("%.1f", rate));

        return result;
    }

    // 【新增】获取当前教师的课程列表（含多班级与人数统计）
    @Override
    public List<Course> getMyCourses() {
        User teacher = getCurrentTeacher();
        String teacherName = teacher.getRealName() == null ? "" : teacher.getRealName().replaceAll("\\s+", "");
        List<Course> all = courseMapper.selectAllCourses();
        List<Course> mine = all.stream()
                .filter(c -> c.getTeacher() != null && c.getTeacher().replaceAll("\\s+", "").contains(teacherName))
                .collect(Collectors.toList());

        List<Long> mineIds = mine.stream().map(Course::getId).filter(Objects::nonNull).collect(Collectors.toList());
        Map<Long, List<Long>> courseClassMap = getCourseClassMap(mineIds);

        mine.forEach(c -> {
            List<Long> classList = new ArrayList<>(courseClassMap.getOrDefault(c.getId(), Collections.emptyList()));
            if (classList.isEmpty() && c.getResponsibleClassIds() != null && !c.getResponsibleClassIds().trim().isEmpty()) {
                for (String s : c.getResponsibleClassIds().split(",")) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    try { classList.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                }
            } else if (classList.isEmpty() && c.getClassId() != null) {
                classList.add(c.getClassId());
            }
            if (!classList.isEmpty()) {
                c.setStudentCount(userMapper.countStudentsByClassIds(classList));
                c.setClassId(classList.get(0));
                c.setResponsibleClassIds(classList.stream().map(String::valueOf).collect(Collectors.joining(",")));
            } else {
                c.setStudentCount(0L);
            }
        });

        return mine;
    }

    @Override
    @Transactional
    public OnlineQuestion createOnlineQuestion(OnlineQuestion question) {
        User teacher = getCurrentTeacher();
        List<Course> myCourses = getMyCourses();
        Set<Long> myCourseIds = myCourses.stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(question.getCourseId())) {
            throw new RuntimeException("无权发布非本人课程的问题");
        }
        // 绑定班级（course_class > responsible_class_ids > class_id）
        List<Long> classIds = new ArrayList<>(getCourseClassMap(Collections.singletonList(question.getCourseId()))
                .getOrDefault(question.getCourseId(), Collections.emptyList()));
        if (classIds.isEmpty()) {
            Course course = myCourses.stream().filter(c -> c.getId().equals(question.getCourseId())).findFirst().orElse(null);
            if (course != null && course.getResponsibleClassIds() != null) {
                for (String s : course.getResponsibleClassIds().split(",")) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    try { classIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                }
            } else if (course != null && course.getClassId() != null) {
                classIds.add(course.getClassId());
            }
        }
        question.setTeacherId(teacher.getUserId());
        question.setClassId(classIds.isEmpty() ? null : classIds.get(0));
        // 将模式/描述编码进 content JSON，兼容旧表结构
        Map<String, Object> payload = new HashMap<>();
        payload.put("mode", question.getMode() == null ? "broadcast" : question.getMode());
        payload.put("description", question.getContent());
        try {
            question.setContent(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
            // fallback
        }
        onlineQuestionMapper.insert(question);
        classroomEventPublisher.publish(new ClassroomEvent("question", question.getCourseId(), question.getId(), enrichQuestion(question)));
        return question;
    }

    @Override
    public List<OnlineQuestion> listOnlineQuestions(Long courseId) {
        List<Course> myCourses = getMyCourses();
        Set<Long> myCourseIds = myCourses.stream().map(Course::getId).collect(Collectors.toSet());
        List<Long> targetIds = courseId != null ? Collections.singletonList(courseId) : new ArrayList<>(myCourseIds);
        if (targetIds.isEmpty()) return Collections.emptyList();
        targetIds = targetIds.stream().filter(myCourseIds::contains).collect(Collectors.toList());
        if (targetIds.isEmpty()) return Collections.emptyList();
        LocalDateTime sessionStart = getSessionStart(courseId, false);
        return onlineQuestionMapper.selectByCourseIds(targetIds).stream()
                .filter(q -> sessionStart == null || (q.getCreateTime() != null && q.getCreateTime().isAfter(sessionStart)))
                .map(this::enrichQuestion)
                .collect(Collectors.toList());
    }

    @Override
    public List<OnlineAnswer> listOnlineAnswers(Long questionId) {
        OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
        if (q == null) return Collections.emptyList();
        // 权限校验
        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(q.getCourseId())) return Collections.emptyList();
        LocalDateTime sessionStart = getSessionStart(q.getCourseId(), false);
        return enrichAnswers(onlineAnswerMapper.selectByQuestionId(questionId)).stream()
                .filter(a -> sessionStart == null || (a.getCreateTime() != null && a.getCreateTime().isAfter(sessionStart)))
                .collect(Collectors.toList());
    }

    @Override
    public List<OnlineAnswer> listQueue(Long questionId) {
        return listOnlineAnswers(questionId).stream()
                .filter(a -> "hand".equals(a.getType()) || "race".equals(a.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public void callAnswer(Long answerId) {
        OnlineAnswer answer = onlineAnswerMapper.selectById(answerId);
        if (answer == null) return;
        OnlineAnswer enriched = enrichAnswer(answer);
        Map<String, Object> json = buildAnswerJson(
                enriched.getType() == null ? "answer" : enriched.getType(),
                enriched.getText(),
                "called",
                enriched.getStudentId());
        try {
            String text = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(json);
            onlineAnswerMapper.updateAnswerTextById(answerId, text);
        } catch (Exception ignored) {}
        classroomEventPublisher.publish(new ClassroomEvent("call", answer.getQuestionId(), answer.getQuestionId(), Map.of("answerId", answerId)));
    }

    @Override
    public List<CourseChat> listCourseChat(Long courseId, int limit) {
        return classroomMemoryStore.listChats(courseId, limit <= 0 ? 200 : limit);
    }

    @Override
    public CourseChat sendCourseChat(Long courseId, String content) {
        User teacher = getCurrentTeacher();
        CourseChat chat = new CourseChat();
        chat.setCourseId(courseId);
        chat.setSenderId(teacher.getUserId());
        chat.setSenderName(teacher.getRealName());
        chat.setRole("teacher");
        chat.setContent(content);
        CourseChat stored = classroomMemoryStore.appendChat(chat);
        classroomEventPublisher.publish(new ClassroomEvent("chat", courseId, null, stored));
        return stored;
    }

    @Override
    @Transactional
    public void resetClassroom(Long courseId) {
        if (courseId == null) return;
        // 0) 在线测试表现汇总（先保存，再清空）
        try {
            List<Map<String, Object>> perf = getClassroomPerformance(courseId);
            if (perf != null && !perf.isEmpty()) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("courseId", courseId);
                payload.put("generatedAt", java.time.LocalDateTime.now());
                payload.put("students", perf);
                AnalysisResult ar = new AnalysisResult();
                ar.setCourseId(courseId);
                ar.setMetric("classroom_online_performance");
                ar.setEventId("classroom_perf_" + courseId + "_" + System.currentTimeMillis());
                ar.setValueJson(objectMapper.writeValueAsString(payload));
                analysisResultMapper.insert(ar);
            }
        } catch (Exception ignored) {}

        // 1) 先删除当前课程的在线回答，再删问题
        List<Long> qids = onlineQuestionMapper.selectIdsByCourseId(courseId);
        if (qids != null && !qids.isEmpty()) {
            onlineAnswerMapper.deleteByQuestionIds(qids);
        }
        onlineQuestionMapper.deleteByCourseId(courseId);
        // 2) 删除课程聊天记录（内存态）
        classroomMemoryStore.clearCourse(courseId);
        // 2.5) 标记新的课堂会话开始时间
        setSessionStart(courseId, LocalDateTime.now());
        // 3) 通知 WebSocket 客户端刷新
        classroomEventPublisher.publish(new ClassroomEvent("reset", courseId, null, Map.of("courseId", courseId)));
    }

    @Override
    public List<Map<String, Object>> getClassroomPerformance(Long courseId) {
        if (courseId == null) return Collections.emptyList();
        LocalDateTime sessionStart = getSessionStart(courseId, false);
        List<OnlineAnswer> answers = onlineAnswerMapper.selectByCourseIds(Collections.singletonList(courseId));
        if (answers == null || answers.isEmpty()) return Collections.emptyList();

        Map<Long, PerfCounter> perfMap = new HashMap<>();
        for (OnlineAnswer a : answers) {
            OnlineAnswer enriched = enrichAnswer(a);
            if (sessionStart != null && a.getCreateTime() != null && !a.getCreateTime().isAfter(sessionStart)) {
                continue;
            }
            PerfCounter c = perfMap.computeIfAbsent(a.getStudentId(), k -> new PerfCounter());
            c.total++;
            String type = enriched.getType() == null ? "answer" : enriched.getType();
            switch (type) {
                case "hand": c.hand++; break;
                case "race": c.race++; break;
                default: c.answer++; break;
            }
            if (a.getCreateTime() != null && (c.lastTime == null || a.getCreateTime().isAfter(c.lastTime))) {
                c.lastTime = a.getCreateTime();
            }
        }

        Map<Long, User> studentMap = userMapper.selectUsersByRole("4").stream()
                .collect(Collectors.toMap(User::getUserId, u -> u, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        perfMap.forEach((sid, c) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", sid);
            User stu = studentMap.get(sid);
            m.put("studentName", stu != null ? stu.getRealName() : ("学生" + sid));
            m.put("handCount", c.hand);
            m.put("raceCount", c.race);
            m.put("answerCount", c.answer);
            m.put("total", c.total);
            m.put("lastAnswerTime", c.lastTime);
            result.add(m);
        });

        result.sort(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("total")).reversed());
        return result;
    }

    private OnlineQuestion enrichQuestion(OnlineQuestion q) {
        if (q == null) return null;
        q.setMode("broadcast");
        q.setDescription(q.getContent());
        if (q.getContent() != null && q.getContent().trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(q.getContent());
                if (node.has("mode")) q.setMode(node.get("mode").asText());
                if (node.has("description")) q.setDescription(node.get("description").asText());
            } catch (Exception ignored) {}
        }
        return q;
    }

    private OnlineAnswer enrichAnswer(OnlineAnswer a) {
        if (a == null) return null;
        a.setType("answer");
        a.setState("answered");
        a.setText(a.getAnswerText());
        if (a.getAnswerText() != null && a.getAnswerText().trim().startsWith("{")) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(a.getAnswerText());
                if (node.has("type")) a.setType(node.get("type").asText());
                if (node.has("state")) a.setState(node.get("state").asText());
                if (node.has("text")) a.setText(node.get("text").asText());
            } catch (Exception ignored) {}
        }
        return a;
    }

    private LocalDateTime getSessionStart(Long courseId, boolean createIfAbsent) {
        if (courseId == null) return null;
        String key = "classroom:session:start:" + courseId;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try { return LocalDateTime.parse(val); } catch (Exception ignored) {}
        }
        if (createIfAbsent) {
            LocalDateTime now = LocalDateTime.now();
            redisTemplate.opsForValue().set(key, now.toString());
            return now;
        }
        return null;
    }

    private void setSessionStart(Long courseId, LocalDateTime time) {
        if (courseId == null || time == null) return;
        redisTemplate.opsForValue().set("classroom:session:start:" + courseId, time.toString());
    }

    private static class PerfCounter {
        int hand = 0;
        int race = 0;
        int answer = 0;
        int total = 0;
        java.time.LocalDateTime lastTime;
    }

    private List<OnlineAnswer> enrichAnswers(List<OnlineAnswer> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(this::enrichAnswer).collect(Collectors.toList());
    }

    private Map<String, Object> buildAnswerJson(String type, String text, String state, Long studentId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("text", text);
        map.put("state", state);
        map.put("studentId", studentId);
        return map;
    }
}
