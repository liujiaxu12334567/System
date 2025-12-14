package com.project.system.controller;

import com.project.system.service.AnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    @Autowired
    private AnalysisService analysisService;

    @GetMapping("/{courseId}")
    public ResponseEntity<?> getCourseAnalysis(@PathVariable Long courseId) {
        return ResponseEntity.ok(analysisService.getAnalysisByCourse(courseId));
    }
}
