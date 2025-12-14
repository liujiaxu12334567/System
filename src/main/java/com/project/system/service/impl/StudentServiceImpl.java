package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import com.project.system.mq.AnalysisEventPublisher;
import com.project.system.service.StudentService;
import com.project.system.service.support.ClassroomMemoryStore;
import com.project.system.websocket.ClassroomEvent;
import com.project.system.websocket.ClassroomEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StudentServiceImpl implements StudentService {
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private ExamMapper examMapper;
    @Autowired private MaterialMapper materialMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private QuizRecordMapper quizRecordMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private NotificationMapper notificationMapper;
    @Autowired private OnlineQuestionMapper onlineQuestionMapper;
    @Autowired private OnlineAnswerMapper onlineAnswerMapper;
    @Autowired private ClassroomEventPublisher classroomEventPublisher;
    @Autowired private CourseChatMapper courseChatMapper;
    @Autowired private AnalysisEventPublisher analysisEventPublisher;
    @Autowired private ClassroomMemoryStore classroomMemoryStore;
    @Autowired private ClassroomMemoryStore classroomMemoryStore;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String deepSeekApiUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepSeekModel;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 辅助方法：获取当前登录用户
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    /**
     * 获取课程详情
     * 使用 Redis 缓存，key 为 "course_info::ID"
     */
    @Override
    @Cacheable(value = "course_info", key = "#courseId")
    public Course getCourseInfo(Long courseId) {
        // 当 Redis 中没有数据时，会执行此方法查询数据库，并将结果存入 Redis
        List<Course> all = courseMapper.selectAllCourses();
        return all.stream().filter(c -> c.getId().equals(courseId)).findFirst().orElse(null);
    }
    @Override
    public void markNotificationAsRead(Long id) {
        notificationMapper.updateReadStatus(id);
    }
    @Override
    public List<Material> getCourseMaterials(Long courseId) {
        return materialMapper.selectByCourseId(courseId);
    }
    @Override
    public boolean doCheckIn(Long courseId) {
        String batchId = redisTemplate.opsForValue().get("course:checkin:active:" + courseId);
        if (batchId == null) return false; // 签到未开启

        User student = getCurrentUser();
        // 将学生ID加入签到集合 (使用 Set 自动去重)
        redisTemplate.opsForSet().add("course:checkin:list:" + batchId, student.getUserId().toString());
        return true;
    }

    @Override
    public boolean isCheckInActive(Long courseId) {
        return redisTemplate.hasKey("course:checkin:active:" + courseId);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitQuiz(Long materialId, Integer score, String userAnswers, String textAnswer, List<MultipartFile> files) {
        User user = getCurrentUser();
        Material material = materialMapper.findById(materialId);
        String finalContentJson = userAnswers;
        List<String> uploadedPaths = new ArrayList<>();

        // 处理文件上传和文本答案
        if (textAnswer != null || (files != null && !files.isEmpty())) {
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
                        throw new RuntimeException("文件上传失败: " + e.getMessage());
                    }
                }
            }
            // JSON 构造
            try {
                Map<String, Object> answerMap = new HashMap<>();
                answerMap.put("text", textAnswer != null ? textAnswer : "");
                answerMap.put("files", uploadedPaths);
                ObjectMapper mapper = new ObjectMapper();
                finalContentJson = mapper.writeValueAsString(answerMap);
            } catch (Exception e) {
                throw new RuntimeException("数据格式转换失败");
            }
        }

        // 检查是否已提交
        QuizRecord exist = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        if (exist != null) {
            throw new RuntimeException("您已提交过，如需重交请联系老师重置。");
        }

        QuizRecord record = new QuizRecord();
        record.setUserId(user.getUserId());
        record.setMaterialId(materialId);
        record.setScore(score);
        record.setUserAnswers(finalContentJson);
        record.setAiFeedback(null);

        quizRecordMapper.insert(record);

        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", material != null ? material.getCourseId() : null);
        payload.put("materialId", materialId);
        payload.put("studentId", user.getUserId());
        payload.put("type", material != null ? material.getType() : "quiz");
        payload.put("score", score);
        analysisEventPublisher.publish("analysis.assignment.submitted", payload);
    }

    @Override
    public String chatWithAiTutor(Long materialId, List<Map<String, String>> history) {
        User user = getCurrentUser();
        QuizRecord record = quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
        Material material = materialMapper.findById(materialId);

        if (record == null || material == null) {
            throw new RuntimeException("数据异常，无法建立 AI 上下文");
        }

        String userAnswersJson = record.getUserAnswers();
        String homeworkText = "";
        if ("作业".equals(material.getType())) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode node = mapper.readTree(userAnswersJson);
                if (node.has("text")) homeworkText = node.get("text").asText();
            } catch (Exception e) {}
        }

        String reply = callDeepSeekChat(material, userAnswersJson, homeworkText, history);

        // 如果是第一轮对话，保存 AI 的初始反馈
        if (history != null && history.size() == 1) {
            record.setAiFeedback(reply);
            quizRecordMapper.updateAiFeedback(record);
        }

        return reply;
    }

    // 私有方法：调用 AI
    private String callDeepSeekChat(Material material, String quizAnswers, String homeworkText, List<Map<String, String>> history) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            StringBuilder systemPrompt = new StringBuilder();
            systemPrompt.append("你是一位专业的大学助教。以下是当前学生的作业/测验背景信息，请基于此回答学生的问题。\n");
            systemPrompt.append("【题目内容】：\n").append(material.getContent()).append("\n");

            if ("测验".equals(material.getType())) {
                systemPrompt.append("【学生提交的答案索引】：\n").append(quizAnswers).append("\n");
            } else {
                systemPrompt.append("【学生提交的作业内容】：\n").append(homeworkText).append("\n");
            }
            systemPrompt.append("请保持语气亲切自然，可以适当使用Markdown格式（如列表、粗体），以提供更清晰的格式化回复。");

            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", deepSeekModel);
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt.toString());

            if (history != null) {
                for (Map<String, String> msg : history) {
                    messages.addObject().put("role", msg.get("role")).put("content", msg.get("content"));
                }
            }

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

    @Override
    public QuizRecord getQuizRecord(Long materialId) {
        User user = getCurrentUser();
        return quizRecordMapper.findByUserIdAndMaterialId(user.getUserId(), materialId);
    }

    @Override
    public List<Exam> getCourseExams(Long courseId) {
        List<Exam> exams = examMapper.selectExamsByCourseId(courseId);
        User user = getCurrentUser();
        LocalDateTime now = LocalDateTime.now();

        for (Exam exam : exams) {
            // 1. 检查是否已交卷
            ExamRecord record = examMapper.findRecordByUserIdAndExamId(user.getUserId(), exam.getId());
            if (record != null) {
                exam.setStatus("已交卷");
                continue;
            }

            // 2. 动态判断状态
            try {
                LocalDateTime startTime = LocalDateTime.parse(exam.getStartTime(), FORMATTER);
                LocalDateTime deadlineTime = LocalDateTime.parse(exam.getDeadline(), FORMATTER);

                if (now.isBefore(startTime)) {
                    exam.setStatus("未开始");
                } else if (now.isAfter(deadlineTime)) {
                    exam.setStatus("已结束");
                } else {
                    exam.setStatus("进行中");
                }
            } catch (Exception e) {
                System.err.println("Error parsing exam time: " + e.getMessage());
                exam.setStatus("时间异常");
            }
        }
        return exams;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExam(Long examId, Integer score, String userAnswers, Integer cheatCount) {
        User user = getCurrentUser();
        Exam exam = examMapper.findExamById(examId);
        ExamRecord record = new ExamRecord();
        record.setUserId(user.getUserId());
        record.setExamId(examId);
        record.setScore(score);
        record.setUserAnswers(userAnswers);
        record.setCheatCount(cheatCount);

        examMapper.insertExamRecord(record);

        Map<String, Object> payload = new HashMap<>();
        payload.put("courseId", exam != null ? exam.getCourseId() : null);
        payload.put("examId", examId);
        payload.put("studentId", user.getUserId());
        payload.put("score", score);
        payload.put("cheatCount", cheatCount);
        analysisEventPublisher.publish("analysis.exam.submitted", payload);
    }

    @Override
    public Object getExamRecord(Long examId) {
        User user = getCurrentUser();
        ExamRecord record = examMapper.findRecordByUserIdAndExamId(user.getUserId(), examId);
        if (record == null) {
            return examMapper.findExamById(examId); // 返回考试信息供查看
        }
        return record; // 返回提交记录
    }

    @Override
    public List<Map<String, Object>> getRecentActivities() {
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();

        List<Notification> notifications = notificationMapper.selectByUserId(user.getUserId());

        return notifications.stream().map(n -> {
            Map<String, Object> activity = new HashMap<>();
            activity.put("type", n.getType());
            activity.put("title", n.getTitle());
            activity.put("message", n.getMessage());
            activity.put("time", n.getCreateTime().format(FORMATTER));
            activity.put("relatedId", n.getRelatedId());
            activity.put("displayTime", n.getCreateTime().format(FORMATTER));
            return activity;
        }).collect(Collectors.toList());
    }
    @Override
    public List<Notification> getMyNotifications() {
        return notificationMapper.selectByUserId(getCurrentUser().getUserId());
    }

    @Override
    public List<OnlineQuestion> listOnlineQuestions(Long courseId) {
        List<Long> ids = new ArrayList<>();
        if (courseId != null) {
            ids.add(courseId);
        } else {
            ids = courseMapper.selectAllCourses().stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
        }
        if (ids.isEmpty()) return Collections.emptyList();
        java.time.LocalDateTime sessionStart = getSessionStart(courseId, false);
        return onlineQuestionMapper.selectByCourseIds(ids).stream()
                .filter(q -> sessionStart == null || (q.getCreateTime() != null && q.getCreateTime().isAfter(sessionStart)))
                .map(this::enrichQuestion)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OnlineAnswer answerOnlineQuestion(Long questionId, String answerText) {
        User student = getCurrentUser();
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new RuntimeException("回答内容不能为空");
        }
        OnlineQuestion question = onlineQuestionMapper.selectById(questionId);
        if (question == null) {
            throw new RuntimeException("问题不存在");
        }
        // 仅允许当前课堂会话的问题
        java.time.LocalDateTime sessionStart = getSessionStart(question.getCourseId(), false);
        if (sessionStart != null && question.getCreateTime() != null && !question.getCreateTime().isAfter(sessionStart)) {
            throw new RuntimeException("该问题已不在当前课堂会话中");
        }
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());
        answer.setAnswerText(buildAnswerJson("answer", answerText.trim(), "answered", student.getUserId()));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        classroomEventPublisher.publish(new ClassroomEvent("answer", question.getCourseId(), questionId, enriched));
        return enriched;
    }

    @Override
    @Transactional
    public OnlineAnswer handRaise(Long questionId) {
        User student = getCurrentUser();
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());
        answer.setAnswerText(buildAnswerJson("hand", "举手", "pending", student.getUserId()));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        // 推送举手事件
        OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
        if (q != null) classroomEventPublisher.publish(new ClassroomEvent("hand", q.getCourseId(), questionId, enriched));
        return enriched;
    }

    @Override
    @Transactional
    public OnlineAnswer raceAnswer(Long questionId) {
        User student = getCurrentUser();
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());
        answer.setAnswerText(buildAnswerJson("race", "抢答", "pending", student.getUserId()));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
        if (q != null) classroomEventPublisher.publish(new ClassroomEvent("race", q.getCourseId(), questionId, enriched));
        return enriched;
    }

    @Override
    public List<OnlineAnswer> listAnswers(Long questionId) {
        OnlineQuestion q = onlineQuestionMapper.selectById(questionId);
        if (q == null) return Collections.emptyList();
        java.time.LocalDateTime sessionStart = getSessionStart(q.getCourseId(), false);
        return enrichAnswers(onlineAnswerMapper.selectByQuestionId(questionId)).stream()
                .filter(a -> sessionStart == null || (a.getCreateTime() != null && a.getCreateTime().isAfter(sessionStart)))
                .collect(Collectors.toList());
    }

    @Override
    public List<CourseChat> listCourseChat(Long courseId, int limit) {
        return classroomMemoryStore.listChats(courseId, limit <= 0 ? 200 : limit);
    }

    @Override
    public CourseChat sendCourseChat(Long courseId, String content) {
        User student = getCurrentUser();
        CourseChat chat = new CourseChat();
        chat.setCourseId(courseId);
        chat.setSenderId(student.getUserId());
        chat.setSenderName(student.getRealName());
        chat.setRole("student");
        chat.setContent(content);
        CourseChat stored = classroomMemoryStore.appendChat(chat);
        classroomEventPublisher.publish(new ClassroomEvent("chat", courseId, null, stored));
        return stored;
    }

    private java.time.LocalDateTime getSessionStart(Long courseId, boolean createIfAbsent) {
        if (courseId == null) return null;
        String key = "classroom:session:start:" + courseId;
        String val = redisTemplate.opsForValue().get(key);
        if (val != null) {
            try { return java.time.LocalDateTime.parse(val); } catch (Exception ignored) {}
        }
        if (createIfAbsent) {
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            redisTemplate.opsForValue().set(key, now.toString());
            return now;
        }
        return null;
    }

    // 【新增】获取待办任务（逻辑：该学生所在班级的所有作业 - 该学生已提交的作业）
    @Override
    public List<Map<String, Object>> getPendingTasks() {
        User user = getCurrentUser();
        if (user.getClassId() == null) return Collections.emptyList();

        // 1. 获取该班级所有课程
        List<Course> courses = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getClassId() != null && c.getClassId().equals(user.getClassId()))
                .collect(Collectors.toList());

        // 2. 获取这些课程下的所有"作业/测验/项目"资料
        List<Material> allTasks = new ArrayList<>();
        for (Course c : courses) {
            List<Material> materials = materialMapper.selectByCourseId(c.getId());
            materials.stream()
                    .filter(m -> Arrays.asList("作业", "测验", "项目").contains(m.getType()))
                    .forEach(m -> {
                        // 临时借用 filePath 字段存课程名，方便前端显示（或者用 Map 包装）
                        m.setFilePath(c.getName());
                        allTasks.add(m);
                    });
        }

        // 3. 获取学生已提交的记录
        List<QuizRecord> submittedRecords = quizRecordMapper.selectByUserId(user.getUserId());
        Set<Long> submittedMaterialIds = submittedRecords.stream()
                .map(QuizRecord::getMaterialId)
                .collect(Collectors.toSet());

        // 4. 过滤出未提交的任务
        return allTasks.stream()
                .filter(task -> !submittedMaterialIds.contains(task.getId()))
                .map(task -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", task.getId());
                    map.put("courseId", task.getCourseId()); // ★★★ 修复点：添加 courseId ★★★
                    map.put("courseName", task.getFilePath()); // 之前临时存的课程名
                    map.put("title", task.getFileName());
                    map.put("type", task.getType());

                    // 解析截止时间
                    String deadline = "无限制";
                    try {
                        JsonNode node = new ObjectMapper().readTree(task.getContent());
                        if (node.has("deadline")) deadline = node.get("deadline").asText();
                    } catch (Exception e) {}
                    map.put("deadline", deadline);

                    return map;
                })
                .collect(Collectors.toList());
    }

    // ==== 辅助 ====
    private OnlineQuestion enrichQuestion(OnlineQuestion q) {
        if (q == null) return null;
        q.setMode("broadcast");
        q.setDescription(q.getContent());
        if (q.getContent() != null && q.getContent().trim().startsWith("{")) {
            try {
                JsonNode node = new ObjectMapper().readTree(q.getContent());
                if (node.has("mode")) q.setMode(node.get("mode").asText());
                if (node.has("description")) q.setDescription(node.get("description").asText());
            } catch (Exception ignored) {}
        }
        return q;
    }

    private String buildAnswerJson(String type, String text, String state, Long studentId) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("text", text);
        map.put("state", state);
        map.put("studentId", studentId);
        try {
            return new ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return text;
        }
    }

    private OnlineAnswer enrichAnswer(OnlineAnswer a) {
        if (a == null) return null;
        a.setType("answer");
        a.setState("answered");
        a.setText(a.getAnswerText());
        if (a.getAnswerText() != null && a.getAnswerText().trim().startsWith("{")) {
            try {
                JsonNode node = new ObjectMapper().readTree(a.getAnswerText());
                if (node.has("type")) a.setType(node.get("type").asText());
                if (node.has("state")) a.setState(node.get("state").asText());
                if (node.has("text")) a.setText(node.get("text").asText());
            } catch (Exception ignored) {}
        }
        return a;
    }

    private List<OnlineAnswer> enrichAnswers(List<OnlineAnswer> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(this::enrichAnswer).collect(Collectors.toList());
    }
}
