package com.project.system.controller;

import com.project.system.dto.AnalysisResultResponse;
import com.project.system.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourseAnalysis(@PathVariable Long courseId) {
        return ResponseEntity.ok(analysisService.getAnalysisByCourse(courseId));
    }

    // 【新增】查询特定指标接口
    @GetMapping("/result")
    public ResponseEntity<?> getAnalysisResult(
            @RequestParam Long courseId,
            @RequestParam String metric) {
        AnalysisResultResponse result = analysisService.getLatestAnalysisResult(courseId, metric);
        if (result == null) {
            // 返回空对象或 404，这里返回空对象方便前端处理
            return ResponseEntity.ok(null);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/results")
    public ResponseEntity<?> listAnalysisResults(
            @RequestParam Long courseId,
            @RequestParam String metric,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return ResponseEntity.ok(analysisService.listAnalysisResults(courseId, metric, limit));
    }
}
