package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.entity.*;
import com.project.system.entity.Class;
import com.project.system.mapper.*;
import com.project.system.service.LeaderService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LeaderServiceImpl implements LeaderService {

    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
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
    public List<Object> listMyTeamMembers() {
        User currentLeader = getCurrentLeader();
        if (currentLeader == null) return Collections.emptyList();

        // 1. 获取组长负责的所有课程名 (teacherRank)
        Set<String> leaderManagedCourses = new HashSet<>();
        if (currentLeader.getTeacherRank() != null && !currentLeader.getTeacherRank().isEmpty()) {
            Arrays.stream(currentLeader.getTeacherRank().replace("，", ",").split(","))
                    .map(String::trim)
                    .forEach(leaderManagedCourses::add);
        }

        if (leaderManagedCourses.isEmpty()) {
            // 如果组长没有负责任何课程，则只返回他自己
            return Collections.singletonList(currentLeader);
        }

        // 2. 查找所有教师 (包括其他组长和普通教师)
        List<User> allTeachers = userMapper.selectUsersByRole("3"); // 普通教师
        allTeachers.addAll(userMapper.selectUsersByRole("2")); // 其他组长

        Set<Long> teamMemberIds = new HashSet<>();
        teamMemberIds.add(currentLeader.getUserId()); // 首先加入组长自己

        // 3. 筛选出教授了组长负责课程的教师
        List<User> filteredMembers = allTeachers.stream()
                .filter(user -> {
                    // 如果是当前组长自己，跳过（已经在集合里）
                    if (user.getUserId().equals(currentLeader.getUserId())) {
                        return false;
                    }

                    // 教师的教授范围 (teachingClasses) 或其负责的科目 (teacherRank)
                    String teacherCourses = user.getTeacherRank();

                    if (teacherCourses != null) {
                        // 检查该教师的负责科目（通常是组长）是否与本组长的负责科目有交集
                        Set<String> memberCourses = Arrays.stream(teacherCourses.replace("，", ",").split(","))
                                .map(String::trim)
                                .collect(Collectors.toSet());

                        // 如果两个 Set 有交集，则该教师是相关成员
                        memberCourses.retainAll(leaderManagedCourses);
                        return !memberCourses.isEmpty();
                    }

                    // 默认情况下，普通教师不应被筛选进来，除非他们被明确分配为组长负责的课程的老师
                    // 但由于我们没有直接根据 classId 反查教师负责课程的便捷方法，
                    // 且题意要求只根据“负责科目”筛选，所以对于普通教师，我们依赖 Admin/Leader 设置其 teacherRank（尽管通常是空的）。
                    // 为了严格遵循“只显示负责该科目的老师”，我们只检查 teacherRank。
                    return false;
                })
                .collect(Collectors.toList());

        // 4. 将筛选出的成员加入结果列表
        List<Object> result = new ArrayList<>();
        result.add(currentLeader);

        for (User member : filteredMembers) {
            if (teamMemberIds.add(member.getUserId())) {
                result.add(member);
            }
        }

        return result;
    }

    @Override
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
                finalContent = String.format("{\"text\": \"%s\", \"deadline\": \"%s\"}",
                        content != null ? content.replace("\"", "\\\"") : "", deadline);
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
        if (targetUsernames == null || targetUsernames.isEmpty()) {
            // 发送给全体 (这里需要根据业务确定全体的范围，假设是所有用户或特定范围，暂用 MQ 广播)
            System.out.println("暂不支持全员广播，请指定目标");
        } else {
            for (String username : targetUsernames) {
                User u = userMapper.findByUsername(username);
                if (u != null) {
                    Notification n = new Notification();
                    n.setUserId(u.getUserId());
                    n.setType("GENERAL_NOTICE");
                    n.setTitle(title);
                    n.setMessage(content);
                    notificationMapper.insert(n);
                }
            }
        }
    }

    @Override
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
    public void deleteCourse(Long id) {
        courseMapper.deleteCourseById(id);
    }

    @Override
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

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(material.getContent());
            if (root.isObject() && root.has("text")) {
                ((ObjectNode) root).put("deadline", newDeadline);
                String newContent = mapper.writeValueAsString(root);
                materialMapper.updateContent(materialId, newContent);
            } else {
                throw new RuntimeException("资料格式不支持更新截止时间");
            }
        } catch (IOException e) {
            throw new RuntimeException("JSON解析失败");
        }
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
            finalContent = String.format("{\"text\": \"%s\", \"deadline\": \"%s\"}",
                    content != null ? content.replace("\"", "\\\"") : "", deadline);
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
        List<User> allTeachers = new ArrayList<>();
        allTeachers.addAll(userMapper.selectUsersByRole("2"));
        allTeachers.addAll(userMapper.selectUsersByRole("3"));

        for (String name : teacherNames) {
            User u = allTeachers.stream().filter(t -> t.getRealName().equals(name.trim())).findFirst().orElse(null);
            // 组长不更新班级
            if (u != null && !"2".equals(u.getRoleType())) {
                User dbUser = userMapper.findByUsername(u.getUsername());
                Set<String> classes = new HashSet<>();
                if (dbUser.getTeachingClasses() != null) {
                    Collections.addAll(classes, dbUser.getTeachingClasses().split(","));
                }
                boolean changed = false;
                for (Long cid : classIds) {
                    if (classes.add(String.valueOf(cid))) changed = true;
                }
                if (changed) {
                    dbUser.setTeachingClasses(String.join(",", classes));
                    userMapper.updateUser(dbUser);
                }
            }
        }
    }
}