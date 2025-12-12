package com.project.system.service.impl;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.*;
import com.project.system.entity.Class;
import com.project.system.mapper.*;
import com.project.system.service.AdminService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AdminServiceImpl implements AdminService {
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private ClassMapper classMapper;
    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private PasswordEncoder passwordEncoder;

    // ... (前部分 addUser, listUsers, checkAndInsertClass 等方法保持不变，此处省略以节省篇幅，请保留原有的这些方法) ...
    // 您只需替换下面的 batchEnrollFromFile 方法即可

    // 辅助方法：检查并自动创建班级 (保留以确保完整性)
    private void checkAndInsertClass(Long classId, String major) {
        if (classId == null) return;
        if (classMapper.findById(classId) == null) {
            String className = classId + "班";
            String finalMajor = (major != null && !major.trim().isEmpty()) ? major.trim() : "未分配专业";
            classMapper.insert(new Class(classId, className, finalMajor));
        }
    }

    // ... (保留 addUser, updateUser, deleteUser, batchEnroll, listUsers 等原有方法) ...
    // 请确保 batchEnroll 方法存在，因为 batchEnrollFromFile 依赖它

    @Override
    public PaginationResponse<?> listUsers(String keyword, String roleType, String classId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        long total = userMapper.countAllUsers(keyword, roleType, classId);
        if (total == 0) return new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize);
        List<User> list = userMapper.selectAllUsers(keyword, roleType, classId, offset, pageSize);
        return new PaginationResponse<>(list, total, pageNum, pageSize);
    }

    @Override
    @Transactional
    public void addUser(Map<String, Object> userMap) {
        // ... (保持原样)
        String username = (String) userMap.get("username");
        String realName = (String) userMap.get("realName");
        String roleType = String.valueOf(userMap.get("roleType"));
        Long classId = userMap.get("classId") != null ? Long.valueOf(userMap.get("classId").toString()) : null;
        String major = (String) userMap.get("major");
        String teachingClasses = (String) userMap.get("teachingClasses");

        if (userMapper.findByUsername(username) != null) throw new RuntimeException("用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setRealName(realName);
        user.setRoleType(roleType);
        user.setClassId(classId);
        user.setPassword(passwordEncoder.encode("123456"));

        if ("2".equals(roleType)) {
            List<String> managerCourses = (List<String>) userMap.get("managerCourses");
            if (managerCourses != null && !managerCourses.isEmpty()) {
                user.setTeacherRank(String.join(",", managerCourses));
            }
        }
        if ("4".equals(roleType) && classId != null) checkAndInsertClass(classId, major);
        if ("5".equals(roleType) && teachingClasses != null) user.setTeachingClasses(teachingClasses);

        userMapper.insert(user);
    }

    @Override
    public void updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        if ("2".equals(user.getRoleType())) user.setTeachingClasses(null);
        if ("4".equals(user.getRoleType()) && user.getClassId() != null) checkAndInsertClass(user.getClassId(), null);
        userMapper.updateUser(user);
    }

    @Override
    public void deleteUser(Long id) {
        userMapper.deleteUserById(id);
    }

    @Override
    @Transactional
    public String batchEnroll(BatchEnrollmentRequest request) {
        // ... (保持原样)
        List<User> users = new ArrayList<>();
        String pwd = passwordEncoder.encode("123456");
        Long classId = request.getTargetClassId();
        checkAndInsertClass(classId, request.getMajor());

        if (request.getStartUsername() != null && request.getEndUsername() != null) {
            long start = Long.parseLong(request.getStartUsername());
            long end = Long.parseLong(request.getEndUsername());
            for (long i = start; i <= end; i++) {
                if (userMapper.findByUsername(String.valueOf(i)) == null) {
                    User u = new User();
                    u.setUsername(String.valueOf(i));
                    u.setPassword(pwd);
                    u.setRealName("待命名");
                    u.setRoleType("4");
                    u.setClassId(classId);
                    users.add(u);
                }
            }
        } else if (request.getStudentList() != null) {
            for (BatchEnrollmentRequest.StudentInfo info : request.getStudentList()) {
                if (userMapper.findByUsername(info.getUsername()) == null) {
                    User u = new User();
                    u.setUsername(info.getUsername());
                    u.setPassword(pwd);
                    u.setRealName(info.getRealName());
                    u.setRoleType("4");
                    u.setClassId(classId);
                    users.add(u);
                }
            }
        }

        if (!users.isEmpty()) {
            userMapper.insertBatchStudents(users);
            return "批量创建成功，数量: " + users.size();
        }
        return "无新用户创建";
    }

    // 【修改核心】智能识别 Excel 和 CSV，防止格式错误
    @Override
    public String batchEnrollFromFile(MultipartFile file, Long targetClassId, Long startId, String major) {
        List<BatchEnrollmentRequest.StudentInfo> list = new ArrayList<>();
        String filename = file.getOriginalFilename();

        // 尝试解析标志
        boolean parsed = false;
        Exception lastException = null;

        // 策略1：先尝试按 Excel (.xlsx) 解析
        try (InputStream is = file.getInputStream()) {
            Workbook wb = new XSSFWorkbook(is); // 如果是CSV这里会报错
            Sheet sheet = wb.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Cell cell = row.getCell(0);
                if (cell != null) {
                    String name = cell.getStringCellValue().trim();
                    if (!name.isEmpty()) {
                        BatchEnrollmentRequest.StudentInfo info = new BatchEnrollmentRequest.StudentInfo();
                        info.setRealName(name);
                        info.setUsername(String.valueOf(startId++));
                        list.add(info);
                    }
                }
            }
            parsed = true;
        } catch (Exception e) {
            lastException = e; // 记录错误，继续尝试策略2
        }

        // 策略2：如果Excel解析失败，尝试按 CSV 解析
        if (!parsed) {
            try (InputStream is = file.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) { // 注意编码，如果乱码改GBK

                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    if (lineNum == 1) continue; // 跳过表头

                    String name = line.trim();
                    // 处理可能的CSV逗号
                    if (name.contains(",")) {
                        name = name.split(",")[0].trim();
                    }

                    if (!name.isEmpty() && !name.toLowerCase().contains("content_types")) { // 简单的垃圾数据过滤
                        BatchEnrollmentRequest.StudentInfo info = new BatchEnrollmentRequest.StudentInfo();
                        info.setRealName(name);
                        info.setUsername(String.valueOf(startId++));
                        list.add(info);
                    }
                }
                parsed = true;
            } catch (Exception e) {
                // 如果两种都失败，抛出异常
                throw new RuntimeException("文件解析失败，请确保上传的是标准的 .xlsx 或 .csv 文件。错误信息: " + lastException.getMessage());
            }
        }

        if (list.isEmpty()) {
            return "文件解析成功，但未找到有效数据（请检查是否从第二行开始填写姓名）。";
        }

        BatchEnrollmentRequest req = new BatchEnrollmentRequest();
        req.setTargetClassId(targetClassId);
        req.setMajor(major);
        req.setStudentList(list);
        return batchEnroll(req);
    }

    // ... (保留后续方法: listCourses, addCourse, updateCourse, deleteCourse, batchAssignCourse 等) ...
    @Override
    public Object listCourses() { return courseMapper.selectAllCourses(); }
    @Override
    @Transactional
    public void addCourse(Course course) {
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userMapper.findByUsername(username);
        checkAndInsertClass(course.getClassId(), null);
        course.setCode("C" + System.currentTimeMillis() % 10000);
        course.setStatus("进行中");
        course.setColor("blue");
        course.setManagerName(admin.getRealName());
        if (course.getClassId() != null && (course.getResponsibleClassIds() == null || course.getResponsibleClassIds().isEmpty())) {
            course.setResponsibleClassIds(String.valueOf(course.getClassId()));
        }
        courseMapper.insertCourse(course);
    }
    @Override
    @Transactional
    public void batchAssignCourse(String name, String semester, List<String> teacherNames, List<Object> rawClassIds) {
        String username = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        User admin = userMapper.findByUsername(username);
        List<Long> classIds = rawClassIds.stream().map(o -> Long.parseLong(o.toString())).collect(Collectors.toList());
        String teacherStr = String.join(",", teacherNames);
        List<Course> courses = new ArrayList<>();
        for (Long cid : classIds) {
            checkAndInsertClass(cid, null);
            Course c = new Course();
            c.setName(name);
            c.setSemester(semester);
            c.setTeacher(teacherStr);
            c.setCode("C" + System.currentTimeMillis() % 10000 + "-" + cid);
            c.setStatus("进行中");
            c.setColor("blue");
            c.setIsTop(0);
            c.setClassId(cid);
            c.setResponsibleClassIds(String.valueOf(cid));
            c.setManagerName(admin.getRealName());
            courses.add(c);
        }
        if (!courses.isEmpty()) courseMapper.insertBatchCourses(courses);
        // updateTeacherTeachingClasses 逻辑略...
    }
    @Override
    public void updateCourse(Course course) { courseMapper.updateCourse(course); }
    @Override
    public void deleteCourse(Long id) { courseMapper.deleteCourseById(id); }
    @Override
    public Object listClasses() {
        return classMapper.selectAllClasses().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getClassId());
            m.put("name", c.getClassName());
            return m;
        }).collect(Collectors.toList());
    }
    @Override
    public Object listPendingApplications() { return applicationMapper.findByStatus("PENDING"); }
    @Override
    @Transactional
    public void reviewApplication(Long appId, String status) {
        Application app = applicationMapper.findById(appId);
        if (app == null) throw new RuntimeException("申请不存在");
        if ("APPROVED".equals(status)) {
            String type = app.getType();
            Long targetId = app.getTargetId();
            if ("DELETE".equals(type)) userMapper.deleteUserById(targetId);
            else if ("RESET_PWD".equals(type)) {
                User u = new User();
                u.setUserId(targetId);
                u.setPassword(passwordEncoder.encode("123456"));
                userMapper.updateUser(u);
            }
        }
        applicationMapper.updateStatus(appId, status);
    }
    @Override
    public void sendNotificationToUsers(List<Long> userIds, String title, String content) { /*略*/ }
    @Override
    public void sendNotification(String title, String content, String targetType, List<Long> specificUserIds, boolean needReply) {
        String batchId = UUID.randomUUID().toString();
        List<User> targets = new ArrayList<>();
        if ("SPECIFIC".equals(targetType) && specificUserIds != null) {
            userMapper.selectAllUsers(null, null, null, 0, 100000).stream()
                    .filter(u -> specificUserIds.contains(u.getUserId())).forEach(targets::add);
        } else if ("ALL_STUDENTS".equals(targetType)) targets = userMapper.selectUsersByRole("4");
        else if ("ALL_TEACHERS".equals(targetType)) {
            targets.addAll(userMapper.selectUsersByRole("2"));
            targets.addAll(userMapper.selectUsersByRole("3"));
        } else if ("ALL".equals(targetType)) targets = userMapper.selectAllUsers(null, null, null, 0, 100000);

        for (User user : targets) {
            Notification n = new Notification();
            n.setUserId(user.getUserId());
            n.setType("Admin_Notice");
            n.setTitle(title);
            n.setMessage(content);
            n.setIsActionRequired(needReply);
            n.setBatchId(batchId);
            n.setSenderName("管理员");
            notificationMapper.insert(n);
        }
    }
    @Override
    public List<Object> getNotificationHistory() { return new ArrayList<>(notificationMapper.selectSentBatches()); }
    @Override
    public List<Map<String, Object>> getNotificationStats(String batchId) { return notificationMapper.selectStatsByBatchId(batchId); }
}
