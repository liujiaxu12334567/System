package com.project.system.controller;

import com.project.system.entity.User;
import com.project.system.entity.Course;
import com.project.system.entity.Class;
import com.project.system.entity.Material;
import com.project.system.entity.Exam;
import com.project.system.entity.Application; // Added Application import

import com.project.system.mapper.UserMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.ClassMapper;
import com.project.system.mapper.MaterialMapper;
import com.project.system.mapper.ExamMapper;
import com.project.system.mapper.ApplicationMapper; // Added ApplicationMapper import

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/leader")
public class LeaderController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private ExamMapper examMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ApplicationMapper applicationMapper;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    // 1. 发布考试
    @PostMapping("/course/{courseId}/publish-exam")
    public ResponseEntity<?> publishCourseExam(
            @PathVariable Long courseId,
            @RequestBody Map<String, Object> examData)
    {
        String title = (String) examData.get("title");
        String content = (String) examData.get("content");
        String startTime = (String) examData.get("startTime");
        String deadline = (String) examData.get("deadline");
        Integer duration = (Integer) examData.get("duration");
        String status = (String) examData.get("status");

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body("考试标题和试题内容不能为空");
        }
        if (startTime == null || deadline == null) {
            return ResponseEntity.badRequest().body("考试开始时间和截止时间不能为空");
        }

        Exam exam = new Exam();
        exam.setCourseId(courseId);
        exam.setTitle(title);
        exam.setContent(content);
        exam.setStartTime(startTime);
        exam.setDeadline(deadline);
        exam.setDuration(duration != null ? duration : 60);
        exam.setStatus(status != null ? status : "未开始");

        try {
            examMapper.insertExam(exam);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("考试发布失败: " + e.getMessage());
        }

        return ResponseEntity.ok("考试发布成功！");
    }


    /**
     * 2. 获取团队成员
     */
    @GetMapping("/teacher/list")
    public ResponseEntity<?> listMyTeamMembers() {
        // 1. 获取当前登录的课题组长
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentLeader = userMapper.findByUsername(currentUsername);

        if (currentLeader == null) {
            return ResponseEntity.status(403).body("无法获取用户信息");
        }

        System.out.println("====== Leader Team Query: " + currentLeader.getRealName() + " ======");

        // 2. 获取该组长负责的所有课程 (支持双重匹配：ManagerName 或 TeacherRank)
        List<Course> allCourses = courseMapper.selectAllCourses();
        List<Course> myCourses = new ArrayList<>();

        // 策略: 准备 teacherRank 中的课程名集合 (例如: ["java", "C语言"])
        Set<String> rankCourseNames = new HashSet<>();
        if (currentLeader.getTeacherRank() != null && !currentLeader.getTeacherRank().isEmpty()) {
            String[] ranks = currentLeader.getTeacherRank().replace("，", ",").split(",");
            for (String r : ranks) {
                rankCourseNames.add(r.trim());
            }
        }

        for (Course c : allCourses) {
            // 匹配条件 1: 课程表的 manager_name 是我
            boolean isManager = c.getManagerName() != null && c.getManagerName().equals(currentLeader.getRealName());
            // 匹配条件 2: 课程名出现在我的 teacher_rank 里
            boolean isNameMatch = rankCourseNames.contains(c.getName());

            if (isManager || isNameMatch) {
                myCourses.add(c);
            }
        }

        // === 3. 提取目标条件 (教师姓名和班级ID) ===
        Set<String> targetTeacherNames = new HashSet<>();
        Set<String> targetClassIds = new HashSet<>();

        if (!myCourses.isEmpty()) {
            for (Course c : myCourses) {
                // A. 收集课程关联的班级ID
                if (c.getClassId() != null) {
                    targetClassIds.add(String.valueOf(c.getClassId()));
                }

                // B. 收集课程录入的教师姓名 (同时处理中文逗号和英文逗号)
                if (c.getTeacher() != null && !c.getTeacher().isEmpty()) {
                    String cleanedNames = c.getTeacher().replace("，", ",");
                    String[] names = cleanedNames.split(",");
                    for (String name : names) {
                        targetTeacherNames.add(name.trim());
                    }
                }
            }
        }

        System.out.println("Targets - Names: " + targetTeacherNames + ", Classes: " + targetClassIds);

        // 4. 获取所有候选人 (普通教师 + 课题组长)
        List<User> allCandidates = new ArrayList<>();
        allCandidates.addAll(userMapper.selectUsersByRole("3")); // Role 3 = Teacher
        allCandidates.addAll(userMapper.selectUsersByRole("2")); // Role 2 = Leader

        // 5. 开始筛选 (并集逻辑 OR)
        Map<Long, User> teamMap = new HashMap<>();

        // 5.1 必须包含组长自己
        teamMap.put(currentLeader.getUserId(), currentLeader);

        for (User user : allCandidates) {
            if (teamMap.containsKey(user.getUserId())) continue; // 已存在则跳过

            boolean isMatch = false;

            // 规则1：姓名匹配 (Course表里写了这个人的名字)
            if (user.getRealName() != null && targetTeacherNames.contains(user.getRealName())) {
                isMatch = true;
            }

            // 规则2：班级匹配 (User表里这个老师教了组长负责的班级)
            if (!isMatch && user.getTeachingClasses() != null) {
                String[] teachingClasses = user.getTeachingClasses().replace("，", ",").split(",");
                for (String tClassId : teachingClasses) {
                    if (targetClassIds.contains(tClassId.trim())) {
                        isMatch = true;
                        break; // 只要命中一个班级就算匹配
                    }
                }
            }

            if (isMatch) {
                teamMap.put(user.getUserId(), user);
            }
        }

        System.out.println("Total Team Members: " + teamMap.size());
        return ResponseEntity.ok(new ArrayList<>(teamMap.values()));
    }

    // 3. 获取该组长负责的课程列表
    @GetMapping("/course/list")
    public ResponseEntity<?> listMyCourses() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentLeader = userMapper.findByUsername(currentUsername);

        List<Course> allCourses = courseMapper.selectAllCourses();
        List<Course> myCourses = new ArrayList<>();

        Set<String> rankCourseNames = new HashSet<>();
        if (currentLeader.getTeacherRank() != null) {
            String[] ranks = currentLeader.getTeacherRank().replace("，", ",").split(",");
            for (String r : ranks) rankCourseNames.add(r.trim());
        }

        for (Course c : allCourses) {
            boolean isManager = c.getManagerName() != null && c.getManagerName().equals(currentLeader.getRealName());
            boolean isNameMatch = rankCourseNames.contains(c.getName());
            if (isManager || isNameMatch) {
                myCourses.add(c);
            }
        }
        return ResponseEntity.ok(myCourses);
    }

    // 4. 内容下发：发布资料 (Material)
    @PostMapping(value = "/course/{courseId}/upload-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCourseMaterial(
            @PathVariable Long courseId,
            @RequestParam("type") String materialType,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "deadline", required = false) String deadline,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        // 1. 知识图谱和目录的特殊处理 (它们的内容是 JSON 字符串，不需要额外包装)
        if ("知识图谱".equals(materialType) || "目录".equals(materialType)) {
            if (content == null || content.isEmpty()) return ResponseEntity.badRequest().body("内容不能为空");

            Material material = new Material();
            material.setCourseId(courseId);
            material.setType(materialType);
            material.setContent(content);
            material.setFileName(title != null ? title : (materialType + ".json"));
            material.setFilePath("");
            materialMapper.insert(material);
            return ResponseEntity.ok(materialType + "保存成功");
        }

        // 2. 普通资料的处理
        if ((content == null || content.isEmpty()) && (file == null || file.isEmpty())) {
            return ResponseEntity.badRequest().body("请填写内容描述或上传文件。");
        }

        String finalFileName = "无标题资料";
        String filePath = "";

        // --- A. 处理文件保存逻辑 (保存到磁盘) ---
        if (file != null && !file.isEmpty()) {
            try {
                File directory = new File(uploadDir);
                if (!directory.exists()) {
                    directory.mkdirs();
                }
                String originalFilename = file.getOriginalFilename();
                String extension = "";
                if (originalFilename != null && originalFilename.contains(".")) {
                    extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                }
                String uniqueName = UUID.randomUUID().toString() + extension;

                Path path = Paths.get(uploadDir + File.separator + uniqueName);
                Files.write(path, file.getBytes());

                filePath = path.toString();
                finalFileName = (title != null && !title.isEmpty()) ? title : originalFilename;

            } catch (IOException e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("文件上传失败: " + e.getMessage());
            }
        } else {
            if (title != null && !title.isEmpty()) finalFileName = title;
        }

        // --- B. 处理内容 (如果是作业/测验/项目，将截止时间合并到 content) ---
        String finalContent = content;
        if (deadline != null && !deadline.isEmpty()) {
            finalContent = String.format("{\"text\": \"%s\", \"deadline\": \"%s\"}",
                    content != null ? content.replace("\"", "\\\"") : "",
                    deadline
            );
        }

        // --- C. 保存记录到数据库 (sys_material 表) ---
        try {
            Material material = new Material();
            material.setCourseId(courseId);
            material.setType(materialType);
            material.setContent(finalContent);
            material.setFileName(finalFileName);
            material.setFilePath(filePath);

            materialMapper.insert(material);

            System.out.println("资料记录已存入数据库 ID: " + material.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("数据库保存失败: " + e.getMessage());
        }

        return ResponseEntity.ok("资料 [" + materialType + "] 发布成功！");
    }

    // 5. 下发通知
    @PostMapping("/notification/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> data) {
        String title = (String) data.get("title");
        String content = (String) data.get("content");
        List<String> targetUsernames = (List<String>) data.get("targets");

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body("通知标题和内容不能为空");
        }

        System.out.println("【通知下发】");
        System.out.println("标题: " + title);
        System.out.println("内容: " + content);
        System.out.println("目标用户: " + (targetUsernames != null ? targetUsernames : "全体成员"));

        return ResponseEntity.ok("消息通知已成功下发");
    }

    // 6. 更新课程
    @PostMapping("/course/update")
    @Transactional
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        courseMapper.updateCourse(course);
        if (course.getTeacher() != null && course.getClassId() != null) {
            List<String> names = Arrays.asList(course.getTeacher().replace("，", ",").split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());
            updateTeacherTeachingClasses(names, classIds);
        }
        return ResponseEntity.ok("更新成功");
    }

    // 7. 删除课程
    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        courseMapper.deleteCourseById(id);
        return ResponseEntity.ok("删除成功");
    }

    // 8. 批量分配
    @PostMapping("/course/batch-assign")
    @Transactional
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userMapper.findByUsername(currentUsername);
        String name = (String) request.get("name");
        String semester = (String) request.get("semester");
        List<String> teacherNames = (List<String>) request.get("teacherNames");
        List<Object> rawClassIds = (List<Object>) request.get("classIds");

        if (name == null || teacherNames == null || rawClassIds == null) {
            return ResponseEntity.badRequest().body("参数不完整");
        }

        List<Long> classIds = rawClassIds.stream().map(obj -> Long.parseLong(obj.toString())).collect(Collectors.toList());
        String teacherStr = String.join(",", teacherNames);

        List<Course> courses = new ArrayList<>();
        for (Long cid : classIds) {
            checkAndInsertClass(cid);
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

        if(!courses.isEmpty()) courseMapper.insertBatchCourses(courses);
        updateTeacherTeachingClasses(teacherNames, classIds);

        return ResponseEntity.ok("批量分配成功");
    }

    // 9. 课题组长直接延长/修改资料截止时间
    @PostMapping("/material/update-deadline")
    @Transactional
    public ResponseEntity<?> updateMaterialDeadline(@RequestBody Map<String, Object> data) {
        Long materialId = Long.valueOf(data.get("materialId").toString());
        String newDeadline = (String) data.get("newDeadline");

        if (materialId == null || newDeadline == null) {
            return ResponseEntity.badRequest().body("资料ID和新截止时间不能为空");
        }

        Material material = materialMapper.findById(materialId);
        if (material == null) {
            return ResponseEntity.status(404).body("找不到该资料记录");
        }

        String oldContent = material.getContent();
        String newContent = oldContent;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(oldContent);

            if (root.isObject() && root.has("text")) {
                ((ObjectNode)root).put("deadline", newDeadline);
                newContent = mapper.writeValueAsString(root);
            } else {
                return ResponseEntity.badRequest().body("该资料内容格式特殊，无法自动更新截止时间。");
            }
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("资料内容格式错误，无法更新截止时间。");
        }

        int rows = materialMapper.updateContent(materialId, newContent);

        if (rows > 0) {
            return ResponseEntity.ok("资料截止时间已更新为: " + newDeadline);
        } else {
            return ResponseEntity.status(500).body("数据库更新失败");
        }
    }

    // 10. 【新增】获取待审核的延期申请 (课题组长只看延期申请)
    @GetMapping("/applications/pending")
    public ResponseEntity<?> listPendingApplications() {
        // 课题组长只审核 DEADLINE_EXTENSION 申请
        List<Application> allPending = applicationMapper.findByStatus("PENDING");

        List<Application> deadlineApplications = allPending.stream()
                .filter(app -> "DEADLINE_EXTENSION".equals(app.getType()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(deadlineApplications);
    }

    // 11. 【新增】处理审核操作 (课题组长只能处理延期申请)
    @PostMapping("/applications/review")
    @Transactional
    public ResponseEntity<?> reviewApplication(@RequestBody Map<String, Object> request) {
        Long appId = Long.valueOf(request.get("id").toString());
        String status = (String) request.get("status"); // APPROVED 或 REJECTED

        if (appId == null || status == null) {
            return ResponseEntity.badRequest().body("审核参数不完整");
        }

        Application app = applicationMapper.findById(appId);
        if (app == null) {
            return ResponseEntity.badRequest().body("找不到对应的申请记录");
        }

        // 课题组长只处理 DEADLINE_EXTENSION 类型的申请
        if (!"DEADLINE_EXTENSION".equals(app.getType())) {
            return ResponseEntity.status(403).body("无权处理非延期申请类型");
        }

        // 1. 如果是驳回，直接更新状态
        if ("REJECTED".equals(status)) {
            applicationMapper.updateStatus(appId, status);
            return ResponseEntity.ok("资料延期申请已驳回");
        }

        // 2. 如果是批准，执行延期操作
        if ("APPROVED".equals(status)) {
            try {
                // 解析 content: "请求将资料 [xxx] 的截止时间延长至: YYYY-MM-DD HH:mm:ss"
                Long materialId = app.getTargetId();

                if (materialId == null || materialId == 0) {
                    return ResponseEntity.badRequest().body("申请记录缺少资料ID，无法执行延期");
                }

                Material material = materialMapper.findById(materialId);
                if (material == null) {
                    return ResponseEntity.status(404).body("找不到要延期的资料");
                }

                // 从 content 中提取时间
                String content = app.getContent();
                String deadlinePattern = "延长至:\\s*(\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2})";
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(deadlinePattern);
                java.util.regex.Matcher matcher = pattern.matcher(content);

                String newDeadline = null;
                if (matcher.find()) {
                    newDeadline = matcher.group(1);
                } else {
                    return ResponseEntity.badRequest().body("申请内容格式不正确，无法解析新的截止时间");
                }

                // 执行更新 Material deadline 的逻辑
                String oldContent = material.getContent();
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(oldContent);

                if (root.isObject() && root.has("text")) {
                    ((ObjectNode)root).put("deadline", newDeadline);
                    String newMaterialContent = mapper.writeValueAsString(root);
                    materialMapper.updateContent(materialId, newMaterialContent);

                    applicationMapper.updateStatus(appId, status);
                    return ResponseEntity.ok("资料截止时间已成功延长至: " + newDeadline);
                } else {
                    return ResponseEntity.badRequest().body("资料内容格式特殊，无法自动更新截止时间。");
                }

            } catch (Exception e) {
                System.err.println("Error processing deadline extension: " + e.getMessage());
                return ResponseEntity.status(500).body("处理请求时发生系统错误：" + e.getMessage());
            }
        }

        return ResponseEntity.badRequest().body("未知的状态操作");
    }

    // 辅助方法：检查并插入新班级
    private void checkAndInsertClass(Long classId) {
        if (classMapper.findById(classId) == null) {
            classMapper.insert(new Class(classId, classId + "班", "未分配专业"));
        }
    }

    // 辅助方法：更新教师执教班级 (排除组长)
    private void updateTeacherTeachingClasses(List<String> teacherNames, List<Long> classIds) {
        List<User> allUsers = new ArrayList<>();
        allUsers.addAll(userMapper.selectUsersByRole("2"));
        allUsers.addAll(userMapper.selectUsersByRole("3"));

        for (String name : teacherNames) {
            User u = allUsers.stream().filter(user -> user.getRealName().equals(name.trim())).findFirst().orElse(null);

            if (u != null && !"2".equals(u.getRoleType())) {
                User dbUser = userMapper.findByUsername(u.getUsername());
                if(dbUser == null) continue;

                Set<String> classes = new HashSet<>();
                if (dbUser.getTeachingClasses() != null) {
                    Collections.addAll(classes, dbUser.getTeachingClasses().split(","));
                }

                boolean changed = false;
                for(Long cid : classIds) {
                    if(classes.add(String.valueOf(cid))) changed = true;
                }

                if (changed) {
                    User update = new User();
                    update.setUserId(dbUser.getUserId());
                    update.setTeachingClasses(String.join(",", classes));
                    userMapper.updateUser(update);
                }
            }
        }
    }
}