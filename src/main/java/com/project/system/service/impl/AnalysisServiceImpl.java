package com.project.system.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.system.entity.AnalysisResult;
import com.project.system.mapper.AnalysisResultMapper;
import com.project.system.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
