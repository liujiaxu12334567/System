package com.project.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
// 【新增 import: 引入时间 API】
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


@RestController
@RequestMapping("/api/student")
public class StudentController {

    // 【依赖注入】
    @Autowired
    private ExamMapper examMapper;
    @Autowired
    private MaterialMapper materialMapper;
    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private QuizRecordMapper quizRecordMapper;
    @Autowired
    private UserMapper userMapper;

    // 【配置注入】
    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String deepSeekApiUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepSeekModel;

    // 1. 获取课程详情
    @GetMapping("/course/{courseId}/info")
    public ResponseEntity<?> getCourseInfo(@PathVariable Long courseId) {
        List<Course> all = courseMapper.selectAllCourses();
        Course target = all.stream().filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
        if (target == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(target);
    }

    // 2. 获取课程资料列表 (作业/测验/资料)
    @GetMapping("/course/{courseId}/materials")
    public ResponseEntity<?> getCourseMaterials(@PathVariable Long courseId) {
        List<Material> materials = materialMapper.selectByCourseId(courseId);
        return ResponseEntity.ok(materials);
    }

    // 3. 提交测验/作业 (只负责保存，不触发 AI)
    @PostMapping(value = "/quiz/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> submitQuiz(
            @RequestParam("materialId") Long materialId,
            @RequestParam(value = "score", required = false, defaultValue = "0") Integer score,
            @RequestParam(value = "userAnswers", required = false) String userAnswers,
            @RequestParam(value = "textAnswer", required = false) String textAnswer,
            @RequestParam(value = "files", required = false) List<MultipartFile> files
    ) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        String finalContentJson = userAnswers;
        List<String> uploadedPaths = new ArrayList<>();

        if (textAnswer != null || (files != null && !files.isEmpty())) {
            // 文件上传逻辑
            if (files != null) {
                for (MultipartFile file : files) {
                    try {
                        File directory = new File(uploadDir);
                        if (!directory.exists()) directory.mkdirs();
                        String originalFilename = file.getOriginalFilename();
                        String extension = originalFilename != null && originalFilename.contains(".") ? originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
                        String uniqueName = UUID.randomUUID().toString() + extension;
                        Path path = Paths.get(uploadDir + File.separator + uniqueName);
                        Files.write(path, file.getBytes());
                        uploadedPaths.add(uniqueName);
                    } catch (IOException e) {
                        return ResponseEntity.status(500).body("文件上传失败");
                    }
                }
            }
            // JSON 构造 (使用 Jackson)
            try {
                Map<String, Object> answerMap = new HashMap<>();
                answerMap.put("text", textAnswer != null ? textAnswer : "");
                answerMap.put("files", uploadedPaths);
                ObjectMapper mapper = new ObjectMapper();
                finalContentJson = mapper.writeValueAsString(answerMap);
            } catch (Exception e) {
                return ResponseEntity.status(500).body("数据格式转换失败");
            }
        }

        QuizRecord exist = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        if (exist != null) {
            return ResponseEntity.badRequest().body("您已提交过，如需重交请联系老师重置。");
        }

        QuizRecord record = new QuizRecord();
        record.setUserId(user.getUserId());
        record.setMaterialId(materialId);
        record.setScore(score);
        record.setUserAnswers(finalContentJson);
        record.setAiFeedback(null); // 提交时设置为 NULL，等待手动分析

        quizRecordMapper.insert(record);
        return ResponseEntity.ok("提交成功！");
    }

    // 4. 【核心升级】AI 对话接口 (支持历史记录)
    @PostMapping("/quiz/chat")
    public ResponseEntity<?> chatWithAiTutor(@RequestBody Map<String, Object> payload) {
        Long materialId = Long.valueOf(payload.get("materialId").toString());
        // 获取前端传来的聊天历史
        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history");

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        // 查找记录和题目，用于构建上下文
        QuizRecord record = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        Material material = materialMapper.findById(materialId);

        if (record == null || material == null) {
            return ResponseEntity.badRequest().body("数据异常，无法建立 AI 上下文");
        }

        // 准备上下文数据
        String userAnswersJson = record.getUserAnswers();
        String homeworkText = "";
        if ("作业".equals(material.getType())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(userAnswersJson);
                if (node.has("text")) homeworkText = node.get("text").asText();
            } catch (Exception e) {}
        }

        // 调用 AI
        String reply = callDeepSeekChat(material, userAnswersJson, homeworkText, history);

        // 如果是第一轮，将 AI 的初始分析结果保存到 aiFeedback 字段
        // 注意：这里需要判断 history.size() > 0 且最后一个消息角色是 user，以确保是第一次真正的提问
        if (history != null && history.size() == 1) {
            record.setAiFeedback(reply);
            quizRecordMapper.updateAiFeedback(record);
        }

        return ResponseEntity.ok(reply);
    }

    // 5. 调用 DeepSeek API 的私有方法 (Prompt Logic)
    private String callDeepSeekChat(Material material, String quizAnswers, String homeworkText, List<Map<String, String>> history) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            // 构建 System Prompt (背景知识)
            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("你是一位专业的大学助教。以下是当前学生的作业/测验背景信息，请基于此回答学生的问题。\n");
            systemPrompt.append("【题目内容】：\n").append(material.getContent()).append("\n");

            if ("测验".equals(material.getType())) {
                systemPrompt.append("【学生提交的答案索引】：\n").append(quizAnswers).append("\n");
            } else {
                systemPrompt.append("【学生提交的作业内容】：\n").append(homeworkText).append("\n");
            }
            // 【关键】修改：移除纯文本限制，鼓励使用 Markdown 格式提供更清晰的回复
            systemPrompt.append("请保持语气亲切自然，可以适当使用Markdown格式（如列表、粗体），以提供更清晰的格式化回复。");

            // 组装请求体
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", deepSeekModel);
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");
            // 1. 先放 System Prompt
            messages.addObject().put("role", "system").put("content", systemPrompt.toString());

            // 2. 再放历史对话 (包含用户最新的提问)
            if (history != null) {
                for (Map<String, String> msg : history) {
                    messages.addObject().put("role", msg.get("role")).put("content", msg.get("content"));
                }
            }

            // 发送请求
            String jsonBody = mapper.writeValueAsString(requestBody);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deepSeekApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + deepSeekApiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = mapper.readTree(response.body());
                return rootNode.path("choices").get(0).path("message").path("content").asText();
            } else {
                return "AI 思考超时，请稍后再试。";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "AI 服务连接失败。";
        }
    }

    // 6. 获取测验记录
    @GetMapping("/quiz/record/{materialId}")
    public ResponseEntity<?> getQuizRecord(@PathVariable Long materialId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);
        QuizRecord record = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        return record == null ? ResponseEntity.ok(Collections.emptyMap()) : ResponseEntity.ok(record);
    }

    // 7. 【考试模块】获取该课程下的所有考试
    @GetMapping("/course/{courseId}/exams")
    public ResponseEntity<?> getCourseExams(@PathVariable Long courseId) {
        List<Exam> exams = examMapper.selectExamsByCourseId(courseId);
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        // ★★★ 核心修改：动态计算考试状态 ★★★
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        for (Exam exam : exams) {
            // 1. 检查是否已交卷 (优先级最高)
            ExamRecord record = examMapper.findRecordByUserIdAndExamId(user.getUserId(), exam.getId());
            if (record != null) {
                exam.setStatus("已交卷");
                continue;
            }

            // 2. 动态判断状态 (如果未交卷)
            try {
                // Exam 实体中的 startTime 和 deadline (原 endTime) 都是 String 类型，需要解析
                LocalDateTime startTime = LocalDateTime.parse(exam.getStartTime(), formatter);
                LocalDateTime deadlineTime = LocalDateTime.parse(exam.getDeadline(), formatter); // 使用 getDeadline()

                if (now.isBefore(startTime)) {
                    exam.setStatus("未开始");
                } else if (now.isAfter(deadlineTime)) { // 使用 deadlineTime
                    exam.setStatus("已结束");
                } else {
                    exam.setStatus("进行中");
                }
            } catch (Exception e) {
                System.err.println("Error parsing exam time for exam ID " + exam.getId() + ": " + e.getMessage());
                exam.setStatus("时间异常");
            }
            // ★★★ 核心修改结束 ★★★
        }
        return ResponseEntity.ok(exams);
    }

    // 8. 【考试模块】提交考试记录 (包含切屏次数)
    @PostMapping("/exam/submit")
    public ResponseEntity<?> submitExam(
            @RequestBody Map<String, Object> submission)
    {
        Long examId = Long.valueOf(submission.get("examId").toString());
        Integer score = (Integer) submission.getOrDefault("score", 0);
        String userAnswers = (String) submission.get("userAnswers");
        Integer cheatCount = (Integer) submission.getOrDefault("cheatCount", 0);

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        ExamRecord record = new ExamRecord();
        record.setUserId(user.getUserId());
        record.setExamId(examId);
        record.setScore(score);
        record.setUserAnswers(userAnswers);
        record.setCheatCount(cheatCount);

        try {
            examMapper.insertExamRecord(record);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("考试提交失败，可能已提交或数据错误。");
        }
        return ResponseEntity.ok("考试提交成功！切屏次数: " + cheatCount);
    }

    // 9. 【考试模块】获取考试记录详情 (用于检查是否已提交)
    @GetMapping("/exam/record/{examId}")
    public ResponseEntity<?> getExamRecord(@PathVariable Long examId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userMapper.findByUsername(username);

        ExamRecord record = examMapper.findRecordByUserIdAndExamId(user.getUserId(), examId);
        if (record == null) {
            // 如果记录不存在，但学生要看试卷内容，返回考试内容
            Exam exam = examMapper.findExamById(examId);
            return exam == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(exam);
        }
        return ResponseEntity.ok(record);
    }
}