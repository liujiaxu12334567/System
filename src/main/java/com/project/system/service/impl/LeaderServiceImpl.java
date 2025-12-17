package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.dto.TeacherAnalysisResponse;
import com.project.system.entity.*;
import com.project.system.entity.Class;
import com.project.system.mapper.*;
import com.project.system.service.LeaderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderServiceImpl implements LeaderService {

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private CourseGroupMapper courseGroupMapper;
    @Autowired private AnalysisResultMapper analysisResultMapper;
    @Autowired private ClassMapper classMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private ExamMapper examMapper;
    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private RabbitTemplate rabbitTemplate;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    private User getCurrentLeader() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    // 辅助方法：获取课程信息 (简化实现)
    private Course getCourseInfo(Long courseId) {
        List<Course> all = courseMapper.selectAllCourses();
        return all.stream().filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
    }

    // 辅助方法：发送任务发布通知
    private void sendTaskNotification(Long courseId, String title, String type, String senderName) {
        Course course = getCourseInfo(courseId);
        if (course == null || course.getClassId() == null) return;

        List<User> students = userMapper.selectStudentsByClassIds(Collections.singletonList(course.getClassId()));

        for (User student : students) {
            Notification n = new Notification();
            n.setUserId(student.getUserId());
            n.setRelatedId(courseId);
            n.setType("TASK_PUBLISHED");
            n.setTitle(type + "发布：" + title);
            n.setMessage("课程《" + course.getName() + "》发布了新的" + type + "，请前往课程学习查看。");
            n.setSenderName(senderName);
            notificationMapper.insert(n);
        }
    }


    @Override
    public void publishCourseExam(Long courseId, Map<String, Object> examData) {
        String title = (String) examData.get("title");
        String content = (String) examData.get("content");
        String startTime = (String) examData.get("startTime");
        String deadline = (String) examData.get("deadline");
        Integer duration = (Integer) examData.get("duration");
        String status = (String) examData.get("status");

        User leader = getCurrentLeader();

        if (title == null || content == null || startTime == null || deadline == null) {
            throw new IllegalArgumentException("考试信息不完整");
        }

        Exam exam = new Exam();
        exam.setCourseId(courseId);
        exam.setTitle(title);
        exam.setContent(content);
        exam.setStartTime(startTime);
        exam.setDeadline(deadline);
        exam.setDuration(duration != null ? duration : 60);
        exam.setStatus(status != null ? status : "未开始");
        exam.setTeacherId(leader.getUserId());

        examMapper.insertExam(exam);

        sendTaskNotification(courseId, title, "正式考试", leader.getRealName());
    }

    @Override
    @Cacheable(cacheNames = "leader_teacher_list", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public List<Object> listMyTeamMembers() {
        User currentLeader = getCurrentLeader();
        if (currentLeader == null) return Collections.emptyList();

        // 新模型：组长负责课程来自 sys_course_group（按 leader_id），不再依赖 sys_user.teacherRank
        List<CourseGroup> managedGroups = courseGroupMapper.selectByLeaderId(currentLeader.getUserId());
        if (managedGroups == null || managedGroups.isEmpty()) {
            return Collections.singletonList(currentLeader);
        }

        Set<Long> managedGroupIds = managedGroups.stream()
                .map(CourseGroup::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> memberIds = new HashSet<>();
        memberIds.add(currentLeader.getUserId());

        for (Course c : courseMapper.selectAllCourses()) {
            if (c.getGroupId() == null) continue;
            if (!managedGroupIds.contains(c.getGroupId())) continue;
            if (c.getTeacherId() != null) memberIds.add(c.getTeacherId());
        }

        List<User> allTeachers = new ArrayList<>();
        allTeachers.addAll(userMapper.selectUsersByRole("3"));
        allTeachers.addAll(userMapper.selectUsersByRole("2"));

        List<Object> result = new ArrayList<>();
        for (User u : allTeachers) {
            if (u != null && u.getUserId() != null && memberIds.contains(u.getUserId())) {
                result.add(u);
            }
        }
        result.removeIf(o -> (o instanceof User) && ((User) o).getUserId().equals(currentLeader.getUserId()));
        result.add(0, currentLeader);
        return result;
    }

    @Override
    @Cacheable(cacheNames = "leader_course_list", key = "T(org.springframework.security.core.context.SecurityContextHolder).getContext().getAuthentication().getName()")
    public List<Course> listMyCourses() {
        User currentLeader = getCurrentLeader();
        List<Course> allCourses = courseMapper.selectAllCourses();
        List<Course> myCourses = new ArrayList<>();
        Set<String> rankCourseNames = new HashSet<>();

        if (currentLeader.getTeacherRank() != null) {
            Arrays.stream(currentLeader.getTeacherRank().replace("，", ",").split(","))
                    .forEach(r -> rankCourseNames.add(r.trim()));
        }

        for (Course c : allCourses) {
            boolean isManager = c.getManagerName() != null && c.getManagerName().equals(currentLeader.getRealName());
            boolean isNameMatch = rankCourseNames.contains(c.getName());
            if (isManager || isNameMatch) myCourses.add(c);
        }
        return myCourses;
    }

    @Override
    public List<TeacherAnalysisResponse> listTeacherAnalysis(String metric) {
        User currentLeader = getCurrentLeader();
        if (currentLeader == null) return Collections.emptyList();

        String m = metric == null ? "" : metric.trim();
        if (m.isEmpty()) m = "classroom_online_performance";

        List<CourseGroup> managedGroups = courseGroupMapper.selectByLeaderId(currentLeader.getUserId());
        if (managedGroups == null || managedGroups.isEmpty()) return Collections.emptyList();

        Set<Long> managedGroupIds = managedGroups.stream()
                .map(CourseGroup::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Course> managedCourses = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getGroupId() != null && managedGroupIds.contains(c.getGroupId()))
                .collect(Collectors.toList());

        if (managedCourses.isEmpty()) return Collections.emptyList();

        Map<Long, List<Course>> coursesByTeacherId = managedCourses.stream()
                .filter(c -> c.getTeacherId() != null)
                .collect(Collectors.groupingBy(Course::getTeacherId));

        List<Long> courseIds = managedCourses.stream().map(Course::getId).filter(Objects::nonNull).toList();
        Map<Long, AnalysisResult> latestByCourseId = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (AnalysisResult ar : analysisResultMapper.selectLatestByCourseIdsAndMetric(courseIds, m)) {
                if (ar != null && ar.getCourseId() != null) {
                    latestByCourseId.put(ar.getCourseId(), ar);
                }
            }
        }

        List<TeacherAnalysisResponse> result = new ArrayList<>();
        for (Map.Entry<Long, List<Course>> entry : coursesByTeacherId.entrySet()) {
            Long teacherId = entry.getKey();
            User teacher = userMapper.selectById(teacherId);
            String teacherName = teacher != null ? teacher.getRealName() : String.valueOf(teacherId);

            List<TeacherAnalysisResponse.CourseAnalysisItem> items = new ArrayList<>();
            TeacherAnalysisResponse.CourseAnalysisItem latestItem = null;

            for (Course c : entry.getValue()) {
                TeacherAnalysisResponse.CourseAnalysisItem item = new TeacherAnalysisResponse.CourseAnalysisItem();
                item.setCourseId(c.getId());
                item.setCourseName(c.getName());
                item.setSemester(c.getSemester());
                item.setClassId(c.getClassId());
                item.setMetric(m);

                AnalysisResult ar = c.getId() == null ? null : latestByCourseId.get(c.getId());
                if (ar != null) {
                    item.setValueJson(ar.getValueJson());
                    item.setGeneratedAt(ar.getGeneratedAt());

                    if (latestItem == null) {
                        latestItem = item;
                    } else if (latestItem.getGeneratedAt() == null) {
                        latestItem = item;
                    } else if (item.getGeneratedAt() != null && item.getGeneratedAt().after(latestItem.getGeneratedAt())) {
                        latestItem = item;
                    }
                }

                items.add(item);
            }

            TeacherAnalysisResponse tr = new TeacherAnalysisResponse();
            tr.setTeacherId(teacherId);
            tr.setTeacherName(teacherName);
            tr.setCourseCount(items.size());
            tr.setLatest(latestItem);
            tr.setCourses(items);
            result.add(tr);
        }

        result.sort(Comparator.comparing(TeacherAnalysisResponse::getTeacherName, Comparator.nullsLast(String::compareTo)));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadCourseMaterial(Long courseId, String type, String title, String content, String deadline, MultipartFile file) {
        String finalContent = content;
        String finalFileName = "无标题资料";
        String filePath = "";

        if ("知识图谱".equals(type) || "目录".equals(type)) {
            finalFileName = (title != null ? title : (type + ".json"));
        } else {
            if (file != null && !file.isEmpty()) {
                try {
                    File directory = new File(uploadDir);
                    if (!directory.exists()) directory.mkdirs();
                    String originalFilename = file.getOriginalFilename();
                    String extension = originalFilename != null && originalFilename.lastIndexOf(".") > 0 ?
                            originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
                    String uniqueName = UUID.randomUUID().toString() + extension;
                    Path path = Paths.get(uploadDir + File.separator + uniqueName);
                    Files.write(path, file.getBytes());
                    filePath = path.toString();
                    finalFileName = (title != null && !title.isEmpty()) ? title : originalFilename;
                } catch (IOException e) {
                    throw new RuntimeException("文件上传失败: " + e.getMessage());
                }
            } else if (title != null) {
                finalFileName = title;
            }

            if (deadline != null && !deadline.isEmpty()) {
                finalContent = mergeDeadlineIntoContent(content, deadline);
            }
        }

        Material material = new Material();
        material.setCourseId(courseId);
        material.setType(type);
        material.setContent(finalContent);
        material.setFileName(finalFileName);
        material.setFilePath(filePath);
        materialMapper.insert(material);

        // 【新增通知逻辑】
        if (Arrays.asList("测验", "作业", "项目").contains(type)) {
            String finalTitle = title != null ? title : (file != null ? file.getOriginalFilename() : type);
            User leader = getCurrentLeader();
            sendTaskNotification(courseId, finalTitle, type, leader.getRealName());
        }
    }

    @Override
    public void sendNotification(String title, String content, List<String> targetUsernames) {
        User leader = getCurrentLeader();
        if (leader == null) throw new RuntimeException("未登录或无权限");

        String t = title == null ? "" : title.trim();
        String c = content == null ? "" : content.trim();
        if (t.isEmpty() || c.isEmpty()) throw new RuntimeException("通知标题和内容不能为空");

        // 收件人范围：仅允许发送给本组长负责课程组内的教师（按 sys_course_group.leader_id -> sys_course.group_id -> teacher_id）
        List<CourseGroup> managedGroups = courseGroupMapper.selectByLeaderId(leader.getUserId());
        if (managedGroups == null || managedGroups.isEmpty()) {
            throw new RuntimeException("当前账号未绑定负责的课程组，无法发送通知");
        }

        Set<Long> managedGroupIds = managedGroups.stream()
                .map(CourseGroup::getGroupId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> allowedTeacherIds = courseMapper.selectAllCourses().stream()
                .filter(course -> course.getGroupId() != null && managedGroupIds.contains(course.getGroupId()))
                .map(Course::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (allowedTeacherIds.isEmpty()) {
            throw new RuntimeException("当前负责课程组暂无可通知的教师");
        }

        List<User> recipients = new ArrayList<>();
        if (targetUsernames == null || targetUsernames.isEmpty()) {
            for (Long teacherId : allowedTeacherIds) {
                User u = userMapper.selectById(teacherId);
                if (u != null && u.getUserId() != null) recipients.add(u);
            }
        } else {
            for (String username : targetUsernames) {
                if (username == null || username.trim().isEmpty()) continue;
                User u = userMapper.findByUsername(username.trim());
                if (u == null || u.getUserId() == null) continue;
                if (!allowedTeacherIds.contains(u.getUserId())) {
                    throw new RuntimeException("无权发送给该用户：" + username);
                }
                recipients.add(u);
            }
        }

        if (recipients.isEmpty()) throw new RuntimeException("未找到可发送的目标教师");

        String batchId = recipients.size() > 1 ? ("leader-" + UUID.randomUUID()) : null;
        for (User u : recipients) {
            Notification n = new Notification();
            n.setUserId(u.getUserId());
            n.setType("LEADER_NOTICE");
            n.setTitle(t);
            n.setMessage(c);
            n.setBatchId(batchId);
            n.setSenderName(leader.getRealName());
            n.setIsActionRequired(false);
            notificationMapper.insert(n);
        }
    }

    @Override
    @CacheEvict(cacheNames = {"course_info", "leader_course_list"}, allEntries = true)
    @Transactional
    public void updateCourse(Course course) {
        courseMapper.updateCourse(course);
        if (course.getTeacher() != null && course.getClassId() != null) {
            List<String> names = Arrays.asList(course.getTeacher().replace("，", ",").split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());
            updateTeacherTeachingClasses(names, classIds);
        }
    }

    @Override
    @CacheEvict(cacheNames = {"course_info", "leader_course_list"}, allEntries = true)
    public void deleteCourse(Long id) {
        courseMapper.deleteCourseById(id);
    }

    @Override
    @CacheEvict(cacheNames = {"course_info", "leader_course_list"}, allEntries = true)
    @Transactional
    public void batchAssignCourse(String name, String semester, List<String> teacherNames, List<Object> rawClassIds) {
        User currentUser = getCurrentLeader();
        List<Long> classIds = rawClassIds.stream()
                .map(obj -> Long.parseLong(obj.toString()))
                .collect(Collectors.toList());
        String teacherStr = String.join(",", teacherNames);
        List<Course> courses = new ArrayList<>();

        for (Long cid : classIds) {
            if (classMapper.findById(cid) == null) {
                classMapper.insert(new Class(cid, cid + "班", "未分配专业"));
            }
            Course c = new Course();
            c.setName(name);
            c.setSemester(semester);
            c.setTeacher(teacherStr);
            c.setCode("L" + System.currentTimeMillis() % 10000 + "-" + cid);
            c.setStatus("进行中");
            c.setColor("blue");
            c.setIsTop(0);
            c.setClassId(cid);
            c.setManagerName(currentUser.getRealName());
            courses.add(c);
        }
        if (!courses.isEmpty()) courseMapper.insertBatchCourses(courses);
        updateTeacherTeachingClasses(teacherNames, classIds);
    }

    @Override
    public void updateMaterialDeadline(Long materialId, String newDeadline) {
        Material material = materialMapper.findById(materialId);
        if (material == null) throw new RuntimeException("资料不存在");

        if (newDeadline == null || newDeadline.isBlank()) {
            throw new RuntimeException("新的截止时间不能为空");
        }
        try {
            LocalDateTime.parse(newDeadline.trim(), DEADLINE_FORMATTER);
        } catch (Exception e) {
            throw new RuntimeException("截止时间格式应为 yyyy-MM-dd HH:mm:ss");
        }

        String newContent = mergeDeadlineIntoContent(material.getContent(), newDeadline.trim());
        materialMapper.updateContent(materialId, newContent);
    }

    @Override
    public List<Object> listPendingApplications() {
        List<Application> allPending = applicationMapper.findByStatus("PENDING");
        return allPending.stream()
                .filter(app -> "DEADLINE_EXTENSION".equals(app.getType()))
                .collect(Collectors.toList());
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSendMaterialToTeachers(String type, String title, String content, String deadline, MultipartFile file, List<String> courseNames) {
        if (courseNames == null || courseNames.isEmpty()) {
            throw new IllegalArgumentException("请选择要下发的课程。");
        }

        User currentLeader = getCurrentLeader();
        String senderName = currentLeader.getRealName();
        String uniqueFileName = "";
        String filePath = "";
        String finalContent = content;

        // 1. 处理文件上传（文件只上传一次）
        if (file != null && !file.isEmpty()) {
            try {
                File directory = new File(uploadDir);
                if (!directory.exists()) directory.mkdirs();
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.lastIndexOf(".") > 0 ?
                        originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
                uniqueFileName = UUID.randomUUID().toString() + extension;
                Path path = Paths.get(uploadDir + File.separator + uniqueFileName);
                Files.write(path, file.getBytes());
                filePath = path.toString();
            } catch (IOException e) {
                throw new RuntimeException("文件上传失败: " + e.getMessage());
            }
        }

        // 2. 格式化内容（处理截止时间）
        if (deadline != null && !deadline.isEmpty()) {
            finalContent = mergeDeadlineIntoContent(content, deadline);
        }

        // 3. 查找所有受影响的课程
        List<Course> allCourses = courseMapper.selectAllCourses();

        // 用于记录已下发的课程ID，避免重复发送给同一个班级的课程实例
        Set<Long> processedCourseIds = new HashSet<>();

        for (String courseName : courseNames) {
            String trimmedCourseName = courseName.trim();

            // 查找所有名称匹配的课程实例
            List<Course> targetCourses = allCourses.stream()
                    .filter(c -> c.getName() != null && c.getName().equals(trimmedCourseName))
                    .collect(Collectors.toList());

            for (Course course : targetCourses) {
                if (processedCourseIds.contains(course.getId())) continue;

                // 4. 插入资料
                Material material = new Material();
                material.setCourseId(course.getId());
                material.setType(type);
                material.setContent(finalContent);
                // 这里使用 title 作为文件名，并将实际存储的文件名作为 filePath 的一部分（如果存在文件）
                material.setFileName(title != null ? title : (file != null ? file.getOriginalFilename() : type));
                // 确保文件路径是可访问的，这里只存 unique name，方便下载
                material.setFilePath(uniqueFileName);
                materialMapper.insert(material);
                processedCourseIds.add(course.getId());

                // 5. 如果是任务类，发送通知给所有班级学生
                if (Arrays.asList("测验", "作业", "项目").contains(type)) {
                    sendTaskNotification(course.getId(), material.getFileName(), type, senderName);
                }
            }
        }
    }
    // 【新增方法实现：批量发布考试】
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchPublishExam(Map<String, Object> examData, List<String> courseNames) {
        if (courseNames == null || courseNames.isEmpty()) {
            throw new IllegalArgumentException("请选择要发布考试的课程。");
        }

        String title = (String) examData.get("title");
        String content = (String) examData.get("content");
        String startTime = (String) examData.get("startTime");
        String deadline = (String) examData.get("deadline");
        Integer duration = (Integer) examData.get("duration");

        if (title == null || content == null || startTime == null || deadline == null) {
            throw new IllegalArgumentException("考试信息不完整");
        }

        User leader = getCurrentLeader();
        String initialStatus = (String) examData.get("status");
        if (initialStatus == null) {
            // 根据开始时间计算状态
            initialStatus = (new Date()).after(java.sql.Timestamp.valueOf(startTime)) ? "进行中" : "未开始";
        }


        List<Course> allCourses = courseMapper.selectAllCourses();
        Set<Long> processedCourseIds = new HashSet<>();

        for (String courseName : courseNames) {
            String trimmedCourseName = courseName.trim();

            List<Course> targetCourses = allCourses.stream()
                    .filter(c -> c.getName() != null && c.getName().equals(trimmedCourseName))
                    .collect(Collectors.toList());

            for (Course course : targetCourses) {
                if (processedCourseIds.contains(course.getId())) continue;

                Exam exam = new Exam();
                exam.setCourseId(course.getId());
                exam.setTitle(title);
                exam.setContent(content);
                exam.setStartTime(startTime);
                exam.setDeadline(deadline);
                exam.setDuration(duration != null ? duration : 60);
                exam.setStatus(initialStatus);
                exam.setTeacherId(leader.getUserId());

                examMapper.insertExam(exam);
                processedCourseIds.add(course.getId());

                sendTaskNotification(course.getId(), title, "正式考试", leader.getRealName());
            }
        }
        if (processedCourseIds.isEmpty()) {
            throw new RuntimeException("没有找到匹配的课程来发布考试。");
        }
    }

    private String mergeDeadlineIntoContent(String content, String deadline) {
        if (deadline == null || deadline.isEmpty()) return content;
        String c = content == null ? "" : content.trim();
        try {
            if (c.startsWith("{")) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(c);
                if (root != null && root.isObject()) {
                    ((ObjectNode) root).put("deadline", deadline);
                    return mapper.writeValueAsString(root);
                }
            }
        } catch (Exception ignored) {}

        // 非 JSON：兜底包装为 text + deadline
        String safeText = content == null ? "" : content.replace("\\", "\\\\").replace("\"", "\\\"");
        return String.format("{\"text\": \"%s\", \"deadline\": \"%s\"}", safeText, deadline);
    }
    @Override
    @Transactional
    public void reviewApplication(Long appId, String status) {
        Application app = applicationMapper.findById(appId);
        if (app == null) throw new RuntimeException("申请不存在");
        if (!"DEADLINE_EXTENSION".equals(app.getType())) throw new RuntimeException("权限不足");

        if ("APPROVED".equals(status)) {
            // 解析并执行延期
            try {
                String content = app.getContent();
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("延长至:\\s*(.*)").matcher(content);
                if (matcher.find()) {
                    String newDeadline = matcher.group(1).trim();
                    updateMaterialDeadline(app.getTargetId(), newDeadline);
                }
            } catch (Exception e) {
                throw new RuntimeException("自动执行延期失败: " + e.getMessage());
            }
        }
        applicationMapper.updateStatus(appId, status);
    }

    // 辅助方法：更新教师执教班级 (保持不变)
    private void updateTeacherTeachingClasses(List<String> teacherNames, List<Long> classIds) {
        // 不再向 sys_user.teaching_classes 写入，改用 sys_course.responsible_class_ids 维护教师-班级关系
    }
}
