package com.project.system.controller;

import com.project.system.entity.Application;
import com.project.system.entity.User;
import com.project.system.service.QualityService;
import com.project.system.service.impl.LeaderServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    // 学生提交素质学分申请
    @PostMapping("/apply/credit")
    public ResponseEntity<?> applyCredit(
            @RequestParam("type") String type,
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("file") MultipartFile file) {
        try {
            qualityService.submitCreditApplication(type, title, content, file);
            return ResponseEntity.ok("提交成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("提交失败: " + e.getMessage());
        }
    }

    // 学生提交请假申请
    @PostMapping("/apply/leave")
    public ResponseEntity<?> applyLeave(@RequestBody Application application) {
        qualityService.submitLeaveApplication(application);
        return ResponseEntity.ok("请假申请已提交");
    }

    // 素质教师获取待审批列表 (负责4个班级)
    @GetMapping("/teacher/applications")
    public ResponseEntity<List<Application>> getPendingApplications() {
        return ResponseEntity.ok(qualityService.getManagedApplications());
    }
// 在 QualityController 类中添加以下方法

    // 素质教师下发通知
    @PostMapping("/teacher/notify")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, String> payload) {
        String title = payload.get("title");
        String content = payload.get("content");
        try {
            qualityService.sendNotificationToManagedClasses(title, content);
            return ResponseEntity.ok("通知发送成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("发送失败: " + e.getMessage());
        }
    }
    // 素质教师审核
    @PostMapping("/teacher/review")
    public ResponseEntity<?> reviewApplication(@RequestBody Map<String, String> payload) {
        Long id = Long.valueOf(payload.get("id"));
        String status = payload.get("status");
        qualityService.reviewApplication(id, status);
        return ResponseEntity.ok("审核完成");
    }
}