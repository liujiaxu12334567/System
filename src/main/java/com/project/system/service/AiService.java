package com.project.system.service;

import java.util.List;
import java.util.Map;

public interface AiService {
    // 通用对话
    String chat(String message, List<Map<String, String>> history);

    // 写作润色
    String polishText(String content);

    // 代码解释
    String explainCode(String code);

    // 学习规划
    String generatePlan(String goal);
}