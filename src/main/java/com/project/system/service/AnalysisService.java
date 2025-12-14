package com.project.system.service;

import com.project.system.dto.AnalysisResultResponse;
import com.project.system.entity.AnalysisResult;

import java.util.List;
import java.util.Map;

public interface AnalysisService {
    Map<String, Object> getAnalysisByCourse(Long courseId);

    // 【新增】获取特定指标分析结果
    AnalysisResult getLatestAnalysis(Long courseId, String metric);

    List<AnalysisResult> listAnalysis(Long courseId, String metric, int limit);

    AnalysisResultResponse getLatestAnalysisResult(Long courseId, String metric);

    List<AnalysisResultResponse> listAnalysisResults(Long courseId, String metric, int limit);
}
