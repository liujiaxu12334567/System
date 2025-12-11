package com.project.system.controller;

import com.project.system.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> payload) {
        String message = (String) payload.get("message");
        List<Map<String, String>> history = (List<Map<String, String>>) payload.get("history");
        return ResponseEntity.ok(aiService.chat(message, history));
    }

    @PostMapping("/write")
    public ResponseEntity<?> polish(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(aiService.polishText(payload.get("content")));
    }

    @PostMapping("/code")
    public ResponseEntity<?> explainCode(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(aiService.explainCode(payload.get("code")));
    }

    @PostMapping("/plan")
    public ResponseEntity<?> generatePlan(@RequestBody Map<String, String> payload) {
        return ResponseEntity.ok(aiService.generatePlan(payload.get("goal")));
    }
}