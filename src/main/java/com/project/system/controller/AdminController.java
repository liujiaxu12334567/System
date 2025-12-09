package com.project.system.controller;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.dto.BatchEnrollmentRequest.StudentInfo;
import com.project.system.entity.User;
import com.project.system.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
// Apache POI 核心导入，用于 Excel 文件解析
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import lombok.Data; // 导入 Lombok 的 Data 注解

import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import java.util.Collections; // 导入 Collections

// 【新增】用于返回分页数据的包装类
@Data
class PaginationResponse<T> {
    private List<T> list;
    private long total;
    private int pageNum;
    private int pageSize;

    public PaginationResponse(List<T> list, long total, int pageNum, int pageSize) {
        this.list = list;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }
}

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 1. 【核心修改】获取用户列表 (支持分页和筛选)
    @GetMapping("/user/list")
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {

        // 1. 获取总记录数
        long total = userMapper.countAllUsers(keyword, roleType, classId);

        // 2. 如果没有记录，直接返回空列表
        if (total == 0) {
            return ResponseEntity.ok(new PaginationResponse<>(Collections.emptyList(), 0, pageNum, pageSize));
        }

        // 3. 计算 offset
        int offset = (pageNum - 1) * pageSize;
        if (offset < 0) offset = 0;

        // 4. 获取分页列表
        List<User> list = userMapper.selectAllUsers(keyword, roleType, classId, offset, pageSize);

        // 5. 返回封装好的分页响应
        return ResponseEntity.ok(new PaginationResponse<>(list, total, pageNum, pageSize));
    }


    // 2. 新增用户 (管理员直接添加)
    @PostMapping("/user/add")
    public ResponseEntity<?> addUser(@RequestBody User user) {
        if (userMapper.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("用户名已存在");
        }
        // 默认密码 123456
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            user.setPassword("123456");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userMapper.insert(user);
        return ResponseEntity.ok("添加成功");
    }

    // 3. 更新用户 (核心功能：任命组长/修改密码)
    @PostMapping("/user/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        // 如果修改了密码，需要加密
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null); // 避免覆盖原密码
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
            @RequestParam("startUsername") String startUsername) {

        if (file.isEmpty() || targetClassIdString == null || targetClassIdString.isEmpty() || startUsername == null || startUsername.isEmpty()) {
            return ResponseEntity.badRequest().body("文件、目标班级ID和起始学号都不能为空");
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

            // 仅支持 XLSX 格式
            if (file.getOriginalFilename() != null && file.getOriginalFilename().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(inputStream);
            } else {
                return ResponseEntity.badRequest().body("文件格式错误，目前仅支持 .xlsx 格式文件");
            }

            Sheet sheet = workbook.getSheetAt(0);

            // 遍历所有行，从第二行开始（跳过表头）
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                // 假设真实姓名在第一列 (索引为 0)
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
            // 捕获 POI 解析错误或IO错误
            return ResponseEntity.status(500).body("文件读取或解析失败，请确保文件未被占用且格式正确。错误信息: " + e.getMessage());
        }

        // 【核心逻辑】顺序生成学号并填充 StudentInfo
        for (StudentInfo info : importedNames) {
            // 顺序分配学号
            info.setUsername(String.valueOf(currentId++));
        }

        // 过滤掉那些名字为空的记录
        importedNames.removeIf(info -> info.getUsername() == null);

        // 封装成请求 DTO，调用统一处理方法
        BatchEnrollmentRequest request = new BatchEnrollmentRequest();
        request.setTargetClassId(targetClassId);
        request.setStudentList(importedNames);

        // 调用统一的批量处理方法
        return processBatchEnrollment(request);
    }

    // 7. 统一的批量处理私有方法，供范围和文件导入使用
    private ResponseEntity<?> processBatchEnrollment(BatchEnrollmentRequest request) {
        List<User> usersToInsert = new ArrayList<>();
        String defaultPassword = "123456";
        String encodedPassword = passwordEncoder.encode(defaultPassword);

        Long classId = request.getTargetClassId();
        if (classId == null) {
            return ResponseEntity.badRequest().body("分班失败：请指定目标班级ID");
        }

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
                // 确保学号和姓名都存在（学号在文件上传逻辑中已生成）
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

        // 执行插入操作
        if (!usersToInsert.isEmpty()) {
            int insertedCount = userMapper.insertBatchStudents(usersToInsert);
            return ResponseEntity.ok("成功创建并分配班级给 " + insertedCount + " 个学生账号。默认密码：123456");
        }

        return ResponseEntity.ok("没有新的学生账号需要创建。");
    }
}