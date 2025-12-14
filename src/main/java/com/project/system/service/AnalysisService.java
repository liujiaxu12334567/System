package com.project.system.service;

import com.project.system.entity.AnalysisResult;
import java.util.Map;

public interface AnalysisService {
    Map<String, Object> getAnalysisByCourse(Long courseId);

    // 【新增】获取特定指标分析结果
    AnalysisResult getLatestAnalysis(Long courseId, String metric);
}