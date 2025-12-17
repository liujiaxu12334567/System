package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.dto.PaginationResponse;
import com.project.system.dto.AnalysisResultResponse;
import com.project.system.dto.StudentAttendanceAgg;
import com.project.system.dto.StudentCountAgg;
import com.project.system.dto.StudentPortraitResponse;
import com.project.system.entity.*;
import com.project.system.entity.Class;
import com.project.system.mapper.*;
import com.project.system.mq.AnalysisEventPublisher;
import com.project.system.service.TeacherService;
import com.project.system.service.support.ClassroomChatStore;
import com.project.system.websocket.ClassroomEvent;
import com.project.system.websocket.ClassroomEventBus;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class TeacherServiceImpl implements TeacherService {

    @Autowired private UserMapper userMapper;
    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private CourseScheduleMapper courseScheduleMapper;
    @Autowired private QuizRecordMapper quizRecordMapper;
    @Autowired private ExamMapper examMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AnalysisEventPublisher analysisEventPublisher;

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private CourseClassMapper courseClassMapper;
    @Autowired private AttendanceSummaryMapper attendanceSummaryMapper;
    @Autowired private AssignmentSummaryMapper assignmentSummaryMapper;
    @Autowired private TeacherInteractionMapper teacherInteractionMapper;
    @Autowired private OnlineQuestionMapper onlineQuestionMapper;
    @Autowired private OnlineAnswerMapper onlineAnswerMapper;
    @Autowired private ClassroomEventBus classroomEventBus;
    @Autowired private com.project.system.mapper.AttendanceRecordMapper attendanceRecordMapper;
    @Autowired private ClassroomChatStore classroomChatStore;
    @Autowired private AnalysisResultMapper analysisResultMapper;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ClassMapper classMapper;

    private static final DateTimeFormatter COURSE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static String normalizeTimeString(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        // 兼容旧数据：若包含日期，取最后的时间部分
        if (s.contains(" ")) s = s.substring(s.lastIndexOf(' ') + 1);
        if (s.contains("T")) s = s.substring(s.lastIndexOf('T') + 1);
        if (s.endsWith("Z")) s = s.substring(0, s.length() - 1);
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() == 5) s = s + ":00";
        if (s.length() > 8) s = s.substring(0, 8);
        return s;
    }

    private static LocalTime parseCourseTime(String raw) {
        String s = normalizeTimeString(raw);
        if (s == null) return null;
        return LocalTime.parse(s, COURSE_TIME_FORMATTER);
    }

    // 辅助方法：获取当前登录教师
    private User getCurrentTeacher() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    // 辅助方法：从课程表推导老师负责的班级ID集合（支持 responsibleClassIds/classId 以及 用户表的 teaching_classes）
    private List<String> getValidClassIds(User teacher) {
        Set<String> ids = new HashSet<>();

        // 1. 来源：课程表 (sys_course)
        String teacherName = teacher.getRealName() == null ? "" : teacher.getRealName().replaceAll("\\s+", "");
        List<Course> courses = courseMapper.selectAllCourses();
        for (Course c : courses) {
            String tName = c.getTeacher() == null ? "" : c.getTeacher().replaceAll("\\s+", "");
            if (tName.contains(teacherName)) {
                if (c.getResponsibleClassIds() != null && !c.getResponsibleClassIds().trim().isEmpty()) {
                    for (String s : c.getResponsibleClassIds().split(",")) {
                        s = s.trim();
                        if (!s.isEmpty()) ids.add(s);
                    }
                } else if (c.getClassId() != null) {
                    ids.add(String.valueOf(c.getClassId()));
                }
            }
        }

        // 2. 【新增修复】来源：用户表的 teaching_classes 字段 (sys_user)
        // 解决部分老师未绑定课程但需要管理班级的情况
        if (teacher.getTeachingClasses() != null && !teacher.getTeachingClasses().trim().isEmpty()) {
            String[] manualIds = teacher.getTeachingClasses().split("[,，]");
            for (String id : manualIds) {
                if (!id.trim().isEmpty()) ids.add(id.trim());
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

    private boolean courseContainsClass(Course course, Long classId, Map<Long, List<Long>> courseClassMap) {
        if (course == null || classId == null) return false;
        if (course.getId() != null) {
            List<Long> viaRelation = courseClassMap.getOrDefault(course.getId(), Collections.emptyList());
            if (!viaRelation.isEmpty() && viaRelation.contains(classId)) return true;
        }
        if (course.getResponsibleClassIds() != null && !course.getResponsibleClassIds().trim().isEmpty()) {
            for (String s : course.getResponsibleClassIds().split(",")) {
                s = s.trim();
                if (s.isEmpty()) continue;
                try {
                    if (Long.parseLong(s) == classId) return true;
                } catch (NumberFormatException ignored) {}
            }
        }
        return course.getClassId() != null && course.getClassId().equals(classId);
    }

    @Override
    public PaginationResponse<?> listStudents(String keyword, String classId, int pageNum, int pageSize) {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = getValidClassIds(teacher);

        if (validClassIds.isEmpty()) {
            return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
        }

        String finalClassId = null;
        if (classId != null && !classId.isEmpty()) {
            // 只要在管理范围内即可
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
    public List<StudentPortraitResponse> getStudentPortraits(Long classId, List<Long> studentIds) {
        User teacher = getCurrentTeacher();
        if (classId == null) throw new RuntimeException("classId 不能为空");

        List<String> validClassIds = getValidClassIds(teacher);
        if (!validClassIds.contains(String.valueOf(classId))) throw new RuntimeException("无权访问该班级");

        if (studentIds == null || studentIds.isEmpty()) return Collections.emptyList();

        List<User> classStudents = userMapper.selectStudentsByClassIds(Collections.singletonList(classId));
        Set<Long> classStudentIds = classStudents.stream()
                .map(User::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Long> targetStudentIds = studentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(classStudentIds::contains)
                .collect(Collectors.toList());
        if (targetStudentIds.isEmpty()) return Collections.emptyList();

        List<Course> myCourses = getMyCourses();
        List<Long> myCourseIds = myCourses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<Long>> courseClassMap = getCourseClassMap(myCourseIds);

        List<Long> courseIdsForClass = myCourses.stream()
                .filter(c -> courseContainsClass(c, classId, courseClassMap))
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long taskTotal = courseIdsForClass.isEmpty() ? 0 : materialMapper.countTasksByCourseIds(courseIdsForClass);

        Map<Long, StudentAttendanceAgg> attendanceAggMap = new HashMap<>();
        Map<Long, Long> submittedCountMap = new HashMap<>();
        Map<Long, Long> interactionCountMap = new HashMap<>();

        if (!courseIdsForClass.isEmpty()) {
            for (StudentAttendanceAgg agg : attendanceRecordMapper.countByClassAndCourseIdsAndStudentIds(classId, courseIdsForClass, targetStudentIds)) {
                if (agg != null && agg.getUserId() != null) attendanceAggMap.put(agg.getUserId(), agg);
            }

            for (StudentCountAgg agg : quizRecordMapper.countSubmittedTasksByCourseIdsAndStudentIds(courseIdsForClass, targetStudentIds)) {
                if (agg != null && agg.getUserId() != null) submittedCountMap.put(agg.getUserId(), agg.getCount() == null ? 0L : agg.getCount());
            }

            for (StudentCountAgg agg : onlineAnswerMapper.countAnswersByCourseIdsAndStudentIds(courseIdsForClass, targetStudentIds)) {
                if (agg != null && agg.getUserId() != null) interactionCountMap.put(agg.getUserId(), agg.getCount() == null ? 0L : agg.getCount());
            }
        }

        List<StudentPortraitResponse> result = new ArrayList<>();
        for (Long studentId : targetStudentIds) {
            StudentAttendanceAgg att = attendanceAggMap.get(studentId);
            long present = att == null || att.getPresentCount() == null ? 0 : att.getPresentCount();
            long total = att == null || att.getTotalCount() == null ? 0 : att.getTotalCount();
            double attendanceRate = total > 0 ? roundToOne(present * 100d / total) : 0d;

            long submitted = submittedCountMap.getOrDefault(studentId, 0L);
            double submissionRate = taskTotal > 0 ? roundToOne(submitted * 100d / taskTotal) : 0d;

            long interactionCount = interactionCountMap.getOrDefault(studentId, 0L);
            double interactionScore = Math.min(100d, roundToOne(interactionCount * 12.5d)); // 8次互动≈100分

            double portraitScore = roundToOne(attendanceRate * 0.4d + submissionRate * 0.4d + interactionScore * 0.2d);

            StudentPortraitResponse p = new StudentPortraitResponse();
            p.setUserId(studentId);
            p.setAttendanceRate(attendanceRate);
            p.setAttendancePresent(present);
            p.setAttendanceTotal(total);
            p.setSubmissionRate(submissionRate);
            p.setSubmittedCount(submitted);
            p.setTaskTotal(taskTotal);
            p.setInteractionScore(interactionScore);
            p.setInteractionCount(interactionCount);
            p.setPortraitScore(portraitScore);
            result.add(p);
        }
        return result;
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
        Material material = materialMapper.findById(materialId);
        if (material == null) return new ArrayList<>();

        List<Course> myCourses = getMyCourses();
        Map<Long, Course> myCourseMap = myCourses.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        Course course = myCourseMap.get(material.getCourseId());
        if (course == null) return new ArrayList<>();

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

        List<User> students = userMapper.selectStudentsByClassIds(classList);
        Map<Long, User> studentMap = students.stream().collect(Collectors.toMap(User::getUserId, u -> u));

        List<QuizRecord> records = quizRecordMapper.findByMaterialId(materialId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizRecord r : records) {
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
                    "courses", Collections.emptyList(),
                    "classPerformanceList", Collections.emptyList()
            );
        }

        List<Long> courseIds = myCourses.stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        Map<Long, List<Long>> courseClassMap = getCourseClassMap(courseIds);
        List<Class> allClasses = Collections.emptyList();
        try {
            allClasses = classMapper.selectAllClasses();
        } catch (Exception ignored) {}
        Map<Long, String> classNameMap = allClasses.stream().collect(Collectors.toMap(Class::getClassId, Class::getClassName));

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

        long studentCount = 0;
        try {
            studentCount = classIds.isEmpty() ? 0 : userMapper.countStudentsByClassIds(new ArrayList<>(classIds));
        } catch (Exception ignored) {}

        // 出勤统计
        List<AttendanceSummary> attendanceList = Collections.emptyList();
        try {
            attendanceList = courseIds.isEmpty()
                    ? Collections.emptyList()
                    : attendanceSummaryMapper.selectByCourseIds(courseIds);
        } catch (Exception ignored) {}
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
        List<AssignmentSummary> assignmentList = Collections.emptyList();
        try {
            assignmentList = courseIds.isEmpty()
                    ? Collections.emptyList()
                    : assignmentSummaryMapper.selectByCourseIds(courseIds);
        } catch (Exception ignored) {}
        long submittedSum = 0;
        long totalSum = 0;
        for (AssignmentSummary a : assignmentList) {
            submittedSum += a.getSubmitted() == null ? 0 : a.getSubmitted();
            totalSum += a.getTotal() == null ? 0 : a.getTotal();
        }
        double submissionRate = totalSum > 0 ? roundToOne((double) submittedSum * 100 / totalSum) : 0d;

        // 课堂互动统计
        List<TeacherInteraction> interactionList = Collections.emptyList();
        try {
            interactionList = courseIds.isEmpty()
                    ? Collections.emptyList()
                    : teacherInteractionMapper.selectByCourseIds(courseIds);
        } catch (Exception ignored) {}

        List<OnlineAnswer> qaAnswers = Collections.emptyList();
        try {
            qaAnswers = courseIds.isEmpty()
                    ? Collections.emptyList()
                    : enrichAnswers(onlineAnswerMapper.selectByCourseIds(courseIds));
        } catch (Exception ignored) {}

        long qaQuestionCount = 0;
        try {
            qaQuestionCount = courseIds.isEmpty() ? 0 : onlineQuestionMapper.countByCourseIds(courseIds);
        } catch (Exception ignored) {}
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

        // 构建每个课程/班级的历史表现概览
        List<Map<String, Object>> classPerformanceList = new ArrayList<>();
        for (Course c : myCourses) {
            Map<String, Object> perf = new HashMap<>();
            perf.put("courseId", c.getId());
            perf.put("courseName", c.getName());
            perf.put("className", c.getClassId() != null ? (c.getClassId() + "班") : "混合班级");
            perf.put("studentCount", c.getStudentCount());

            List<Long> qIds;
            try {
                if (c.getId() == null) {
                    qIds = Collections.emptyList();
                } else {
                    qIds = onlineQuestionMapper.selectByCourseIds(Collections.singletonList(c.getId())).stream()
                            .map(OnlineQuestion::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());
                }
            } catch (Exception ignored) {
                qIds = Collections.emptyList();
            }
            final List<Long> finalQIds = qIds;
            long courseInteractions = qaAnswers.stream().filter(a -> finalQIds.contains(a.getQuestionId())).count();

            int activeScore = 60;
            if (c.getStudentCount() > 0) {
                activeScore += (int) ((double)courseInteractions / c.getStudentCount() * 10);
            }
            perf.put("score", Math.min(100, activeScore));
            perf.put("activeCount", Math.min(c.getStudentCount(), courseInteractions / 2 + 5));

            classPerformanceList.add(perf);
        }

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
        stats.put("classPerformanceList", classPerformanceList);
        return stats;
    }

    @Override
    public List<Exam> getTeacherExams() {
        User teacher = getCurrentTeacher();
        List<String> validClassIds = getValidClassIds(teacher);

        if (validClassIds.isEmpty()) return Collections.emptyList();

        List<Long> classIdLongs = validClassIds.stream().map(Long::parseLong).collect(Collectors.toList());

        List<Course> courses = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getClassId() != null && classIdLongs.contains(c.getClassId()))
                .collect(Collectors.toList());

        List<Exam> exams = new ArrayList<>();
        for (Course c : courses) {
            List<Exam> courseExams = examMapper.selectExamsByCourseId(c.getId());
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
        return buildExamRecordList(examId, true);
    }

    @Override
    public List<Map<String, Object>> getExamMonitorRecords(Long examId) {
        return buildExamRecordList(examId, false);
    }

    private List<Map<String, Object>> buildExamRecordList(Long examId, boolean onlyCheating) {
        if (examId == null) return Collections.emptyList();

        Exam exam = examMapper.findExamById(examId);
        if (exam == null) return Collections.emptyList();

        Set<Long> myCourseIds = getMyCourses().stream()
                .map(Course::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!myCourseIds.contains(exam.getCourseId())) {
            throw new RuntimeException("无权限查看该考试");
        }

        List<ExamRecord> allRecords = examMapper.selectExamRecordsByExamId(examId);
        if (allRecords == null || allRecords.isEmpty()) return Collections.emptyList();

        List<User> allStudents = userMapper.selectUsersByRole("4");
        Map<Long, User> studentMap = allStudents.stream()
                .filter(u -> u.getUserId() != null)
                .collect(Collectors.toMap(User::getUserId, s -> s, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ExamRecord r : allRecords) {
            int cheatCount = r.getCheatCount() == null ? 0 : Math.max(0, r.getCheatCount());
            if (onlyCheating && cheatCount <= 0) continue;

            User student = studentMap.get(r.getUserId());
            if (student == null) continue;

            int penalty = cheatCount * 10;
            Integer rawScore = r.getScore();
            Integer finalScore = rawScore == null ? null : Math.max(0, rawScore - penalty);

            Map<String, Object> item = new HashMap<>();
            item.put("record", r);
            item.put("studentName", student.getRealName());
            item.put("studentUsername", student.getUsername());
            item.put("classId", student.getClassId());
            item.put("penalty", penalty);
            item.put("finalScore", finalScore);
            result.add(item);
        }

        // 默认按“最终成绩”排序（监控/作弊记录一致）
        result.sort((a, b) -> {
            Integer sa = (Integer) a.get("finalScore");
            Integer sb = (Integer) b.get("finalScore");
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            return Integer.compare(sb, sa);
        });
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

    @Override
    public String startCheckIn(Long courseId) {
        if (courseId == null) throw new RuntimeException("courseId不能为空");
        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(courseId)) throw new RuntimeException("无权操作该课程");

        // 到点前不允许开启签到：与在线课堂保持一致的时间门禁（按 sys_course_schedule）
        LocalDateTime now = LocalDateTime.now();
        int today = now.getDayOfWeek().getValue();
        String nowTimeStr = now.toLocalTime().format(COURSE_TIME_FORMATTER);
        int inWindow = courseScheduleMapper.countActiveByCourseIdAndTime(courseId, today, nowTimeStr);
        if (inWindow <= 0) {
            List<CourseScheduleSlot> todaySlots = courseScheduleMapper.selectByCourseIdAndDay(courseId, today);
            if (todaySlots == null || todaySlots.isEmpty()) {
                throw new RuntimeException("今天没有该课程的排课记录，未到上课时间无法开始上课/开启签到");
            }
            LocalTime nowTime = now.toLocalTime();
            CourseScheduleSlot next = null;
            for (CourseScheduleSlot slot : todaySlots) {
                if (slot == null) continue;
                LocalTime st = parseCourseTime(slot.getStartTime());
                if (st == null) continue;
                if (st.isAfter(nowTime)) {
                    next = slot;
                    break;
                }
            }
            if (next != null) {
                long minutes = java.time.Duration.between(now.toLocalTime(), parseCourseTime(next.getStartTime())).toMinutes();
                if (minutes >= 0 && minutes <= 15) {
                    throw new RuntimeException("快到上课时间了：距离第" + next.getPeriodIndex() + "节还有" + minutes + "分钟（" + normalizeTimeString(next.getStartTime()) + "开始）");
                }
                throw new RuntimeException("未到上课时间，下一节为第" + next.getPeriodIndex() + "节，开始时间：" + normalizeTimeString(next.getStartTime()));
            }
            throw new RuntimeException("当前不在上课时间范围内，无法开始上课/开启签到");
        }

        String batchId = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set("course:checkin:active:" + courseId, batchId, 2, TimeUnit.HOURS);
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
            List<Long> classIds = new ArrayList<>();
            if (course != null) {
                Map<Long, List<Long>> courseClassMap = getCourseClassMap(Collections.singletonList(courseId));
                classIds.addAll(courseClassMap.getOrDefault(courseId, Collections.emptyList()));
                if (classIds.isEmpty() && course.getResponsibleClassIds() != null && !course.getResponsibleClassIds().trim().isEmpty()) {
                    for (String s : course.getResponsibleClassIds().split("[,，]")) {
                        s = s.trim();
                        if (s.isEmpty()) continue;
                        try { classIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                    }
                } else if (classIds.isEmpty() && course.getClassId() != null) {
                    classIds.add(course.getClassId());
                }
            }
            classIds = classIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
            long totalStudents = classIds.isEmpty() ? 0 : userMapper.countStudentsByClassIds(classIds);

            Map<String, Object> payload = new HashMap<>();
            payload.put("courseId", courseId);
            payload.put("batchId", batchId);
            payload.put("checkedCount", checkedCount == null ? 0 : checkedCount);
            payload.put("totalCount", totalStudents);
            analysisEventPublisher.publish("analysis.attendance.closed", payload);

            if (!classIds.isEmpty()) {
                List<User> students = userMapper.selectStudentsByClassIds(classIds);

                Set<String> presentIds = redisTemplate.opsForSet().members("course:checkin:list:" + batchId);
                Set<Long> present = new HashSet<>();
                if (presentIds != null) {
                    for (String s : presentIds) {
                        try { present.add(Long.parseLong(s)); } catch (Exception ignored) {}
                    }
                }

                Map<Long, Integer> expectedByClass = new HashMap<>();
                Map<Long, Integer> presentByClass = new HashMap<>();

                List<com.project.system.entity.AttendanceRecord> records = new ArrayList<>();
                java.sql.Date today = java.sql.Date.valueOf(java.time.LocalDate.now());
                for (User s : students) {
                    if (s.getUserId() == null || s.getClassId() == null) continue;

                    Long classId = s.getClassId();
                    expectedByClass.merge(classId, 1, Integer::sum);

                    boolean isPresent = present.contains(s.getUserId());
                    if (isPresent) {
                        presentByClass.merge(classId, 1, Integer::sum);
                    }

                    com.project.system.entity.AttendanceRecord r = new com.project.system.entity.AttendanceRecord();
                    r.setClassId(classId);
                    r.setCourseId(courseId);
                    r.setStudentId(s.getUserId());
                    r.setDate(today);
                    r.setPresent(isPresent);
                    r.setBatchId(batchId);
                    records.add(r);
                }
                if (!records.isEmpty()) {
                    attendanceRecordMapper.batchInsert(records);
                }

                // 【新增修复】写入统计汇总表，确保仪表盘能立刻看到数据（多班级分别汇总）
                for (Map.Entry<Long, Integer> entry : expectedByClass.entrySet()) {
                    Long classId = entry.getKey();
                    Integer expected = entry.getValue();
                    Integer presentCount = presentByClass.getOrDefault(classId, 0);

                    AttendanceSummary summary = new AttendanceSummary();
                    summary.setCourseId(courseId);
                    summary.setClassId(classId);
                    summary.setDate(java.sql.Date.valueOf(LocalDate.now()));
                    summary.setExpected(expected == null ? 0 : expected);
                    summary.setPresent(presentCount == null ? 0 : presentCount);
                    attendanceSummaryMapper.insert(summary);
                }
            }
        }
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

        Long count = redisTemplate.opsForSet().size("course:checkin:list:" + batchId);

        Course course = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
        long totalStudents = 0;
        if (course != null) {
            List<Long> classIds = new ArrayList<>();
            Map<Long, List<Long>> courseClassMap = getCourseClassMap(Collections.singletonList(courseId));
            classIds.addAll(courseClassMap.getOrDefault(courseId, Collections.emptyList()));
            if (classIds.isEmpty() && course.getResponsibleClassIds() != null && !course.getResponsibleClassIds().trim().isEmpty()) {
                for (String s : course.getResponsibleClassIds().split("[,，]")) {
                    s = s.trim();
                    if (s.isEmpty()) continue;
                    try { classIds.add(Long.parseLong(s)); } catch (NumberFormatException ignored) {}
                }
            } else if (classIds.isEmpty() && course.getClassId() != null) {
                classIds.add(course.getClassId());
            }
            classIds = classIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
            totalStudents = classIds.isEmpty() ? 0 : userMapper.countStudentsByClassIds(classIds);
        }

        result.put("isActive", true);
        result.put("batchId", batchId);
        result.put("checkedCount", count != null ? count : 0);
        result.put("totalCount", totalStudents);

        double rate = totalStudents > 0 ? (double) (count != null ? count : 0) / totalStudents * 100 : 0;
        result.put("rate", String.format("%.1f", rate));

        return result;
    }

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
        if (question.getCorrectAnswer() != null && question.getCorrectAnswer().trim().isEmpty()) {
            question.setCorrectAnswer(null);
        }
        Map<String, Object> payload = new HashMap<>();
        String mode = question.getMode() == null ? "broadcast" : question.getMode();
        payload.put("mode", mode);
        payload.put("description", question.getContent());
        if ("assign".equals(mode)) {
            if (question.getAssignStudentId() == null) {
                throw new RuntimeException("点名模式需要指定学生");
            }
            payload.put("assignStudentId", question.getAssignStudentId());
        }
        try {
            question.setContent(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
        } catch (Exception e) {
        }
        onlineQuestionMapper.insert(question);

        OnlineQuestion saved = enrichQuestion(onlineQuestionMapper.selectById(question.getId()));
        OnlineQuestion pub = saved;
        if (pub != null) {
            pub.setCorrectAnswer(null);
        }
        classroomEventBus.publish(new ClassroomEvent("question", question.getCourseId(), question.getId(), pub));
        return saved;
    }

    @Override
    public List<OnlineQuestion> listOnlineQuestions(Long courseId) {
        List<Course> myCourses = getMyCourses();
        Set<Long> myCourseIds = myCourses.stream().map(Course::getId).collect(Collectors.toSet());
        List<Long> targetIds = courseId != null ? Collections.singletonList(courseId) : new ArrayList<>(myCourseIds);
        if (targetIds.isEmpty()) return Collections.emptyList();
        targetIds = targetIds.stream().filter(myCourseIds::contains).collect(Collectors.toList());
        if (targetIds.isEmpty()) return Collections.emptyList();
        LocalDateTime sessionStart = courseId == null ? null : getSessionStart(courseId, true);
        List<OnlineQuestion> questions;
        try {
            questions = onlineQuestionMapper.selectByCourseIds(targetIds);
        } catch (BadSqlGrammarException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("correct_answer")) {
                throw new IllegalStateException("数据库缺少 online_question.correct_answer 列：请执行 docs/online_question_correct_answer.sql 后重试。", e);
            }
            throw e;
        }
        return questions.stream()
                .filter(q -> sessionStart == null || (q.getCreateTime() != null && q.getCreateTime().isAfter(sessionStart)))
                .map(this::enrichQuestion)
                .collect(Collectors.toList());
    }

    @Override
    public List<OnlineAnswer> listOnlineAnswers(Long questionId) {
        OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
        if (q == null) return Collections.emptyList();
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
        classroomEventBus.publish(new ClassroomEvent("call", answer.getQuestionId(), answer.getQuestionId(), Map.of("answerId", answerId)));
    }

    @Override
    public List<CourseChat> listCourseChat(Long courseId, int limit) {
        LocalDateTime sessionStart = courseId == null ? null : getSessionStart(courseId, true);
        return classroomChatStore.list(courseId, limit <= 0 ? 200 : limit).stream()
                .filter(c -> sessionStart == null || c.getCreateTime() == null || c.getCreateTime().isAfter(sessionStart))
                .collect(Collectors.toList());
    }

    @Override
    public CourseChat sendCourseChat(Long courseId, String content) {
        User teacher = getCurrentTeacher();
        getSessionStart(courseId, true);
        Long chatId = redisTemplate.opsForValue().increment("classroom:chat:seq:" + courseId);
        CourseChat chat = new CourseChat();
        chat.setId(chatId);
        chat.setCourseId(courseId);
        chat.setSenderId(teacher.getUserId());
        chat.setSenderName(teacher.getRealName());
        chat.setRole("teacher");
        chat.setContent(content);
        chat.setCreateTime(LocalDateTime.now());
        classroomEventBus.publish(new ClassroomEvent("chat", courseId, null, chat));
        return chat;
    }

    @Override
    public Map<String, Object> startClassroom(Long courseId) {
        if (courseId == null) throw new RuntimeException("courseId 不能为空");
        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(courseId)) throw new RuntimeException("无权操作该课程");

        String activeKey = "classroom:active:" + courseId;
        LocalDateTime now = LocalDateTime.now();
        int today = now.getDayOfWeek().getValue();
        String nowTimeStr = now.toLocalTime().format(COURSE_TIME_FORMATTER);

        // 若 Redis 残留了旧 active 标记（例如上次未正常下课），则仅在“当前确实处于上课时间窗”才视为有效
        boolean alreadyActive = Boolean.TRUE.equals(redisTemplate.hasKey(activeKey));
        if (alreadyActive) {
            int inWindow = courseScheduleMapper.countActiveByCourseIdAndTime(courseId, today, nowTimeStr);
            if (inWindow > 0) {
                LocalDateTime existingStart = getSessionStart(courseId, false);
                if (existingStart != null) {
                    return Map.of("active", true, "sessionStart", existingStart.toString());
                }
            }
            // 不在时间窗内：认为是过期/残留标记，清理后继续走正常校验
            redisTemplate.delete(activeKey);
        }

        // 到点才能开课：按 sys_course_schedule（周几 × 节次）匹配当前时间窗
        Course course = courseMapper.selectCourseById(courseId);
        if (course == null) throw new RuntimeException("课程不存在，courseId=" + courseId);

        List<CourseScheduleSlot> todaySlots = courseScheduleMapper.selectByCourseIdAndDay(courseId, today);
        if (todaySlots == null || todaySlots.isEmpty()) {
            throw new RuntimeException("今天没有该课程的排课记录，无法开启在线课堂（请联系管理员在课程表中设置）");
        }

        int active = courseScheduleMapper.countActiveByCourseIdAndTime(courseId, today, nowTimeStr);
        if (active <= 0) {
            LocalTime nowTime = now.toLocalTime();
            CourseScheduleSlot next = null;
            for (CourseScheduleSlot slot : todaySlots) {
                if (slot == null) continue;
                LocalTime st = parseCourseTime(slot.getStartTime());
                if (st == null) continue;
                if (st.isAfter(nowTime)) {
                    next = slot;
                    break;
                }
            }
            if (next != null) {
                long minutes = java.time.Duration.between(now.toLocalTime(), parseCourseTime(next.getStartTime())).toMinutes();
                if (minutes >= 0 && minutes <= 15) {
                    throw new RuntimeException("快到上课时间了：距离第" + next.getPeriodIndex() + "节还有" + minutes + "分钟（" + normalizeTimeString(next.getStartTime()) + "开始）");
                }
                throw new RuntimeException("未到上课时间，下一节为第" + next.getPeriodIndex() + "节，开始时间：" + normalizeTimeString(next.getStartTime()));
            }
            throw new RuntimeException("当前不在上课时间范围内，无法开启在线课堂");
        }
        // 设置 activeKey 的 TTL：优先使用当前节次 end_time（+30分钟缓冲）；否则默认 6 小时，避免残留导致“未到点也能进/开课”
        long ttlSeconds = 6 * 60 * 60;
        LocalTime nowTime = now.toLocalTime();
        for (CourseScheduleSlot slot : todaySlots) {
            if (slot == null) continue;
            LocalTime st = parseCourseTime(slot.getStartTime());
            if (st == null) continue;
            LocalTime et = parseCourseTime(slot.getEndTime());
            boolean hit = !nowTime.isBefore(st) && (et == null || !nowTime.isAfter(et));
            if (!hit) continue;

            if (et != null) {
                LocalDateTime endAt = LocalDateTime.of(LocalDate.now(), et);
                long seconds = java.time.Duration.between(now, endAt).getSeconds();
                ttlSeconds = Math.max(10 * 60, seconds + 30 * 60);
            }
            break;
        }
        redisTemplate.opsForValue().set(activeKey, "1", ttlSeconds, TimeUnit.SECONDS);
        setSessionStart(courseId, now);
        classroomEventBus.publish(new ClassroomEvent("start", courseId, null, Map.of("courseId", courseId, "sessionStart", now.toString())));
        return Map.of("active", true, "sessionStart", now.toString());
    }

    @Override
    public Map<String, Object> endClassroom(Long courseId) {
        if (courseId == null) throw new RuntimeException("courseId 不能为空");
        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(courseId)) throw new RuntimeException("无权操作该课程");

        // 结束课堂时同步结束签到：确保 attendance_summary 能写入，仪表盘可以立刻看到变化
        try {
            stopCheckIn(courseId);
        } catch (Exception ignored) {}

        resetClassroom(courseId);
        redisTemplate.delete("classroom:active:" + courseId);
        classroomEventBus.publish(new ClassroomEvent("end", courseId, null, Map.of("courseId", courseId)));
        return Map.of("active", false);
    }

    @Override
    public Map<String, Object> getClassroomStatus(Long courseId) {
        if (courseId == null) {
            Map<String, Object> res = new HashMap<>();
            res.put("active", false);
            res.put("sessionStart", null);
            res.put("canEnter", false);
            res.put("canStart", false);
            res.put("message", "courseId不能为空");
            return res;
        }

        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(courseId)) throw new RuntimeException("无权操作该课程");

        String activeKey = "classroom:active:" + courseId;
        boolean active = Boolean.TRUE.equals(redisTemplate.hasKey(activeKey));
        LocalDateTime sessionStart = getSessionStart(courseId, false);
        LocalDateTime now = LocalDateTime.now();
        int today = now.getDayOfWeek().getValue();
        String nowTimeStr = now.toLocalTime().format(COURSE_TIME_FORMATTER);

        boolean canEnter = false;
        boolean canStart = false;
        String message = null;
        Integer nextPeriodIndex = null;
        String nextStartTime = null;

        // 已开启的课堂：仅在“当前处于上课时间窗”才允许进入/继续；否则认为是残留 active，清理
        if (active) {
            int inWindow = courseScheduleMapper.countActiveByCourseIdAndTime(courseId, today, nowTimeStr);
            if (inWindow > 0) {
                canEnter = true;
                canStart = true;
            } else {
                redisTemplate.delete(activeKey);
                active = false;
            }
        }

        if (!active) {
            List<CourseScheduleSlot> todaySlots = courseScheduleMapper.selectByCourseIdAndDay(courseId, today);
            if (todaySlots == null || todaySlots.isEmpty()) {
                message = "今天没有该课程的排课记录，未到上课时间无法进入/开启在线课堂";
            } else {
                int inWindow = courseScheduleMapper.countActiveByCourseIdAndTime(courseId, today, nowTimeStr);
                if (inWindow > 0) {
                    canEnter = true;
                    canStart = true;
                } else {
                    LocalTime nowTime = now.toLocalTime();
                    CourseScheduleSlot next = null;
                    for (CourseScheduleSlot slot : todaySlots) {
                        if (slot == null) continue;
                        LocalTime st = parseCourseTime(slot.getStartTime());
                        if (st == null) continue;
                        if (st.isAfter(nowTime)) {
                            next = slot;
                            break;
                        }
                    }
                    if (next != null) {
                        nextPeriodIndex = next.getPeriodIndex();
                        nextStartTime = normalizeTimeString(next.getStartTime());
                        long minutes = java.time.Duration.between(now.toLocalTime(), parseCourseTime(next.getStartTime())).toMinutes();
                        if (minutes >= 0 && minutes <= 15) {
                            message = "快到上课时间了：距离第" + nextPeriodIndex + "节还有" + minutes + "分钟（" + nextStartTime + "开始）";
                        } else {
                            message = "未到上课时间，下一节为第" + nextPeriodIndex + "节，开始时间：" + nextStartTime;
                        }
                    } else {
                        message = "当前不在上课时间范围内，无法进入/开启在线课堂";
                    }
                }
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("active", active);
        res.put("sessionStart", sessionStart == null ? null : sessionStart.toString());
        res.put("canEnter", canEnter);
        res.put("canStart", canStart);
        res.put("message", message);
        res.put("nextPeriodIndex", nextPeriodIndex);
        res.put("nextStartTime", nextStartTime);
        return res;
    }

    @Override
    @Transactional
    public void resetClassroom(Long courseId) {
        if (courseId == null) return;

        // 课堂结束前把“本节课堂互动”沉淀到 teacher_interaction，避免 reset 后清空数据导致仪表盘不变
        try {
            persistClassroomInteractionToTeacherInteraction(courseId);
        } catch (Exception ignored) {}

        try {
            List<Map<String, Object>> perf = getClassroomPerformance(courseId);
            Map<String, Object> payload = new HashMap<>();
            payload.put("courseId", courseId);
            payload.put("generatedAt", java.time.LocalDateTime.now());
            payload.put("students", perf == null ? Collections.emptyList() : perf);
            AnalysisResult ar = new AnalysisResult();
            ar.setCourseId(courseId);
            ar.setMetric("classroom_online_performance");
            ar.setEventId("classroom_perf_" + courseId + "_" + System.currentTimeMillis());
            ar.setGeneratedAt(new java.sql.Timestamp(System.currentTimeMillis()));
            ar.setValueJson(objectMapper.writeValueAsString(payload));
            analysisResultMapper.insert(ar);
        } catch (Exception ignored) {}

        List<Long> qids = onlineQuestionMapper.selectIdsByCourseId(courseId);
        if (qids != null && !qids.isEmpty()) {
            onlineAnswerMapper.deleteByQuestionIds(qids);
        }
        onlineQuestionMapper.deleteByCourseId(courseId);
        classroomChatStore.clear(courseId);
        setSessionStart(courseId, LocalDateTime.now());
        classroomEventBus.publish(new ClassroomEvent("reset", courseId, null, Map.of("courseId", courseId)));
    }

    private void persistClassroomInteractionToTeacherInteraction(Long courseId) {
        if (courseId == null) return;
        LocalDateTime sessionStart = getSessionStart(courseId, false);
        if (sessionStart == null) {
            sessionStart = LocalDateTime.now().minusHours(4);
        }
        final LocalDateTime finalSessionStart = sessionStart;

        User teacher = getCurrentTeacher();
        List<User> students = getStudentsByCourseId(courseId);
        Map<Long, Long> studentClassMap = new HashMap<>();
        for (User s : students) {
            if (s.getUserId() != null && s.getClassId() != null) {
                studentClassMap.put(s.getUserId(), s.getClassId());
            }
        }

        List<OnlineAnswer> answers = onlineAnswerMapper.selectByCourseIds(Collections.singletonList(courseId));
        List<OnlineAnswer> sessionAnswers = answers == null ? Collections.emptyList() : answers.stream()
                .filter(a -> a.getCreateTime() != null && a.getCreateTime().isAfter(finalSessionStart))
                .collect(Collectors.toList());

        List<CourseChat> chats = classroomChatStore.list(courseId, 200);
        List<CourseChat> sessionChats = chats == null ? Collections.emptyList() : chats.stream()
                .filter(c -> c.getCreateTime() != null && c.getCreateTime().isAfter(finalSessionStart))
                .collect(Collectors.toList());

        class Agg {
            int studentTalkSeconds = 0;
            int interactionCount = 0;
        }
        Map<Long, Agg> aggByClass = new HashMap<>();

        for (OnlineAnswer a : sessionAnswers) {
            Long classId = a.getStudentId() == null ? null : studentClassMap.get(a.getStudentId());
            if (classId == null) continue;
            Agg agg = aggByClass.computeIfAbsent(classId, k -> new Agg());
            String type = a.getType() == null ? "answer" : a.getType();
            agg.studentTalkSeconds += ("hand".equals(type) || "race".equals(type)) ? 20 : 45;
            agg.interactionCount += 1;
        }

        for (CourseChat c : sessionChats) {
            if (c == null) continue;
            if (!"student".equals(c.getRole())) continue;
            Long classId = c.getSenderId() == null ? null : studentClassMap.get(c.getSenderId());
            if (classId == null) continue;
            Agg agg = aggByClass.computeIfAbsent(classId, k -> new Agg());
            agg.studentTalkSeconds += 15;
            agg.interactionCount += 1;
        }

        if (aggByClass.isEmpty()) return;

        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        List<TeacherInteraction> rows = new ArrayList<>();
        for (Map.Entry<Long, Agg> e : aggByClass.entrySet()) {
            TeacherInteraction ti = new TeacherInteraction();
            ti.setCourseId(courseId);
            ti.setClassId(e.getKey());
            ti.setTeacherId(teacher.getUserId());
            ti.setDate(today);
            ti.setTalkTimeSeconds(0);
            ti.setStudentTalkSeconds(e.getValue().studentTalkSeconds);
            ti.setGroupTalkSeconds(0);
            ti.setInteractionCount(e.getValue().interactionCount);
            rows.add(ti);
        }

        List<Long> classIds = rows.stream().map(TeacherInteraction::getClassId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        teacherInteractionMapper.deleteByCourseIdAndDateAndClassIds(courseId, today, classIds);
        teacherInteractionMapper.batchInsert(rows);
    }

    // 【核心实现】获取课堂表现（当前会话）- 按班级分组
    @Override
    public List<Map<String, Object>> getClassroomPerformance(Long courseId) {
        // 1. 获取当前会话开始时间
        LocalDateTime sessionStart = getSessionStart(courseId, false);
        if (sessionStart == null) {
            // 如果没开始上课，默认查询最近 4 小时
            sessionStart = LocalDateTime.now().minusHours(4);
        }

        // 2. 获取该课程下的所有学生（用于分组和补全数据）
        List<User> allStudents = getStudentsByCourseId(courseId);
        Map<Long, User> studentMap = allStudents.stream().collect(Collectors.toMap(User::getUserId, u -> u));

        // 3. 获取会话期间的所有互动记录
        List<OnlineAnswer> answers = onlineAnswerMapper.selectByCourseIds(Collections.singletonList(courseId));
        LocalDateTime finalSessionStart = sessionStart;
        List<OnlineAnswer> sessionAnswers = answers.stream()
                .filter(a -> a.getCreateTime() != null && a.getCreateTime().isAfter(finalSessionStart))
                .collect(Collectors.toList());

        // 4. 聚合数据
        Map<Long, Map<String, Object>> studentStatsMap = new HashMap<>();

        // 初始化所有学生数据
        for (User s : allStudents) {
            Map<String, Object> stat = new HashMap<>();
            stat.put("studentId", s.getUserId());
            stat.put("name", s.getRealName());
            stat.put("username", s.getUsername());
            stat.put("classId", s.getClassId());
            stat.put("hand", 0);
            stat.put("race", 0);
            stat.put("answer", 0);
            stat.put("chat", 0);
            stat.put("called", 0);
            stat.put("correct", 0);
            stat.put("wrong", 0);
            stat.put("total", 0);
            stat.put("lastTime", "-");
            studentStatsMap.put(s.getUserId(), stat);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");

        // 发言（聊天）统计：以课堂群聊中学生消息作为发言次数
        try {
            List<CourseChat> chats = classroomChatStore.list(courseId, 200);
            for (CourseChat c : chats) {
                if (c == null || c.getSenderId() == null) continue;
                if (c.getCreateTime() == null || !c.getCreateTime().isAfter(sessionStart)) continue;
                if (!"student".equalsIgnoreCase(c.getRole())) continue;

                Map<String, Object> stat = studentStatsMap.get(c.getSenderId());
                if (stat == null) {
                    User s = userMapper.selectById(c.getSenderId());
                    stat = new HashMap<>();
                    stat.put("studentId", c.getSenderId());
                    stat.put("name", s != null ? s.getRealName() : "未知学生");
                    stat.put("username", s != null ? s.getUsername() : String.valueOf(c.getSenderId()));
                    stat.put("classId", s != null ? s.getClassId() : 0L);
                    stat.put("hand", 0);
                    stat.put("race", 0);
                    stat.put("answer", 0);
                    stat.put("chat", 0);
                    stat.put("called", 0);
                    stat.put("correct", 0);
                    stat.put("wrong", 0);
                    stat.put("total", 0);
                    stat.put("lastTime", "-");
                    studentStatsMap.put(c.getSenderId(), stat);
                }

                stat.put("chat", (int) stat.get("chat") + 1);
                stat.put("total", (int) stat.get("total") + 1);

                String timeStr = c.getCreateTime().format(dtf);
                String last = (String) stat.get("lastTime");
                if ("-".equals(last) || timeStr.compareTo(last) > 0) {
                    stat.put("lastTime", timeStr);
                }
            }
        } catch (Exception ignored) {}

        for (OnlineAnswer a : sessionAnswers) {
            Map<String, Object> stat = studentStatsMap.get(a.getStudentId());
            // 如果是有互动的学生但不在班级列表里（可能是旁听生），也加进去
            if (stat == null) {
                User s = userMapper.selectById(a.getStudentId());
                stat = new HashMap<>();
                stat.put("studentId", a.getStudentId());
                stat.put("name", s != null ? s.getRealName() : "未知学生");
                stat.put("username", s != null ? s.getUsername() : String.valueOf(a.getStudentId()));
                stat.put("classId", s != null ? s.getClassId() : 0L);
                stat.put("hand", 0);
                stat.put("race", 0);
                stat.put("answer", 0);
                stat.put("chat", 0);
                stat.put("called", 0);
                stat.put("correct", 0);
                stat.put("wrong", 0);
                stat.put("total", 0);
                stat.put("lastTime", "-");
                studentStatsMap.put(a.getStudentId(), stat);
            }

            OnlineAnswer enriched = enrichAnswer(a);
            String type = enriched.getType() == null ? "answer" : enriched.getType();

            stat.put("total", (int)stat.get("total") + 1);
            if ("hand".equals(type)) stat.put("hand", (int)stat.get("hand") + 1);
            else if ("race".equals(type)) stat.put("race", (int)stat.get("race") + 1);
            else {
                stat.put("answer", (int)stat.get("answer") + 1);
                if (enriched.getCorrect() != null) {
                    if (Boolean.TRUE.equals(enriched.getCorrect())) stat.put("correct", (int) stat.get("correct") + 1);
                    else stat.put("wrong", (int) stat.get("wrong") + 1);
                }
            }

            if ("called".equalsIgnoreCase(enriched.getState())) {
                stat.put("called", (int) stat.get("called") + 1);
            }

            // 更新最近时间
            String timeStr = a.getCreateTime().format(dtf);
            String last = (String) stat.get("lastTime");
            if ("-".equals(last) || timeStr.compareTo(last) > 0) {
                stat.put("lastTime", timeStr);
            }
        }

        // 5. 按班级分组并计算班级整体指标
        Map<Long, List<Map<String, Object>>> classGroup = new HashMap<>();
        for (Map<String, Object> stat : studentStatsMap.values()) {
            Long cid = (Long) stat.get("classId");
            classGroup.computeIfAbsent(cid, k -> new ArrayList<>()).add(stat);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        // 获取班级名称
        List<Class> classes = classMapper.selectAllClasses();
        Map<Long, String> classNameMap = classes.stream().collect(Collectors.toMap(Class::getClassId, Class::getClassName));

        for (Map.Entry<Long, List<Map<String, Object>>> entry : classGroup.entrySet()) {
            Long cid = entry.getKey();
            List<Map<String, Object>> students = entry.getValue();

            // 计算指标
            int totalInteractions = students.stream().mapToInt(s -> (int)s.get("total")).sum();
            long activeCount = students.stream().filter(s -> (int)s.get("total") > 0).count();

            // 活跃指数算法 (0-100)：(活跃人数占比 * 60) + (人均互动 * 20)
            double activeRate = students.isEmpty() ? 0 : (double) activeCount / students.size();
            double avgInter = students.isEmpty() ? 0 : (double) totalInteractions / students.size();
            int score = (int) Math.min(100, (activeRate * 60 + avgInter * 20));
            // 基础分 40+，只要有人互动就有分
            if (totalInteractions > 0 && score < 40) score = 40 + (int)(activeRate * 40);

            Map<String, Object> classData = new HashMap<>();
            classData.put("classId", cid);
            classData.put("className", classNameMap.getOrDefault(cid, cid + "班"));
            classData.put("studentCount", students.size());
            classData.put("activeCount", activeCount);
            classData.put("score", score);
            // 对学生列表排序：互动多的在前
            students.sort((a, b) -> ((Integer)b.get("total")).compareTo((Integer)a.get("total")));
            classData.put("students", students);

            result.add(classData);
        }

        // 按班级ID排序
        result.sort(Comparator.comparingLong(m -> (Long)m.get("classId")));

        return result;
    }

    @Override
    @Transactional
    public AnalysisResultResponse generateClassroomAnalysisResult(Long courseId, String metric) {
        if (courseId == null) throw new RuntimeException("courseId 不能为空");
        Set<Long> myCourseIds = getMyCourses().stream().map(Course::getId).collect(Collectors.toSet());
        if (!myCourseIds.contains(courseId)) throw new RuntimeException("无权操作该课程");

        String m = (metric == null || metric.trim().isEmpty()) ? "classroom_online_performance" : metric.trim();
        if (!"classroom_online_performance".equals(m)) {
            throw new RuntimeException("暂不支持该 metric: " + m);
        }

        List<Map<String, Object>> perf = getClassroomPerformance(courseId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", courseId);
        payload.put("generatedAt", java.time.LocalDateTime.now());
        payload.put("students", perf == null ? Collections.emptyList() : perf);

        AnalysisResult ar = new AnalysisResult();
        ar.setCourseId(courseId);
        ar.setMetric(m);
        ar.setEventId("manual_" + courseId + "_" + System.currentTimeMillis());
        ar.setGeneratedAt(new java.sql.Timestamp(System.currentTimeMillis()));
        try {
            ar.setValueJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            ar.setValueJson("{\"courseId\":" + courseId + ",\"students\":[]}");
        }
        analysisResultMapper.insert(ar);

        AnalysisResultResponse r = new AnalysisResultResponse();
        r.setId(ar.getId());
        r.setCourseId(ar.getCourseId());
        r.setMetric(ar.getMetric());
        r.setValueJson(ar.getValueJson());
        r.setGeneratedAt(ar.getGeneratedAt());
        r.setEventId(ar.getEventId());
        try {
            r.setValue(objectMapper.readTree(ar.getValueJson()));
        } catch (Exception ignored) {
            r.setValue(null);
        }
        return r;
    }

    private OnlineQuestion enrichQuestion(OnlineQuestion q) {
        if (q == null) return null;
        q.setMode("broadcast");
        q.setDescription(q.getContent());
        if (q.getContent() != null && q.getContent().trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(q.getContent());
                if (node.has("mode")) q.setMode(node.get("mode").asText());
                if (node.has("description")) q.setDescription(node.get("description").asText());
                if (node.has("assignStudentId") && !node.get("assignStudentId").isNull()) {
                    q.setAssignStudentId(node.get("assignStudentId").asLong());
                }
            } catch (Exception ignored) {}
        }
        return q;
    }

    private OnlineAnswer enrichAnswer(OnlineAnswer a) {
        if (a == null) return null;
        a.setType("answer");
        a.setState("answered");
        a.setText(a.getAnswerText());
        a.setCorrect(null);
        if (a.getAnswerText() != null && a.getAnswerText().trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(a.getAnswerText());
                if (node.has("type")) a.setType(node.get("type").asText());
                if (node.has("state")) a.setState(node.get("state").asText());
                if (node.has("text")) a.setText(node.get("text").asText());
                if (node.has("correct") && !node.get("correct").isNull()) a.setCorrect(node.get("correct").asBoolean());
            } catch (Exception ignored) {}
        }
        return a;
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

    // 辅助：获取课程下的所有学生
    private List<User> getStudentsByCourseId(Long courseId) {
        Course course = courseMapper.selectAllCourses().stream().filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
        if (course == null) return Collections.emptyList();

        List<Long> classIds = new ArrayList<>();
        if (course.getResponsibleClassIds() != null && !course.getResponsibleClassIds().isEmpty()) {
            for (String s : course.getResponsibleClassIds().split(",")) {
                try { classIds.add(Long.parseLong(s.trim())); } catch (Exception e) {}
            }
        } else if (course.getClassId() != null) {
            classIds.add(course.getClassId());
        }

        if (classIds.isEmpty()) return Collections.emptyList();
        return userMapper.selectStudentsByClassIds(classIds);
    }
}
