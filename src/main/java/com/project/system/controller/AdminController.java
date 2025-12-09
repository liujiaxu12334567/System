package com.project.system.controller;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.BatchEnrollmentRequest.StudentInfo;
import com.project.system.dto.PaginationResponse; // 导入 DTO
import com.project.system.entity.Class;
import com.project.system.entity.Course;
import com.project.system.entity.User;
import com.project.system.mapper.ClassMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.Data;

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


    // 2. 新增用户 (接收 Map 来处理 major 字段)
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
    public ResponseEntity<?> addCourse(@RequestBody Course course) {
        if (course.getClassId() == null) {
            return ResponseEntity.badRequest().body("发布课程必须指定班级ID。");
        }

        // 1. 确保班级记录存在
        checkAndInsertClass(course.getClassId());

        course.setCode("C" + System.currentTimeMillis() % 10000);
        course.setStatus("进行中");
        course.setColor("blue");

        // 2. 插入课程
        courseMapper.insertCourse(course);

        // 3. 如果分配了教师，同步更新其执教班级 (合并模式)
        if (course.getTeacher() != null && !course.getTeacher().isEmpty() && course.getClassId() != null) {
            List<String> teacherNames = Arrays.asList(course.getTeacher().split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());

            updateTeacherTeachingClasses(teacherNames, classIds);
        }

        return ResponseEntity.ok("课程发布成功");
    }

    // 10. 批量分配课程给多个班级和教师 (实现课程复制功能)
    @PostMapping("/course/batch-assign")
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {
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

            // 【！！！已修复的错误！！！】：这里之前错误地写成了 new User()
            Course newCourse = new Course();
            newCourse.setName(name);
            newCourse.setSemester(semester != null ? semester : "2025-1");
            newCourse.setCode(baseCode + "-" + classId);
            newCourse.setTeacher(teachersString);
            newCourse.setStatus("进行中");
            newCourse.setColor("blue");
            newCourse.setIsTop(0);
            newCourse.setClassId(classId);

            coursesToInsert.add(newCourse);
        }

        // 3. 批量插入课程
        if (!coursesToInsert.isEmpty()) {
            courseMapper.insertBatchCourses(coursesToInsert);
        }

        // 4. 更新教师的执教班级 (teachingClasses)
        updateTeacherTeachingClasses(teacherNames, classIds);

        return ResponseEntity.ok("成功为 " + coursesToInsert.size() + " 个班级分配了课程，并更新了相关教师的执教班级。");
    }

    // 11. 更新课程 (包含同步更新教师执教班级)
    @PostMapping("/course/update")
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        // 1. 更新课程记录
        courseMapper.updateCourse(course);

        // 2. 如果提供了 teacher 和 classId，同步更新教师的执教班级列表
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
                // 3. *** 关键修复：从数据库获取最新的完整记录，确保 teachingClasses 不是陈旧值 ***
                // 必须使用 username 重新查询，获取最新的 teachingClasses
                User latestTeacher = userMapper.findByUsername(cachedTeacher.getUsername());

                if (latestTeacher == null) continue;

                String currentClasses = latestTeacher.getTeachingClasses();

                // 4. 使用 Set 存储现有班级ID，并清理空格
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
                    userUpdate.setUserId(latestTeacher.getUserId()); // 使用最新的 ID
                    userUpdate.setTeachingClasses(updatedClasses); // 写入合并后的新字符串

                    userMapper.updateUser(userUpdate);
                }
            }
        }
    }
}