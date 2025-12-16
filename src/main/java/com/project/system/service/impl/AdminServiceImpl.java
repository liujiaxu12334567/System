package com.project.system.service.impl;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.CourseAssignRequest;
import com.project.system.dto.CourseBatchAssignRequest;
import com.project.system.dto.CourseGroupCreateRequest;
import com.project.system.dto.CourseGroupUpdateLeaderRequest;
import com.project.system.dto.CourseScheduleBatchUpdateRequest;
import com.project.system.dto.PaginationResponse;
import com.project.system.entity.*;
import com.project.system.entity.Class;
import com.project.system.mapper.*;
import com.project.system.service.AdminService;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AdminServiceImpl implements AdminService {
    @Autowired private UserMapper userMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private CourseGroupMapper courseGroupMapper;
    @Autowired private ClassMapper classMapper;
    @Autowired private ApplicationMapper applicationMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private PasswordEncoder passwordEncoder;

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

    // ... (前部分 addUser, listUsers, checkAndInsertClass 等方法保持不变，此处省略以节省篇幅，请保留原有的这些方法) ...
    // 您只需替换下面的 batchEnrollFromFile 方法即可

    // 辅助方法：检查并自动创建班级 (保留以确保完整性)
    private void checkAndInsertClass(Long classId, String major) {
        if (classId == null) return;
        Class existed = classMapper.findById(classId);
        if (existed == null) {
            String className = classId + "班";
            String finalMajor = (major != null && !major.trim().isEmpty()) ? major.trim() : "未分配专业";
            classMapper.insert(new Class(classId, className, finalMajor));
            return;
        }

        // 历史数据兼容：如果已有班级但专业为空/未分配，且本次传入专业，则补全
        String incoming = major != null ? major.trim() : null;
        if (incoming != null && !incoming.isEmpty()) {
            String current = existed.getMajor() != null ? existed.getMajor().trim() : "";
            if (current.isEmpty() || "未分配专业".equals(current)) {
                classMapper.updateMajor(classId, incoming);
            }
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
        String college = (String) userMap.get("college");

        if (userMapper.findByUsername(username) != null) throw new RuntimeException("用户名已存在");

        User user = new User();
        user.setUsername(username);
        user.setRealName(realName);
        user.setRoleType(roleType);
        user.setClassId(classId);
        user.setPassword(passwordEncoder.encode("123456"));
        // 学院字段：仅对教师相关角色保留（2=组长/3=教师/5=素质教师）
        if ("2".equals(roleType) || "3".equals(roleType) || "5".equals(roleType)) {
            String c = college != null ? college.trim() : null;
            user.setCollege(c == null || c.isEmpty() ? null : c);
        } else {
            user.setCollege(null);
        }

        if ("2".equals(roleType)) {
            List<String> managerCourses = (List<String>) userMap.get("managerCourses");
            if (managerCourses != null && !managerCourses.isEmpty()) {
                user.setTeacherRank(String.join(",", managerCourses));
            }
        }
        if ("4".equals(roleType) && classId != null) checkAndInsertClass(classId, major);
        if ("5".equals(roleType) && teachingClasses != null) {
            user.setTeachingClasses(validateTeachingClasses(teachingClasses));
        }

        userMapper.insert(user);
    }

    @Override
    public void updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }

        // 学院字段：仅对教师相关角色保留（2=组长/3=教师/5=素质教师）
        if (user.getRoleType() == null) {
            user.setCollege(null);
        } else {
            String rt = user.getRoleType().trim();
            boolean teacherRole = "2".equals(rt) || "3".equals(rt) || "5".equals(rt);
            if (!teacherRole) {
                user.setCollege(null);
            } else if (user.getCollege() != null) {
                String c = user.getCollege().trim();
                user.setCollege(c.isEmpty() ? null : c);
            }
        }
        if ("2".equals(user.getRoleType())) {
            user.setTeachingClasses(null);
        } else if ("5".equals(user.getRoleType())) {
            if (user.getTeachingClasses() != null) {
                user.setTeachingClasses(validateTeachingClasses(user.getTeachingClasses()));
            }
        } else {
            user.setTeachingClasses(null);
        }
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
        List<String> names = new ArrayList<>();

        boolean parsed = false;
        Exception excelException = null;

        // 策略1：优先按 Excel 解析（xlsx/xls），CSV/非Excel 会抛异常
        try (InputStream is = file.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);

            DataFormatter formatter = new DataFormatter();
            int nameColIdx = 0;
            boolean hasHeader = false;

            Row firstRow = sheet.getRow(0);
            if (firstRow != null) {
                int last = Math.max(firstRow.getLastCellNum(), 0);
                for (int c = 0; c < last; c++) {
                    String hl = normalizeHeaderToken(formatter.formatCellValue(firstRow.getCell(c)));
                    if (hl.isEmpty()) continue;
                    if (hl.contains("姓名") || hl.contains("name")) {
                        nameColIdx = c;
                        hasHeader = true;
                        break;
                    }
                }
            }

            int startRow = hasHeader ? 1 : 0;
            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                Cell cell = row.getCell(nameColIdx);
                String name = cell == null ? "" : formatter.formatCellValue(cell);
                name = name == null ? "" : name.trim();
                if (name.isEmpty()) continue;
                if (hasHeader && ("姓名".equals(name) || "name".equalsIgnoreCase(name))) continue;
                names.add(name);
            }

            parsed = true;
        } catch (Exception e) {
            excelException = e;
        }

        // 策略2：Excel 解析失败时按 CSV 解析（UTF-8 -> GBK 回退），支持逗号/分号/Tab 分隔与引号
        if (!parsed) {
            Exception last = null;
            Charset[] candidates = new Charset[]{StandardCharsets.UTF_8, Charset.forName("GBK")};
            for (Charset charset : candidates) {
                try {
                    names = readNamesFromCsv(file, charset);
                    if (!names.isEmpty()) {
                        parsed = true;
                        break;
                    }
                } catch (Exception ex) {
                    last = ex;
                }
            }

            if (!parsed) {
                String detail = last != null ? last.getMessage() : (excelException != null ? excelException.getMessage() : "unknown");
                throw new RuntimeException("文件解析失败：请上传标准 .xlsx/.xls/.csv 文件。错误信息：" + detail);
            }
        }

        if (names.isEmpty()) {
            return "文件解析成功，但未找到有效姓名数据（请检查是否有“姓名/name”列，且从第2行开始填写）。";
        }

        // 生成用户名：从 startId 起递增，若遇到已存在账号则自动跳过，避免导入人数因重复学号而减少
        long candidate = startId == null ? 0L : startId;
        Set<String> reserved = new HashSet<>();
        List<BatchEnrollmentRequest.StudentInfo> list = new ArrayList<>(names.size());
        for (String name : names) {
            String username;
            while (true) {
                username = String.valueOf(candidate++);
                if (reserved.contains(username)) continue;
                if (userMapper.findByUsername(username) != null) continue;
                reserved.add(username);
                break;
            }
            BatchEnrollmentRequest.StudentInfo info = new BatchEnrollmentRequest.StudentInfo();
            info.setRealName(name);
            info.setUsername(username);
            list.add(info);
        }

        BatchEnrollmentRequest req = new BatchEnrollmentRequest();
        req.setTargetClassId(targetClassId);
        req.setMajor(major);
        req.setStudentList(list);
        return batchEnroll(req);
    }

    private static String normalizeHeaderToken(String s) {
        if (s == null) return "";
        String trimmed = s.trim();
        if (trimmed.startsWith("\uFEFF")) trimmed = trimmed.substring(1);
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static List<String> readNamesFromCsv(MultipartFile file, Charset charset) throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream is = file.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, charset))) {

            String firstLine = reader.readLine();
            if (firstLine == null) return names;
            firstLine = stripBom(firstLine);

            if (firstLine.toLowerCase(Locale.ROOT).startsWith("sep=")) {
                firstLine = reader.readLine();
                if (firstLine == null) return names;
                firstLine = stripBom(firstLine);
            }

            char delimiter = detectCsvDelimiter(firstLine);
            List<String> firstFields = splitCsvLine(firstLine, delimiter);

            int nameColIdx = 0;
            boolean hasHeader = false;
            for (int i = 0; i < firstFields.size(); i++) {
                String hl = normalizeHeaderToken(firstFields.get(i));
                if (hl.contains("姓名") || hl.contains("name")) {
                    nameColIdx = i;
                    hasHeader = true;
                    break;
                }
            }

            if (!hasHeader) {
                String name = nameColIdx < firstFields.size() ? firstFields.get(nameColIdx) : "";
                name = name == null ? "" : name.trim();
                if (!name.isEmpty()) names.add(name);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                if (trimmed.toLowerCase(Locale.ROOT).contains("content_types")) continue;

                List<String> cols = splitCsvLine(line, delimiter);
                String name = nameColIdx < cols.size() ? cols.get(nameColIdx) : (cols.isEmpty() ? "" : cols.get(0));
                name = name == null ? "" : name.trim();
                if (name.isEmpty()) continue;
                if (hasHeader && ("姓名".equals(name) || "name".equalsIgnoreCase(name))) continue;
                names.add(name);
            }
        }
        return names;
    }

    private static String stripBom(String s) {
        if (s == null) return null;
        if (s.startsWith("\uFEFF")) return s.substring(1);
        return s;
    }

    private static char detectCsvDelimiter(String line) {
        if (line == null) return ',';
        if (line.contains("\t")) return '\t';
        if (line.contains(";")) return ';';
        return ',';
    }

    private static List<String> splitCsvLine(String line, char delimiter) {
        List<String> out = new ArrayList<>();
        if (line == null) return out;

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }

            if (ch == delimiter && !inQuotes) {
                out.add(current.toString());
                current.setLength(0);
                continue;
            }

            current.append(ch);
        }
        out.add(current.toString());
        return out;
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
    public void assignCourse(CourseAssignRequest request) {
        if (request == null) throw new RuntimeException("请求体不能为空");
        CourseBatchAssignRequest wrapper = new CourseBatchAssignRequest();
        wrapper.setGroupId(request.getGroupId());
        wrapper.setMajor(request.getMajor());
        CourseBatchAssignRequest.Item item = new CourseBatchAssignRequest.Item();
        item.setClassId(request.getClassId());
        item.setTeacherId(request.getTeacherId());
        wrapper.setAssignments(Collections.singletonList(item));
        batchAssignCourse(wrapper);
    }

    @Override
    public Object listCourseGroups(String semester) {
        String s = semester == null ? null : semester.trim();
        if (s != null && s.isEmpty()) s = null;
        return courseGroupMapper.selectAll(s);
    }

    @Override
    @Transactional
    public void createCourseGroup(CourseGroupCreateRequest request) {
        if (request == null) throw new RuntimeException("请求体不能为空");
        String name = request.getName() == null ? "" : request.getName().trim();
        String semester = request.getSemester() == null ? "" : request.getSemester().trim();
        if (name.isEmpty()) throw new RuntimeException("课程名称不能为空");
        if (semester.isEmpty()) throw new RuntimeException("学期不能为空");

        CourseGroup existed = courseGroupMapper.selectByNameAndSemester(name, semester);
        if (existed != null) throw new RuntimeException("该课程组已存在：" + name + " / " + semester);

        Long leaderId = request.getLeaderId();
        String leaderName = null;
        if (leaderId != null) {
            User leader = userMapper.selectById(leaderId);
            if (leader == null) throw new RuntimeException("组长不存在，leaderId=" + leaderId);
            if (!"2".equals(leader.getRoleType()) && !"3".equals(leader.getRoleType())) {
                throw new RuntimeException("组长必须是教师/组长（role=3/2），leaderId=" + leaderId);
            }
            leaderName = leader.getRealName();
            promoteToLeaderRoleIfNeeded(leader);
        }

        CourseGroup group = new CourseGroup();
        group.setName(name);
        group.setSemester(semester);
        group.setLeaderId(leaderId);
        group.setLeaderName(leaderName);
        courseGroupMapper.insert(group);
    }

    @Override
    @Transactional
    public void updateCourseGroupLeader(CourseGroupUpdateLeaderRequest request) {
        if (request == null) throw new RuntimeException("请求体不能为空");
        if (request.getGroupId() == null) throw new RuntimeException("groupId不能为空");

        CourseGroup group = courseGroupMapper.selectById(request.getGroupId());
        if (group == null) throw new RuntimeException("课程组不存在，groupId=" + request.getGroupId());

        Long oldLeaderId = group.getLeaderId();
        Long newLeaderId = request.getLeaderId();
        String newLeaderName = null;

        if (newLeaderId != null) {
            User newLeader = userMapper.selectById(newLeaderId);
            if (newLeader == null) throw new RuntimeException("组长不存在，leaderId=" + newLeaderId);
            if (!"2".equals(newLeader.getRoleType()) && !"3".equals(newLeader.getRoleType())) {
                throw new RuntimeException("组长必须是教师/组长（role=3/2），leaderId=" + newLeaderId);
            }
            newLeaderName = newLeader.getRealName();
            promoteToLeaderRoleIfNeeded(newLeader);
        }

        courseGroupMapper.updateLeader(group.getGroupId(), newLeaderId, newLeaderName);
        courseMapper.updateLeaderByGroupId(group.getGroupId(), newLeaderId, newLeaderName);

        if (oldLeaderId != null && (newLeaderId == null || !oldLeaderId.equals(newLeaderId))) {
            maybeDemoteLeaderRole(oldLeaderId);
        }
    }

    @Override
    @Transactional
    public void batchAssignCourse(CourseBatchAssignRequest request) {
        if (request == null) throw new RuntimeException("请求体不能为空");
        if (request.getAssignments() == null || request.getAssignments().isEmpty()) {
            throw new RuntimeException("请至少选择一个班级进行分配");
        }

        String major = request.getMajor() == null ? null : request.getMajor().trim();
        // 兼容旧前端：未传 major 时，若所选班级专业一致则自动推断；否则提示选择专业
        if (major == null || major.isEmpty()) {
            Set<String> majors = new HashSet<>();
            for (CourseBatchAssignRequest.Item item : request.getAssignments()) {
                if (item == null || item.getClassId() == null) continue;
                Class cls = classMapper.findById(item.getClassId());
                if (cls == null) {
                    throw new RuntimeException("班级不存在，无法推断专业，请先选择专业或补全班级信息：classId=" + item.getClassId());
                }
                String cm = cls.getMajor() == null ? "" : cls.getMajor().trim();
                if (cm.isEmpty() || "未分配专业".equals(cm)) {
                    throw new RuntimeException("班级未设置专业，无法推断专业：classId=" + item.getClassId());
                }
                majors.add(cm);
                if (majors.size() > 1) break;
            }
            if (majors.size() != 1) {
                throw new RuntimeException("请选择专业（当前所选班级专业不一致，无法自动推断）");
            }
            major = majors.iterator().next();
        }

        CourseGroup group = resolveCourseGroup(request);
        if (group.getLeaderId() == null) {
            throw new RuntimeException("该课程组尚未设置组长，请先在「课程组管理」中指定组长");
        }
        User leader = userMapper.selectById(group.getLeaderId());
        if (leader == null) {
            throw new RuntimeException("课程组组长用户不存在，leaderId=" + group.getLeaderId());
        }
        String courseName = group.getName();
        String semester = group.getSemester();
        String leaderName = group.getLeaderName() != null ? group.getLeaderName() : leader.getRealName();
        Long groupId = group.getGroupId();

        Map<Long, Long> classTeacherMap = new LinkedHashMap<>();
        Set<Long> teacherIds = new HashSet<>();
        for (CourseBatchAssignRequest.Item item : request.getAssignments()) {
            if (item == null) continue;
            if (item.getClassId() == null) throw new RuntimeException("班级ID不能为空");
            if (item.getTeacherId() == null) throw new RuntimeException("授课老师ID不能为空");
            if (classTeacherMap.containsKey(item.getClassId())) {
                throw new RuntimeException("同一个班级只能指定一个老师，重复班级ID：" + item.getClassId());
            }

            // 校验：只能给所选专业的班级分配
            checkAndInsertClass(item.getClassId(), major);
            Class cls = classMapper.findById(item.getClassId());
            String classMajor = cls != null && cls.getMajor() != null ? cls.getMajor().trim() : "";
            if (classMajor.isEmpty() || "未分配专业".equals(classMajor)) {
                throw new RuntimeException("班级未设置专业，无法按专业分配：classId=" + item.getClassId());
            }
            if (!classMajor.equals(major)) {
                throw new RuntimeException("班级专业不匹配：classId=" + item.getClassId() + "，班级专业=" + classMajor + "，选择专业=" + major);
            }

            classTeacherMap.put(item.getClassId(), item.getTeacherId());
            teacherIds.add(item.getTeacherId());
        }
        if (classTeacherMap.isEmpty()) {
            throw new RuntimeException("请至少选择一个班级进行分配");
        }

        Map<Long, User> teacherMap = new HashMap<>();
        for (Long teacherId : teacherIds) {
            User teacher = userMapper.selectById(teacherId);
            if (teacher == null) {
                throw new RuntimeException("授课老师不存在，teacherId=" + teacherId);
            }
            if (!"2".equals(teacher.getRoleType()) && !"3".equals(teacher.getRoleType())) {
                throw new RuntimeException("授课老师必须是组长/教师（role=2/3），teacherId=" + teacherId);
            }
            teacherMap.put(teacherId, teacher);
        }

        List<String> conflictMessages = new ArrayList<>();
        for (Long classId : classTeacherMap.keySet()) {
            Course existed = courseMapper.selectByGroupIdAndClassId(groupId, classId);
            if (existed == null) continue;

            String className = null;
            Class clazz = classMapper.findById(classId);
            if (clazz != null && clazz.getClassName() != null && !clazz.getClassName().trim().isEmpty()) {
                className = clazz.getClassName().trim();
            }

            String existedTeacher = existed.getTeacher() == null ? "未知" : existed.getTeacher();
            String existedCourseId = existed.getId() == null ? "未知" : existed.getId().toString();

            String classLabel = className == null ? String.valueOf(classId) : (className + "（" + classId + "）");
            conflictMessages.add("班级 " + classLabel + " 已存在课程记录：courseId=" + existedCourseId + "，授课老师=" + existedTeacher);
        }
        if (!conflictMessages.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("批量分配失败：以下班级在【课程名=").append(courseName)
                    .append("，学期=").append(semester)
                    .append("】下已存在分配（同班同科目不支持多名老师）。\n");
            sb.append("请到「课程列表」中修改/删除原记录后再批量分配：\n");
            for (String m : conflictMessages) {
                sb.append("- ").append(m).append("\n");
            }
            throw new RuntimeException(sb.toString().trim());
        }

        for (Map.Entry<Long, User> entry : teacherMap.entrySet()) {
            Long teacherId = entry.getKey();
            List<Long> existingClassIds = courseMapper.selectDistinctClassIdsByTeacherIdAndSemester(teacherId, semester);
            Set<Long> distinct = new HashSet<>(existingClassIds == null ? Collections.emptyList() : existingClassIds);
            for (Map.Entry<Long, Long> ct : classTeacherMap.entrySet()) {
                if (teacherId.equals(ct.getValue())) distinct.add(ct.getKey());
            }
            if (distinct.size() > 4) {
                throw new RuntimeException("老师最多授课4个班级，teacher=" + entry.getValue().getRealName());
            }
        }

        List<Course> courses = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : classTeacherMap.entrySet()) {
            Long classId = entry.getKey();
            Long teacherId = entry.getValue();
            User teacher = teacherMap.get(teacherId);

            checkAndInsertClass(classId, null);

            Course c = new Course();
            c.setName(courseName);
            c.setSemester(semester);
            c.setTeacher(teacher.getRealName());
            c.setTeacherId(teacherId);
            c.setCode("C" + System.currentTimeMillis() % 10000 + "-" + classId);
            c.setStatus("进行中");
            c.setColor("blue");
            c.setIsTop(0);
            c.setClassId(classId);
            c.setResponsibleClassIds(String.valueOf(classId));
            c.setManagerName(leader.getRealName());
            c.setLeaderId(leader.getUserId());
            c.setGroupId(groupId);
            courses.add(c);
        }

        if (!courses.isEmpty()) {
            try {
                courseMapper.insertBatchCourses(courses);
            } catch (DataIntegrityViolationException e) {
                throw new RuntimeException("批量分配失败：检测到重复分配（同班同科目不支持多名老师），请刷新后重试或先删除原记录");
            }
        }
    }

    private CourseGroup resolveCourseGroup(CourseBatchAssignRequest request) {
        if (request.getGroupId() != null) {
            CourseGroup group = courseGroupMapper.selectById(request.getGroupId());
            if (group == null) throw new RuntimeException("课程组不存在，groupId=" + request.getGroupId());
            return group;
        }

        String name = request.getName() == null ? "" : request.getName().trim();
        String semester = request.getSemester() == null ? "" : request.getSemester().trim();
        if (name.isEmpty() || semester.isEmpty()) {
            throw new RuntimeException("请选择已有课程组");
        }
        CourseGroup group = courseGroupMapper.selectByNameAndSemester(name, semester);
        if (group == null) {
            throw new RuntimeException("课程组不存在，请先在「课程组管理」中创建并指定组长");
        }
        return group;
    }

    private void promoteToLeaderRoleIfNeeded(User user) {
        if (user == null || user.getUserId() == null) return;
        if ("2".equals(user.getRoleType())) return;
        if ("3".equals(user.getRoleType())) {
            User patch = new User();
            patch.setUserId(user.getUserId());
            patch.setRoleType("2");
            userMapper.updateUser(patch);
        }
    }

    private void maybeDemoteLeaderRole(Long userId) {
        if (userId == null) return;
        User u = userMapper.selectById(userId);
        if (u == null) return;
        if (!"2".equals(u.getRoleType())) return;
        int stillLeads = courseGroupMapper.countByLeaderId(userId);
        if (stillLeads > 0) return;

        User patch = new User();
        patch.setUserId(userId);
        patch.setRoleType("3");
        userMapper.updateUser(patch);
    }
    @Override
    @Transactional
    public void updateCourse(Course course) {
        if (course == null || course.getId() == null) {
            throw new RuntimeException("课程ID不能为空");
        }

        Course existed = courseMapper.selectCourseById(course.getId());
        if (existed == null) {
            throw new RuntimeException("课程不存在，id=" + course.getId());
        }

        // 严格规则：如需修改教师，必须传 teacherId，由后端回写 teacher 展示名，避免不一致
        // 排课时间校验（周几 + 时间段，可单独更新）
        boolean touchingSchedule = course.getDayOfWeek() != null || course.getStartTime() != null || course.getEndTime() != null;
        if (touchingSchedule) {
            Integer effectiveDow = course.getDayOfWeek() != null ? course.getDayOfWeek() : existed.getDayOfWeek();
            if (effectiveDow == null || effectiveDow < 1 || effectiveDow > 7) {
                throw new RuntimeException("星期几(dayOfWeek)必须是1~7");
            }

            String effectiveStart = course.getStartTime() != null ? course.getStartTime() : existed.getStartTime();
            String effectiveEnd = course.getEndTime() != null ? course.getEndTime() : existed.getEndTime();

            String ns = normalizeTimeString(effectiveStart);
            if (ns == null) throw new RuntimeException("开始时间不能为空");
            LocalTime st = parseCourseTime(ns);

            String ne = normalizeTimeString(effectiveEnd);
            LocalTime et = ne == null ? null : parseCourseTime(ne);
            if (et != null && et.isBefore(st)) {
                throw new RuntimeException("结束时间不能早于开始时间");
            }

            // 仅当请求体传入该字段时才回写（避免误覆盖）
            if (course.getDayOfWeek() != null) course.setDayOfWeek(effectiveDow);
            if (course.getStartTime() != null) course.setStartTime(normalizeTimeString(course.getStartTime()));
            if (course.getEndTime() != null) course.setEndTime(normalizeTimeString(course.getEndTime()));
        }

        if (course.getTeacher() != null && course.getTeacherId() == null) {
            throw new RuntimeException("请使用 teacherId 分配教师（避免 teacher 与 teacher_id 不一致）");
        }

        if (course.getTeacherId() != null) {
            User teacher = userMapper.selectById(course.getTeacherId());
            if (teacher == null) {
                throw new RuntimeException("教师不存在，teacherId=" + course.getTeacherId());
            }
            if (!"2".equals(teacher.getRoleType()) && !"3".equals(teacher.getRoleType())) {
                throw new RuntimeException("授课老师必须是组长/教师（role=2/3），teacherId=" + course.getTeacherId());
            }

            String semester = course.getSemester() != null ? course.getSemester() : existed.getSemester();
            Long classId = course.getClassId() != null ? course.getClassId() : existed.getClassId();
            if (semester != null && classId != null) {
                List<Long> existingClassIds = courseMapper.selectDistinctClassIdsByTeacherIdAndSemester(course.getTeacherId(), semester);
                Set<Long> distinct = new HashSet<>(existingClassIds == null ? Collections.emptyList() : existingClassIds);
                distinct.add(classId);
                if (distinct.size() > 4) {
                    throw new RuntimeException("老师最多授课4个班级，teacher=" + teacher.getRealName());
                }
            }

            course.setTeacher(teacher.getRealName());
        }

        courseMapper.updateCourse(course);
    }
    @Override
    public List<Course> listTimetableByClassId(Long classId) {
        if (classId == null) throw new RuntimeException("classId不能为空");
        Class c = classMapper.findById(classId);
        if (c == null) throw new RuntimeException("班级不存在，classId=" + classId);
        return courseMapper.selectCoursesByClassIdOrderByStartTime(classId);
    }

    @Override
    @Transactional
    public void batchUpdateCourseSchedule(CourseScheduleBatchUpdateRequest request) {
        if (request == null) throw new RuntimeException("请求体不能为空");
        if (request.getClassId() == null) throw new RuntimeException("classId不能为空");
        if (request.getSchedules() == null || request.getSchedules().isEmpty()) {
            throw new RuntimeException("请至少设置一门课的时间");
        }
        Class c = classMapper.findById(request.getClassId());
        if (c == null) throw new RuntimeException("班级不存在，classId=" + request.getClassId());

        for (CourseScheduleBatchUpdateRequest.Item item : request.getSchedules()) {
            if (item == null || item.getCourseId() == null) continue;

            Course existed = courseMapper.selectCourseById(item.getCourseId());
            if (existed == null) throw new RuntimeException("课程不存在，courseId=" + item.getCourseId());
            if (existed.getClassId() == null || !existed.getClassId().equals(request.getClassId())) {
                throw new RuntimeException("课程不属于该班级：courseId=" + item.getCourseId() + "，classId=" + request.getClassId());
            }

            Integer dow = item.getDayOfWeek();
            if (dow == null || dow < 1 || dow > 7) {
                throw new RuntimeException("星期几(dayOfWeek)必须是1~7：courseId=" + item.getCourseId());
            }

            String ns = normalizeTimeString(item.getStartTime());
            if (ns == null) throw new RuntimeException("开始时间不能为空：courseId=" + item.getCourseId());
            LocalTime st = parseCourseTime(ns);

            String ne = normalizeTimeString(item.getEndTime());
            LocalTime et = ne == null ? null : parseCourseTime(ne);
            if (et != null && et.isBefore(st)) {
                throw new RuntimeException("结束时间不能早于开始时间：courseId=" + item.getCourseId());
            }

            courseMapper.updateCourseSchedule(item.getCourseId(), dow, ns, ne);
        }
    }

    @Override
    public void deleteCourse(Long id) { courseMapper.deleteCourseById(id); }
    @Override
    public Object listClasses() {
        return classMapper.selectAllClasses().stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getClassId());
            m.put("name", c.getClassName());
            m.put("major", c.getMajor());
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
    @Override
    public Map<String, Object> getNotificationStatsSummary(String batchId) { return notificationMapper.selectStatsSummaryByBatchId(batchId); }

    private String validateTeachingClasses(String teachingClasses) {
        String[] arr = teachingClasses.split(",");
        List<String> cleaned = new ArrayList<>();
        for (String s : arr) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty()) cleaned.add(t);
        }
        if (cleaned.size() > 4) {
            throw new RuntimeException("素质教师最多分配4个班级");
        }
        return String.join(",", cleaned);
    }
}
