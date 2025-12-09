package com.project.system.controller;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.BatchEnrollmentRequest.StudentInfo;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.Application;
import com.project.system.entity.Class;
import com.project.system.entity.Course;
import com.project.system.entity.User;
import com.project.system.mapper.ApplicationMapper;
import com.project.system.mapper.ClassMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private ApplicationMapper applicationMapper;

    // 【辅助方法 1】检查并插入新班级
    private void checkAndInsertClass(Long classId, String major) {
        if (classId == null) return;

        Class existingClass = classMapper.findById(classId);

        if (existingClass == null) {
            String className = String.valueOf(classId) + "班";
            // 对 major 字段进行修剪和安全检查
            String trimmedMajor = (major != null) ? major.trim() : null;
            String finalMajor = (trimmedMajor != null && !trimmedMajor.isEmpty()) ? trimmedMajor : "未分配专业";

            Class newClass = new Class(classId, className, finalMajor);
            classMapper.insert(newClass);
        }
    }

    // 【重载】如果只需要 classId，则使用占位符 major
    private void checkAndInsertClass(Long classId) {
        checkAndInsertClass(classId, null);
    }


    // 1. 获取用户列表 (支持多重筛选)
    @GetMapping("/user/list")
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        int offset = (pageNum - 1) * pageSize;
        long total = userMapper.countAllUsers(keyword, roleType, classId);

        if (total == 0) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        List<User> list = userMapper.selectAllUsers(keyword, roleType, classId, offset, pageSize);
        return ResponseEntity.ok(new PaginationResponse<>(list, total, pageNum, pageSize));
    }


    // 2. 新增用户 (接收 Map 来处理 major 字段和课题组长课程)
    @PostMapping("/user/add")
    public ResponseEntity<?> addUser(@RequestBody Map<String, Object> userMap) {
        String username = (String) userMap.get("username");
        String password = (String) userMap.get("password");
        String realName = (String) userMap.get("realName");
        String roleType = String.valueOf(userMap.get("roleType"));
        Long classId = userMap.get("classId") != null ? Long.valueOf(userMap.get("classId").toString()) : null;
        String major = (String) userMap.get("major"); // 接收前端新增的 major 字段

        if (username == null || realName == null) {
            return ResponseEntity.badRequest().body("账号和姓名不能为空");
        }

        if (userMapper.findByUsername(username) != null) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setRealName(realName);
        user.setRoleType(roleType);
        user.setClassId(classId);

        // 【核心修改：课题组长课程存储在 teacherRank 字段】
        if ("2".equals(roleType)) {
            // 前端将负责的课程名列表放在 managerCourses 字段中 (List<String>)
            List<String> managerCourses = (List<String>) userMap.get("managerCourses");
            if (managerCourses != null && !managerCourses.isEmpty()) {
                user.setTeacherRank(String.join(",", managerCourses)); // 存储课程名列表
            }
        }


        // 密码处理
        if (password == null || password.isEmpty()) {
            password = "123456";
        }
        user.setPassword(passwordEncoder.encode(password));

        // 如果用户是学生且有班级，检查并创建班级记录 (使用前端提供的 major)
        if ("4".equals(roleType) && classId != null) {
            checkAndInsertClass(classId, major); // 传递 major
        }

        userMapper.insert(user);
        return ResponseEntity.ok("添加成功");
    }

    // 3. 更新用户
    @PostMapping("/user/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }

        // 【🚨 核心修复点】: 针对课题组长 (roleType=2)
        if ("2".equals(user.getRoleType())) {
            // 课题组长负责的课程在 teacherRank 字段中。
            // 必须确保 teachingClasses 字段不会被前端意外传递的旧值或空字符串更新。
            // 强制设置为 null，以依赖 MyBatis 的动态 SQL (如果配置正确) 跳过更新该字段。
            user.setTeachingClasses(null);
        }


        // 如果用户是学生且有班级，检查并创建班级记录 (更新操作，使用占位符 major)
        if ("4".equals(user.getRoleType()) && user.getClassId() != null) {
            checkAndInsertClass(user.getClassId());
        }

        userMapper.updateUser(user);
        return ResponseEntity.ok("更新成功");
    }

    // 4. 删除用户
    @PostMapping("/user/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        userMapper.deleteUserById(id);
        return ResponseEntity.ok("删除成功");
    }

    // 5. 批量分班和创建账号功能 (JSON body, for range)
    @PostMapping("/batch/enroll")
    public ResponseEntity<?> batchEnroll(@RequestBody BatchEnrollmentRequest request) {
        return processBatchEnrollment(request);
    }

    // 6. 【文件导入接口】处理文件上传和解析
    @PostMapping("/batch/upload")
    public ResponseEntity<?> batchEnrollFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetClassId") String targetClassIdString,
            @RequestParam("startUsername") String startUsername,
            @RequestParam("major") String major) { // 明确接收 major 参数

        if (file.isEmpty() || targetClassIdString == null || targetClassIdString.isEmpty() || startUsername == null || startUsername.isEmpty() || major == null || major.isEmpty()) {
            return ResponseEntity.badRequest().body("文件、目标班级ID、起始学号和专业都不能为空");
        }

        // --- 转换参数 ---
        Long targetClassId;
        long currentId;
        try {
            targetClassId = Long.parseLong(targetClassIdString);
            currentId = Long.parseLong(startUsername);
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("目标班级ID或起始学号格式错误，必须是数字");
        }

        List<StudentInfo> importedNames = new ArrayList<>();

        // --- 真正的文件解析逻辑 (使用 Apache POI) ---
        try (InputStream inputStream = file.getInputStream()) {
            Workbook workbook = null;

            if (file.getOriginalFilename() != null && file.getOriginalFilename().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                return ResponseEntity.badRequest().body("文件格式错误，目前仅支持 .xlsx 格式文件");
            }

            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell nameCell = row.getCell(0);
                if (nameCell != null) {
                    nameCell.setCellType(CellType.STRING);
                    String realName = nameCell.getStringCellValue().trim();

                    if (!realName.isEmpty()) {
                        StudentInfo info = new StudentInfo();
                        info.setRealName(realName);
                        importedNames.add(info);
                    }
                }
            }

        } catch (Exception e) {
            return ResponseEntity.status(500).body("文件读取或解析失败，请确保文件未被占用且格式正确。错误信息: " + e.getMessage());
        }

        for (StudentInfo info : importedNames) {
            info.setUsername(String.valueOf(currentId++));
        }

        importedNames.removeIf(info -> info.getUsername() == null);

        BatchEnrollmentRequest request = new BatchEnrollmentRequest();
        request.setTargetClassId(targetClassId);
        request.setStudentList(importedNames);
        request.setMajor(major); // 将 major 放入 request DTO

        return processBatchEnrollment(request);
    }

    // 7. 统一的批量处理私有方法 (供 /batch/enroll 和 /batch/upload 调用)
    private ResponseEntity<?> processBatchEnrollment(BatchEnrollmentRequest request) {
        List<User> usersToInsert = new ArrayList<>();
        String defaultPassword = "123456";
        String encodedPassword = passwordEncoder.encode(defaultPassword);

        Long classId = request.getTargetClassId();
        if (classId == null) {
            return ResponseEntity.badRequest().body("分班失败：请指定目标班级ID");
        }

        // 确保班级记录存在，并使用 DTO 中的 major 字段
        checkAndInsertClass(classId, request.getMajor());


        // --- 逻辑 1: 学号范围生成 ---
        if (request.getStartUsername() != null && request.getEndUsername() != null) {
            try {
                long start = Long.parseLong(request.getStartUsername());
                long end = Long.parseLong(request.getEndUsername());

                if (start > end || end - start > 500) {
                    return ResponseEntity.badRequest().body("学号范围不合法或数量过多(最多500)");
                }

                for (long i = start; i <= end; i++) {
                    String username = String.valueOf(i);
                    if (userMapper.findByUsername(username) == null) {
                        User user = new User();
                        user.setUsername(username);
                        user.setPassword(encodedPassword);
                        user.setRealName("待命名学生");
                        user.setRoleType("4");
                        user.setClassId(classId);
                        usersToInsert.add(user);
                    }
                }
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest().body("学号必须是纯数字格式");
            }
        }

        // --- 逻辑 2: 导入列表处理 (包括文件上传后的列表) ---
        else if (request.getStudentList() != null && !request.getStudentList().isEmpty()) {
            for (StudentInfo info : request.getStudentList()) {
                if (info.getUsername() != null && userMapper.findByUsername(info.getUsername()) == null) {
                    User user = new User();
                    user.setUsername(info.getUsername());
                    user.setPassword(encodedPassword);
                    user.setRealName(info.getRealName() != null && !info.getRealName().trim().isEmpty() ? info.getRealName() : "待命名学生");
                    user.setRoleType("4");
                    user.setClassId(classId);
                    usersToInsert.add(user);
                }
            }
        }

        if (!usersToInsert.isEmpty()) {
            int insertedCount = userMapper.insertBatchStudents(usersToInsert);
            return ResponseEntity.ok("成功创建并分配班级给 " + insertedCount + " 个学生账号。默认密码：123456");
        }

        return ResponseEntity.ok("没有新的学生账号需要创建。");
    }


    // --- 课程管理 APIs ---

    // 8. 获取所有课程列表
    @GetMapping("/course/list")
    public ResponseEntity<?> listCourses() {
        return ResponseEntity.ok(courseMapper.selectAllCourses());
    }

    // 9. 发布新课程 (同步更新教师执教班级)
    @PostMapping("/course/add")
    @Transactional
    public ResponseEntity<?> addCourse(@RequestBody Course course) {
        if (course.getClassId() == null) {
            return ResponseEntity.badRequest().body("发布课程必须指定班级ID。");
        }

        // 1. 获取当前 Admin 姓名作为默认 Leader
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userMapper.findByUsername(currentUsername);

        // 1. 确保班级记录存在
        checkAndInsertClass(course.getClassId());

        course.setCode("C" + System.currentTimeMillis() % 10000);
        course.setStatus("进行中");
        course.setColor("blue");
        course.setManagerName(currentUser.getRealName()); // 【新增】Admin 发布时，默认 Admin 为 Manager

        // 2. 插入课程
        courseMapper.insertCourse(course);

        // 3. 如果分配了教师，同步更新其执教班级 (合并模式)
        // 注意：如果教师是课题组长，此方法会跳过对 teachingClasses 的更新
        if (course.getTeacher() != null && !course.getTeacher().isEmpty() && course.getClassId() != null) {
            List<String> teacherNames = Arrays.asList(course.getTeacher().split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());

            updateTeacherTeachingClasses(teacherNames, classIds);
        }

        return ResponseEntity.ok("课程发布成功");
    }

    // 10. 批量分配课程给多个班级和教师 (实现课程复制功能)
    @PostMapping("/course/batch-assign")
    @Transactional
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {

        // 1. 获取当前 Admin 姓名作为默认 Leader
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userMapper.findByUsername(currentUsername);
        String managerName = currentUser.getRealName();

        String name = (String) request.get("name");
        String semester = (String) request.get("semester");
        List<String> teacherNames = (List<String>) request.get("teacherNames");
        List<Object> rawClassIds = (List<Object>) request.get("classIds");

        if (name == null || teacherNames == null || rawClassIds == null || teacherNames.isEmpty() || rawClassIds.isEmpty()) {
            return ResponseEntity.badRequest().body("课程信息、教师或班级列表不完整。");
        }

        // 1. 转换 classIds (确保是 Long)
        List<Long> classIds = rawClassIds.stream()
                .map(obj -> {
                    if (obj instanceof Integer) return ((Integer) obj).longValue();
                    if (obj instanceof String) return Long.parseLong((String) obj);
                    return (Long) obj;
                })
                .collect(Collectors.toList());

        String baseCode = "C" + System.currentTimeMillis() % 10000;
        List<Course> coursesToInsert = new ArrayList<>();
        String teachersString = String.join(",", teacherNames);

        // 2. 创建课程记录 (课程复制)
        for (Long classId : classIds) {
            checkAndInsertClass(classId); // 确保班级记录存在 (使用占位符 major)

            Course newCourse = new Course();
            newCourse.setName(name);
            newCourse.setSemester(semester != null ? semester : "2025-1");
            newCourse.setCode(baseCode + "-" + classId);
            newCourse.setTeacher(teachersString);
            newCourse.setStatus("进行中");
            newCourse.setColor("blue");
            newCourse.setIsTop(0);
            newCourse.setClassId(classId);
            newCourse.setManagerName(managerName); // 【新增】默认 Admin 为 Manager

            coursesToInsert.add(newCourse);
        }

        // 3. 批量插入课程
        if (!coursesToInsert.isEmpty()) {
            courseMapper.insertBatchCourses(coursesToInsert);
        }

        // 4. 更新教师的执教班级 (teachingClasses)
        // 注意：如果教师是课题组长，此方法会跳过对 teachingClasses 的更新
        updateTeacherTeachingClasses(teacherNames, classIds);

        return ResponseEntity.ok("成功为 " + coursesToInsert.size() + " 个班级分配了课程，并更新了相关教师的执教班级。");
    }

    // 11. 更新课程 (包含同步更新教师执教班级)
    @PostMapping("/course/update")
    @Transactional
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        // 1. 更新课程记录
        courseMapper.updateCourse(course);

        // 2. 如果提供了 teacher 和 classId，同步更新教师的执教班级列表
        // 注意：如果教师是课题组长，此方法会跳过对 teachingClasses 的更新
        if (course.getTeacher() != null && course.getClassId() != null) {
            List<String> teacherNames = Arrays.asList(course.getTeacher().split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());

            updateTeacherTeachingClasses(teacherNames, classIds);
        }

        return ResponseEntity.ok("更新成功");
    }

    // 12. 删除课程
    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        // 应该调用 courseMapper 来删除课程记录
        courseMapper.deleteCourseById(id);
        return ResponseEntity.ok("删除成功");
    }

    // 13. 获取所有已建立的班级列表 API
    @GetMapping("/classes")
    public ResponseEntity<?> listClasses() {
        // 从 sys_class 表中获取所有班级数据
        List<Class> classEntities = classMapper.selectAllClasses();

        // 格式化为前端所需的 { id: number, name: string } 格式
        List<Map<String, Object>> formattedClasses = classEntities.stream()
                .map(entity -> {
                    Map<String, Object> classMap = new HashMap<>();
                    classMap.put("id", entity.getClassId());
                    classMap.put("name", entity.getClassName());
                    return classMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(formattedClasses);
    }

    // 14. 【新增】获取所有待审核的教师申请
    @GetMapping("/applications/pending")
    public ResponseEntity<?> listPendingApplications() {
        // 假设 ApplicationMapper 有 findByStatus 方法
        List<Application> pendingList = applicationMapper.findByStatus("PENDING");
        return ResponseEntity.ok(pendingList);
    }

    // 15. 【新增】处理审核操作 (批准或拒绝)
    @PostMapping("/applications/review")
    @Transactional // 确保整个操作要么成功要么失败
    public ResponseEntity<?> reviewApplication(@RequestBody Map<String, Object> request) {
        Long appId = Long.valueOf(request.get("id").toString());
        String status = (String) request.get("status"); // APPROVED 或 REJECTED

        if (appId == null || status == null) {
            return ResponseEntity.badRequest().body("审核参数不完整");
        }

        // 假设 ApplicationMapper 有 findById 方法
        Application app = applicationMapper.findById(appId);
        if (app == null) {
            return ResponseEntity.badRequest().body("找不到对应的申请记录");
        }

        if (!"APPROVED".equals(status)) {
            // 如果是 REJECTED，直接更新状态并返回
            applicationMapper.updateStatus(appId, status);
            return ResponseEntity.ok("申请已拒绝");
        }

        // --- 核心批准逻辑 ---
        String type = app.getType();
        Long targetId = app.getTargetId();

        try {
            switch (type) {
                case "DELETE":
                    if (targetId != null) {
                        userMapper.deleteUserById(targetId);
                        break;
                    }
                    return ResponseEntity.badRequest().body("删除操作缺少目标用户ID");

                case "RESET_PWD":
                    if (targetId != null) {
                        User user = new User();
                        user.setUserId(targetId);
                        user.setPassword(passwordEncoder.encode("123456")); // 重置为默认密码
                        userMapper.updateUser(user);
                        break;
                    }
                    return ResponseEntity.badRequest().body("重置密码操作缺少目标用户ID");

                case "ADD":
                    // 解析新增学生信息: "新增学生：张三 (241010101), 班级ID: 202401"
                    Pattern pattern = Pattern.compile("新增学生：\\s*([^\\s]+)\\s*\\(([^\\)]+)\\),\\s*班级ID:\\s*(\\d+)");
                    Matcher matcher = pattern.matcher(app.getContent());

                    if (matcher.find()) {
                        String realName = matcher.group(1).trim();
                        String username = matcher.group(2).trim();
                        String classIdStr = matcher.group(3).trim();
                        Long classId = Long.parseLong(classIdStr);

                        if (userMapper.findByUsername(username) == null) { // 再次检查是否重复
                            User newUser = new User();
                            newUser.setUsername(username);
                            newUser.setRealName(realName);
                            newUser.setRoleType("4");
                            newUser.setClassId(classId);
                            newUser.setPassword(passwordEncoder.encode("123456"));

                            // 确保班级存在 (这里我们无法获取 Major，所以使用占位符)
                            checkAndInsertClass(classId);
                            userMapper.insert(newUser);
                            break;
                        } else {
                            return ResponseEntity.badRequest().body("用户已存在，无法新增");
                        }
                    }
                    return ResponseEntity.badRequest().body("新增申请内容格式不正确");

                default:
                    return ResponseEntity.badRequest().body("未知的申请类型");
            }

            // 批准成功后，更新申请状态
            applicationMapper.updateStatus(appId, "APPROVED");
            return ResponseEntity.ok("操作已批准并执行成功");

        } catch (Exception e) {
            // 如果执行数据库操作失败，抛出异常以触发事务回滚
            System.err.println("Error processing application " + appId + ": " + e.getMessage());
            return ResponseEntity.status(500).body("处理请求时发生系统错误：" + e.getMessage());
        }
    }


    // 【核心私有方法】封装更新教师执教班级的通用逻辑 (合并模式，防空格和陈旧数据)
    private void updateTeacherTeachingClasses(List<String> teacherNames, List<Long> classIds) {

        // 1. 找到所有老师/组长 (此列表仅用于查找 userId/username)
        List<User> allTeachers = new ArrayList<>();
        allTeachers.addAll(userMapper.selectUsersByRole("2")); // 课题组长
        allTeachers.addAll(userMapper.selectUsersByRole("3")); // 普通教师

        for (String teacherName : teacherNames) {
            // 2. 找到当前老师的缓存信息 (包含 username)
            User cachedTeacher = allTeachers.stream()
                    .filter(t -> teacherName.equals(t.getRealName()))
                    .findFirst()
                    .orElse(null);

            if (cachedTeacher != null) {

                // 修复 Bug 1：只有普通教师 (roleType="3") 才更新 teachingClasses
                // 课题组长 (roleType="2") 负责的课程信息存储在 teacherRank，不更新 teachingClasses
                if ("2".equals(cachedTeacher.getRoleType())) {
                    continue; // 跳过课题组长
                }

                // 3. *** 关键修复：从数据库获取最新的完整记录，确保 teachingClasses 不是陈旧值 ***
                User latestUser = userMapper.findByUsername(cachedTeacher.getUsername());

                if (latestUser == null) continue;

                String currentClasses = latestUser.getTeachingClasses();

                Set<String> classSet = new HashSet<>();
                if (currentClasses != null && !currentClasses.isEmpty()) {
                    Arrays.stream(currentClasses.split(","))
                            .map(String::trim) // 清理空格
                            .filter(s -> !s.isEmpty())
                            .forEach(classSet::add);
                }

                boolean addedNewClass = false;
                for (Long classId : classIds) {
                    String classStr = String.valueOf(classId);
                    // 5. 尝试将新班级ID添加到 Set 中。如果 Set.add 返回 true，说明班级ID是新的。
                    if (classSet.add(classStr)) {
                        addedNewClass = true;
                    }
                }

                // 6. 只有在添加了新班级后才执行更新
                if (addedNewClass) {
                    // 重新构建去重后的班级字符串，并用逗号连接
                    String updatedClasses = String.join(",", classSet);

                    User userUpdate = new User();
                    userUpdate.setUserId(latestUser.getUserId()); // 使用最新的 ID
                    userUpdate.setTeachingClasses(updatedClasses); // 写入合并后的新字符串

                    userMapper.updateUser(userUpdate);
                }
            }
        }
    }
}