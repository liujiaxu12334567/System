package com.project.system.controller;

import com.project.system.entity.Material;
import com.project.system.entity.Course;
import com.project.system.entity.QuizRecord;
import com.project.system.entity.User;
import com.project.system.mapper.MaterialMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.QuizRecordMapper;
import com.project.system.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper; // 【新增】引入Jackson库
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    @Autowired
    private MaterialMapper materialMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private UserMapper userMapper;

    // 读取配置文件中的上传路径
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    // 1. 获取课程详情
    @GetMapping("/course/{courseId}/info")
    public ResponseEntity<?> getCourseInfo(@PathVariable Long courseId) {
        List<Course> all = courseMapper.selectAllCourses();
        Course target = all.stream()
                .filter(c -> c.getId().equals(courseId))
                .findFirst()
                .orElse(null);

        if (target == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(target);
    }

    // 2. 获取该课程下的所有资料
    @GetMapping("/course/{courseId}/materials")
    public ResponseEntity<?> getCourseMaterials(@PathVariable Long courseId) {
        List<Material> materials = materialMapper.selectByCourseId(courseId);
        return ResponseEntity.ok(materials);
    }

    // 3. 提交测验/作业 (核心修改：使用 Jackson 自动生成 JSON)
    @PostMapping(value = "/quiz/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitQuiz(
            @RequestParam("materialId") Long materialId,
            @RequestParam(value = "score", required = false, defaultValue = "0") Integer score,
            @RequestParam(value = "userAnswers", required = false) String userAnswers, // 测验的选项索引(JSON字符串)
            @RequestParam(value = "textAnswer", required = false) String textAnswer,   // 作业的文字内容
            @RequestParam(value = "files", required = false) List<MultipartFile> files // 作业的附件文件
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        // 最终存入数据库的 JSON 字符串
        String finalContentJson = userAnswers;

        // --- 如果是作业提交 (有文字或文件) ---
        if (textAnswer != null || (files != null && !files.isEmpty())) {
            List<String> uploadedPaths = new ArrayList<>();

            // 1. 处理文件保存
            if (files != null) {
                for (MultipartFile file : files) {
                    try {
                        // 确保目录存在
                        File directory = new File(uploadDir);
                        if (!directory.exists()) directory.mkdirs();

                        // 生成唯一文件名
                        String originalFilename = file.getOriginalFilename();
                        String extension = "";
                        if (originalFilename != null && originalFilename.contains(".")) {
                            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
                        }
                        String uniqueName = UUID.randomUUID().toString() + extension;

                        // 保存到磁盘
                        Path path = Paths.get(uploadDir + File.separator + uniqueName);
                        Files.write(path, file.getBytes());

                        // 记录相对路径或文件名，方便前端下载
                        uploadedPaths.add(uniqueName);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return ResponseEntity.status(500).body("文件上传失败");
                    }
                }
            }

            // 2. 【核心修复】使用 Jackson 自动生成 JSON (安全可靠)
            try {
                Map<String, Object> answerMap = new HashMap<>();
                // 放入文本内容 (Jackson 会自动处理引号、换行等特殊字符)
                answerMap.put("text", textAnswer != null ? textAnswer : "");
                // 放入文件列表
                answerMap.put("files", uploadedPaths);

                ObjectMapper mapper = new ObjectMapper();
                finalContentJson = mapper.writeValueAsString(answerMap);

            } catch (Exception e) {
                e.printStackTrace();
                return ResponseEntity.status(500).body("系统错误：数据格式转换失败");
            }
        }

        // 检查是否已提交过
        QuizRecord exist = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        if (exist != null) {
            // 这里我们允许覆盖提交，或者你可以返回错误
            return ResponseEntity.badRequest().body("您已提交过，如需重交请联系老师重置。");
        }

        QuizRecord record = new QuizRecord();
        record.setUserId(user.getUserId());
        record.setMaterialId(materialId);
        record.setScore(score);
        record.setUserAnswers(finalContentJson); // 存入 JSON

        quizRecordMapper.insert(record);
        return ResponseEntity.ok("提交成功");
    }

    // 4. 获取测验记录
    @GetMapping("/quiz/record/{materialId}")
    public ResponseEntity<?> getQuizRecord(@PathVariable Long materialId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        QuizRecord record = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        if (record == null) {
            return ResponseEntity.ok(Collections.emptyMap());
        }
        return ResponseEntity.ok(record);
    }
}