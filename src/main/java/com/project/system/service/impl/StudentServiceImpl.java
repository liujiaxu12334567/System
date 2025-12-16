package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.entity.*;
import com.project.system.mapper.*;
import com.project.system.mq.AnalysisEventPublisher;
import com.project.system.service.StudentService;
import com.project.system.service.support.ClassroomChatStore;
import com.project.system.websocket.ClassroomEvent;
import com.project.system.websocket.ClassroomEventBus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.BadSqlGrammarException;
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
    @Autowired private ClassroomEventBus classroomEventBus;
    @Autowired private AnalysisEventPublisher analysisEventPublisher;
    @Autowired private ClassroomChatStore classroomChatStore;

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String deepSeekApiUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepSeekModel;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static String normalizeAnswer(String s) {
        if (s == null) return "";
        return s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // 辅助方法：获取当前登录用户
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userMapper.findByUsername(username);
    }

    @Override
    @Cacheable(value = "course_info_v2", key = "#courseId")
    public Course getCourseInfo(Long courseId) {
        return courseMapper.selectCourseById(courseId);
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
    public List<Course> getPersonalLearningCourses() {
        // “专业组长下发”的课程：sys_course.leader_id 有值（由课程组长批量分配时写入）
        List<Course> all = courseMapper.selectAllCourses();
        if (all == null || all.isEmpty()) return Collections.emptyList();

        // 为避免同一课程组对多个班级产生重复课程：按 group_id 去重（保留最新一条）
        all.sort((a, b) -> {
            Long ai = a == null ? null : a.getId();
            Long bi = b == null ? null : b.getId();
            if (ai == null && bi == null) return 0;
            if (ai == null) return 1;
            if (bi == null) return -1;
            return Long.compare(bi, ai);
        });

        Map<String, Course> dedup = new LinkedHashMap<>();
        for (Course c : all) {
            if (c == null) continue;
            if (c.getLeaderId() == null) continue;

            Long groupId = c.getGroupId();
            String key = groupId != null ? ("g:" + groupId) : ("c:" + c.getId());
            dedup.putIfAbsent(key, c);
        }
        return new ArrayList<>(dedup.values());
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

    // 【核心修复】不仅检查签到是否开启，还检查学生是否已签
    // 请确保 StudentService 接口中定义了此方法: Map<String, Object> getCheckInStatus(Long courseId);
    // 如果接口定义的是 boolean isCheckInActive(Long courseId)，请去修改接口定义
    @Override
    public Map<String, Object> getCheckInStatus(Long courseId) {
        User student = getCurrentUser();
        String activeKey = "course:checkin:active:" + courseId;
        String batchId = redisTemplate.opsForValue().get(activeKey);

        Map<String, Object> res = new HashMap<>();
        if (batchId != null) {
            res.put("active", true);
            // 检查当前学生是否在 Redis 的已签到 Set 中
            Boolean isMember = redisTemplate.opsForSet().isMember("course:checkin:list:" + batchId, student.getUserId().toString());
            res.put("checked", Boolean.TRUE.equals(isMember));
        } else {
            res.put("active", false);
            res.put("checked", false);
        }
        return res;
    }

    // 保留旧方法以防接口报错，建议在接口中废弃它
    @Override
    public boolean isCheckInActive(Long courseId) {
        return redisTemplate.hasKey("course:checkin:active:" + courseId);
    }

    @Override
    public Map<String, Object> getClassroomStatus(Long courseId) {
        Map<String, Object> res = new HashMap<>();
        if (courseId == null) {
            res.put("active", false);
            res.put("sessionStart", null);
            return res;
        }
        boolean active = Boolean.TRUE.equals(redisTemplate.hasKey("classroom:active:" + courseId));
        java.time.LocalDateTime sessionStart = getSessionStart(courseId, false);
        res.put("active", active);
        res.put("sessionStart", sessionStart == null ? null : sessionStart.toString());
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitQuiz(Long materialId, Integer score, String userAnswers, String textAnswer, List<MultipartFile> files) {
        User user = getCurrentUser();
        Material material = materialMapper.findById(materialId);
        if (material == null) throw new RuntimeException("任务不存在");

        // 截止时间校验（组长下发的测验/作业/项目）
        validateMaterialDeadline(material);
        String finalContentJson = userAnswers;
        List<String> uploadedPaths = new ArrayList<>();

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

    private void validateMaterialDeadline(Material material) {
        if (material == null) return;
        String content = material.getContent();
        if (content == null) return;
        String c = content.trim();
        if (!c.startsWith("{")) return;

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(c);

            String deadline = null;
            if (node.has("deadline") && !node.get("deadline").isNull()) {
                deadline = node.get("deadline").asText();
            }

            // 兼容历史错误：批量下发时把原 JSON 放进 text 里
            if ((deadline == null || deadline.isBlank()) && node.has("text") && node.get("text").isTextual()) {
                String text = node.get("text").asText();
                if (text != null && text.trim().startsWith("{")) {
                    try {
                        JsonNode inner = mapper.readTree(text.trim());
                        if (inner.has("deadline") && !inner.get("deadline").isNull()) {
                            deadline = inner.get("deadline").asText();
                        }
                    } catch (Exception ignored) {}
                }
            }

            if (deadline == null || deadline.isBlank()) return;

            LocalDateTime deadlineTime = LocalDateTime.parse(deadline, FORMATTER);
            if (LocalDateTime.now().isAfter(deadlineTime)) {
                throw new RuntimeException("已超过截止时间，无法提交。");
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignored) {
            // JSON/时间格式异常：不阻塞提交（避免误伤历史数据）
        }
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

        if (history != null && history.size() == 1) {
            record.setAiFeedback(reply);
            quizRecordMapper.updateAiFeedback(record);
        }

        return reply;
    }

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
            ExamRecord record = examMapper.findRecordByUserIdAndExamId(user.getUserId(), exam.getId());
            if (record != null) {
                exam.setStatus("已交卷");
                continue;
            }
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
    public List<Map<String, Object>> getMyExams(Integer limit) {
        User user = getCurrentUser();
        if (user == null || user.getClassId() == null) return Collections.emptyList();

        List<Map<String, Object>> rows = examMapper.selectStudentExamOverview(user.getUserId(), user.getClassId());
        if (rows == null || rows.isEmpty()) return Collections.emptyList();

        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> list = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (row == null) continue;

            Map<String, Object> item = new HashMap<>(row);
            boolean submitted = item.get("recordId") != null;
            item.put("submitted", submitted);
            item.put("answerStatus", submitted ? "已答" : "未答");

            String startTime = item.get("startTime") == null ? null : String.valueOf(item.get("startTime"));
            String deadline = item.get("deadline") == null ? null : String.valueOf(item.get("deadline"));

            String timeStatus = "时间异常";
            try {
                LocalDateTime st = (startTime == null || startTime.isBlank()) ? null : LocalDateTime.parse(startTime.trim(), FORMATTER);
                LocalDateTime dl = (deadline == null || deadline.isBlank()) ? null : LocalDateTime.parse(deadline.trim(), FORMATTER);

                if (st != null && now.isBefore(st)) timeStatus = "未开始";
                else if (dl != null && now.isAfter(dl)) timeStatus = "已结束";
                else timeStatus = "进行中";
            } catch (Exception ignored) {
                timeStatus = "时间异常";
            }
            item.put("timeStatus", timeStatus);

            // 兼容前端：如果已答，则优先标记为已交卷（课程详情页沿用此字段）
            if (submitted) item.put("status", "已交卷");
            else item.put("status", timeStatus);

            list.add(item);
        }

        if (limit != null && limit > 0 && list.size() > limit) {
            return list.subList(0, limit);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExam(Long examId, Integer score, String userAnswers, Integer cheatCount) {
        User user = getCurrentUser();
        Exam exam = examMapper.findExamById(examId);
        if (exam == null) throw new RuntimeException("考试不存在");

        ExamRecord exist = examMapper.findRecordByUserIdAndExamId(user.getUserId(), examId);
        if (exist != null) throw new RuntimeException("您已交卷，无需重复提交。");

        // 时间校验：未开始/已结束都禁止提交
        try {
            if (exam.getStartTime() != null && !exam.getStartTime().isBlank()) {
                LocalDateTime startTime = LocalDateTime.parse(exam.getStartTime(), FORMATTER);
                if (LocalDateTime.now().isBefore(startTime)) {
                    throw new RuntimeException("考试未开始，无法提交。");
                }
            }
            if (exam.getDeadline() != null && !exam.getDeadline().isBlank()) {
                LocalDateTime deadlineTime = LocalDateTime.parse(exam.getDeadline(), FORMATTER);
                if (LocalDateTime.now().isAfter(deadlineTime)) {
                    throw new RuntimeException("考试已结束，无法提交。");
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception ignored) {
            // 时间格式异常：不阻塞提交（避免误伤历史数据）
        }

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
            return examMapper.findExamById(examId);
        }
        return record;
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
        User user = getCurrentUser();
        if (user == null) return Collections.emptyList();

        List<Long> ids = new ArrayList<>();
        if (courseId != null) {
            ids.add(courseId);
        } else {
            ids = courseMapper.selectAllCourses().stream()
                    .map(Course::getId)
                    .collect(Collectors.toList());
        }
        if (ids.isEmpty()) return Collections.emptyList();
        java.time.LocalDateTime sessionStart = courseId == null ? null : getSessionStart(courseId, true);
        List<OnlineQuestion> questions;
        try {
            questions = onlineQuestionMapper.selectByCourseIds(ids);
        } catch (BadSqlGrammarException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("correct_answer")) {
                throw new IllegalStateException("数据库缺少 online_question.correct_answer 列：请执行 docs/online_question_correct_answer.sql 后重试。", e);
            }
            throw e;
        }
        return questions.stream()
                .filter(q -> sessionStart == null || (q.getCreateTime() != null && q.getCreateTime().isAfter(sessionStart)))
                .map(q -> {
                    OnlineQuestion enriched = enrichQuestion(q);
                    if (enriched != null) enriched.setCorrectAnswer(null);
                    return enriched;
                })
                .filter(q -> !"assign".equals(q.getMode()) || (q.getAssignStudentId() != null && q.getAssignStudentId().equals(user.getUserId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OnlineAnswer answerOnlineQuestion(Long questionId, String answerText) {
        User student = getCurrentUser();
        if (answerText == null || answerText.trim().isEmpty()) {
            throw new RuntimeException("回答内容不能为空");
        }
        OnlineQuestion question = enrichQuestion(onlineQuestionMapper.selectById(questionId));
        if (question == null) {
            throw new RuntimeException("问题不存在");
        }
        java.time.LocalDateTime sessionStart = getSessionStart(question.getCourseId(), false);
        if (sessionStart != null && question.getCreateTime() != null && !question.getCreateTime().isAfter(sessionStart)) {
            throw new RuntimeException("该问题已不在当前课堂会话中");
        }
        if ("assign".equals(question.getMode()) && (question.getAssignStudentId() == null || !question.getAssignStudentId().equals(student.getUserId()))) {
            throw new RuntimeException("该题为点名题，当前学生无权回答");
        }
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());

        Boolean correct = null;
        if (question.getCorrectAnswer() != null && !question.getCorrectAnswer().trim().isEmpty()) {
            String expected = normalizeAnswer(question.getCorrectAnswer());
            String actual = normalizeAnswer(answerText);
            correct = expected.equals(actual);
        }

        answer.setAnswerText(buildAnswerJson("answer", answerText.trim(), "answered", student.getUserId(), correct));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        classroomEventBus.publish(new ClassroomEvent("answer", question.getCourseId(), questionId, enriched));
        return enriched;
    }

    @Override
    @Transactional
    public OnlineAnswer handRaise(Long questionId) {
        User student = getCurrentUser();
        OnlineQuestion q = enrichQuestion(onlineQuestionMapper.selectById(questionId));
        if (q == null) throw new RuntimeException("问题不存在");
        java.time.LocalDateTime sessionStart = getSessionStart(q.getCourseId(), false);
        if (sessionStart != null && q.getCreateTime() != null && !q.getCreateTime().isAfter(sessionStart)) {
            throw new RuntimeException("该问题已不在当前课堂会话中");
        }
        if (!"hand".equals(q.getMode())) {
            throw new RuntimeException("该问题不是举手模式");
        }
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());
        answer.setAnswerText(buildAnswerJson("hand", "举手", "pending", student.getUserId(), null));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        if (q != null) classroomEventBus.publish(new ClassroomEvent("hand", q.getCourseId(), questionId, enriched));
        return enriched;
    }

    @Override
    @Transactional
    public OnlineAnswer raceAnswer(Long questionId) {
        User student = getCurrentUser();
        OnlineQuestion q = enrichQuestion(onlineQuestionMapper.selectById(questionId));
        if (q == null) throw new RuntimeException("问题不存在");
        java.time.LocalDateTime sessionStart = getSessionStart(q.getCourseId(), false);
        if (sessionStart != null && q.getCreateTime() != null && !q.getCreateTime().isAfter(sessionStart)) {
            throw new RuntimeException("该问题已不在当前课堂会话中");
        }
        if (!"race".equals(q.getMode())) {
            throw new RuntimeException("该问题不是抢答模式");
        }
        OnlineAnswer answer = new OnlineAnswer();
        answer.setQuestionId(questionId);
        answer.setStudentId(student.getUserId());
        answer.setAnswerText(buildAnswerJson("race", "抢答", "pending", student.getUserId(), null));
        onlineAnswerMapper.insert(answer);
        OnlineAnswer enriched = enrichAnswer(answer);
        if (q != null) classroomEventBus.publish(new ClassroomEvent("race", q.getCourseId(), questionId, enriched));
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
        java.time.LocalDateTime sessionStart = courseId == null ? null : getSessionStart(courseId, true);
        return classroomChatStore.list(courseId, limit <= 0 ? 200 : limit).stream()
                .filter(c -> sessionStart == null || c.getCreateTime() == null || c.getCreateTime().isAfter(sessionStart))
                .collect(Collectors.toList());
    }

    @Override
    public CourseChat sendCourseChat(Long courseId, String content) {
        User student = getCurrentUser();
        getSessionStart(courseId, true);
        Long chatId = redisTemplate.opsForValue().increment("classroom:chat:seq:" + courseId);
        CourseChat chat = new CourseChat();
        chat.setId(chatId);
        chat.setCourseId(courseId);
        chat.setSenderId(student.getUserId());
        chat.setSenderName(student.getRealName());
        chat.setRole("student");
        chat.setContent(content);
        chat.setCreateTime(LocalDateTime.now());
        classroomEventBus.publish(new ClassroomEvent("chat", courseId, null, chat));
        return chat;
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

    @Override
    public List<Map<String, Object>> getPendingTasks() {
        User user = getCurrentUser();
        if (user.getClassId() == null) return Collections.emptyList();

        List<Course> courses = courseMapper.selectAllCourses().stream()
                .filter(c -> c.getClassId() != null && c.getClassId().equals(user.getClassId()))
                .collect(Collectors.toList());

        List<Material> allTasks = new ArrayList<>();
        for (Course c : courses) {
            List<Material> materials = materialMapper.selectByCourseId(c.getId());
            materials.stream()
                    .filter(m -> Arrays.asList("作业", "测验", "项目").contains(m.getType()))
                    .forEach(m -> {
                        m.setFilePath(c.getName());
                        allTasks.add(m);
                    });
        }

        List<QuizRecord> submittedRecords = quizRecordMapper.selectByUserId(user.getUserId());
        Set<Long> submittedMaterialIds = submittedRecords.stream()
                .map(QuizRecord::getMaterialId)
                .collect(Collectors.toSet());

        return allTasks.stream()
                .filter(task -> !submittedMaterialIds.contains(task.getId()))
                .map(task -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", task.getId());
                    map.put("courseId", task.getCourseId());
                    map.put("courseName", task.getFilePath());
                    map.put("title", task.getFileName());
                    map.put("type", task.getType());

                    String deadline = "无限制";
                    boolean expired = false;
                    try {
                        JsonNode node = new ObjectMapper().readTree(task.getContent());
                        if (node.has("deadline") && !node.get("deadline").isNull()) {
                            String d = node.get("deadline").asText();
                            if (d != null && !d.isBlank()) deadline = d;
                        }
                        if (deadline != null && !deadline.isBlank() && !"无限制".equals(deadline)) {
                            try {
                                LocalDateTime deadlineTime = LocalDateTime.parse(deadline.trim(), FORMATTER);
                                expired = LocalDateTime.now().isAfter(deadlineTime);
                            } catch (Exception ignored) {
                                expired = false;
                            }
                        }
                    } catch (Exception e) {}
                    map.put("deadline", deadline);
                    map.put("expired", expired);

                    return map;
                })
                .collect(Collectors.toList());
    }

    private OnlineQuestion enrichQuestion(OnlineQuestion q) {
        if (q == null) return null;
        q.setMode("broadcast");
        q.setDescription(q.getContent());
        if (q.getContent() != null && q.getContent().trim().startsWith("{")) {
            try {
                JsonNode node = new ObjectMapper().readTree(q.getContent());
                if (node.has("mode")) q.setMode(node.get("mode").asText());
                if (node.has("description")) q.setDescription(node.get("description").asText());
                if (node.has("assignStudentId") && !node.get("assignStudentId").isNull()) {
                    q.setAssignStudentId(node.get("assignStudentId").asLong());
                }
            } catch (Exception ignored) {}
        }
        return q;
    }

    private String buildAnswerJson(String type, String text, String state, Long studentId, Boolean correct) {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("text", text);
        map.put("state", state);
        map.put("studentId", studentId);
        if (correct != null) {
            map.put("correct", correct);
        }
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
        a.setCorrect(null);
        if (a.getAnswerText() != null && a.getAnswerText().trim().startsWith("{")) {
            try {
                JsonNode node = new ObjectMapper().readTree(a.getAnswerText());
                if (node.has("type")) a.setType(node.get("type").asText());
                if (node.has("state")) a.setState(node.get("state").asText());
                if (node.has("text")) a.setText(node.get("text").asText());
                if (node.has("correct") && !node.get("correct").isNull()) a.setCorrect(node.get("correct").asBoolean());
            } catch (Exception ignored) {}
        }
        return a;
    }

    private List<OnlineAnswer> enrichAnswers(List<OnlineAnswer> list) {
        if (list == null) return Collections.emptyList();
        return list.stream().map(this::enrichAnswer).collect(Collectors.toList());
    }
}
