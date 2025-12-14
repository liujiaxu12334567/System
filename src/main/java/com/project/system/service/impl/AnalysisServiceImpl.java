package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import com.project.system.dto.AnalysisResultResponse;
import com.project.system.entity.AnalysisResult;
import com.project.system.mapper.AnalysisResultMapper;
import com.project.system.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalysisServiceImpl implements AnalysisService {

    @Autowired
    private AnalysisResultMapper analysisResultMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getAnalysisByCourse(Long courseId) {
        List<AnalysisResult> list = analysisResultMapper.selectByCourseId(courseId);
        Map<String, Object> map = new HashMap<>();

        for (AnalysisResult ar : list) {
            Object value = ar.getValueJson();
            try {
                JsonNode node = objectMapper.readTree(ar.getValueJson());
                value = node;
            } catch (Exception ignored) {
            }
            map.put(ar.getMetric(), value);
        }
        return map;
    }
    @Override
    public AnalysisResult getLatestAnalysis(Long courseId, String metric) {
        return analysisResultMapper.selectLatestByCourseIdAndMetric(courseId, metric);
    }

    @Override
    public List<AnalysisResult> listAnalysis(Long courseId, String metric, int limit) {
        int safeLimit = limit <= 0 ? 20 : Math.min(limit, 200);
        return analysisResultMapper.selectByCourseIdAndMetricLimit(courseId, metric, safeLimit);
    }

    @Override
    public AnalysisResultResponse getLatestAnalysisResult(Long courseId, String metric) {
        return toResponse(analysisResultMapper.selectLatestByCourseIdAndMetric(courseId, metric));
    }

    @Override
    public List<AnalysisResultResponse> listAnalysisResults(Long courseId, String metric, int limit) {
        List<AnalysisResult> list = listAnalysis(courseId, metric, limit);
        List<AnalysisResultResponse> result = new ArrayList<>();
        for (AnalysisResult ar : list) {
            AnalysisResultResponse r = toResponse(ar);
            if (r != null) result.add(r);
        }
        return result;
    }

    private AnalysisResultResponse toResponse(AnalysisResult ar) {
        if (ar == null) return null;
        AnalysisResultResponse r = new AnalysisResultResponse();
        r.setId(ar.getId());
        r.setCourseId(ar.getCourseId());
        r.setMetric(ar.getMetric());
        r.setValueJson(ar.getValueJson());
        r.setGeneratedAt(ar.getGeneratedAt());
        r.setEventId(ar.getEventId());

        if (ar.getValueJson() == null) {
            r.setValue(null);
            return r;
        }
        try {
            r.setValue(objectMapper.readTree(ar.getValueJson()));
        } catch (Exception ignored) {
            r.setValue(TextNode.valueOf(ar.getValueJson()));
        }
        return r;
    }
}
