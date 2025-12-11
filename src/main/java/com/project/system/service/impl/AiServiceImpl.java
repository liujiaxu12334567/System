package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.project.system.service.AiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration; // 导入 Duration
import java.util.List;
import java.util.Map;

@Service
public class AiServiceImpl implements AiService {

    @Value("${deepseek.api.key:}")
    private String deepSeekApiKey;

    @Value("${deepseek.api.url:https://api.deepseek.com/chat/completions}")
    private String deepSeekApiUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepSeekModel;

    private final ObjectMapper mapper = new ObjectMapper();

    // 【优化】创建带有连接超时的 HttpClient (设置为 5 分钟)
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(300))
            .build();

    // 通用底层调用方法
    private String callAiApi(String systemPrompt, String userMessage, List<Map<String, String>> history) {
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", deepSeekModel);
            requestBody.put("stream", false);

            ArrayNode messages = requestBody.putArray("messages");

            // 1. 设置系统人设
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                messages.addObject().put("role", "system").put("content", systemPrompt);
            }

            // 2. 添加历史上下文
            if (history != null) {
                for (Map<String, String> msg : history) {
                    messages.addObject().put("role", msg.get("role")).put("content", msg.get("content"));
                }
            }

            // 3. 添加当前用户消息
            messages.addObject().put("role", "user").put("content", userMessage);

            String jsonBody = mapper.writeValueAsString(requestBody);

            // 【优化】设置请求读取超时时间为 5 分钟 (300秒)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(deepSeekApiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + deepSeekApiKey)
                    .timeout(Duration.ofSeconds(300))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode rootNode = mapper.readTree(response.body());
                // 安全检查，防止空指针
                if (rootNode.has("choices") && rootNode.get("choices").size() > 0) {
                    return rootNode.path("choices").get(0).path("message").path("content").asText();
                } else {
                    return "AI 返回了空内容，请稍后重试。";
                }
            } else {
                return "AI 服务暂时不可用 (Status: " + response.statusCode() + ") - " + response.body();
            }
        } catch (java.net.http.HttpTimeoutException e) {
            return "AI 思考时间过长，响应超时，请尝试缩短文本内容。";
        } catch (Exception e) {
            e.printStackTrace();
            return "AI 连接失败: " + e.getMessage();
        }
    }

    @Override
    public String chat(String message, List<Map<String, String>> history) {
        String systemPrompt = "你是一位耐心的大学全科助教，可以回答学生的任何学术问题。请用专业且亲切的口吻回答。";
        return callAiApi(systemPrompt, message, history);
    }

    @Override
    public String polishText(String content) {
        // 【优化】精简 Prompt，让 AI 更专注
        String systemPrompt = "你是一位专业的学术编辑。请对用户提供的文本进行润色，使其更加学术化、逻辑清晰。请直接输出润色后的结果，并在最后简要说明修改理由。";
        return callAiApi(systemPrompt, content, null);
    }

    @Override
    public String explainCode(String code) {
        String systemPrompt = "你是一位资深软件工程师。请分析用户提供的代码，解释其功能，并指出潜在的 Bug 或 优化空间。请使用 Markdown 格式输出。";
        return callAiApi(systemPrompt, code, null);
    }

    @Override
    public String generatePlan(String goal) {
        String systemPrompt = "你是一位专业的学业规划师。用户会输入一个学习目标，请为其生成一份详细的、分阶段的学习计划路线图。";
        return callAiApi(systemPrompt, goal, null);
    }
}