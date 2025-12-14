package com.project.system.controller;

import com.project.system.entity.Course;
import com.project.system.dto.TeacherAnalysisResponse;
import com.project.system.service.LeaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leader")
public class LeaderController {

    @Autowired
    private LeaderService leaderService;

    @PostMapping("/course/{courseId}/publish-exam")
    public ResponseEntity<?> publishCourseExam(@PathVariable Long courseId, @RequestBody Map<String, Object> examData) {
        try {
            leaderService.publishCourseExam(courseId, examData);
            return ResponseEntity.ok("考试发布成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/teacher/list")
    public ResponseEntity<?> listMyTeamMembers() {
        return ResponseEntity.ok(leaderService.listMyTeamMembers());
    }

    @GetMapping("/teacher/analysis")
    public ResponseEntity<?> listTeacherAnalysis(
            @RequestParam(required = false, defaultValue = "classroom_online_performance") String metric) {
        try {
            List<TeacherAnalysisResponse> result = leaderService.listTeacherAnalysis(metric);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/course/list")
    public ResponseEntity<?> listMyCourses() {
        return ResponseEntity.ok(leaderService.listMyCourses());
    }

    @PostMapping(value = "/course/{courseId}/upload-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCourseMaterial(
            @PathVariable Long courseId,
            @RequestParam("type") String type,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "deadline", required = false) String deadline,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            leaderService.uploadCourseMaterial(courseId, type, title, content, deadline, file);
            return ResponseEntity.ok("发布成功");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("发布失败: " + e.getMessage());
        }
    }

    @PostMapping("/notification/send")
    public ResponseEntity<?> sendNotification(@RequestBody Map<String, Object> data) {
        leaderService.sendNotification((String) data.get("title"), (String) data.get("content"), (List<String>) data.get("targets"));
        return ResponseEntity.ok("通知已下发");
    }

    @PostMapping("/course/update")
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        leaderService.updateCourse(course);
        return ResponseEntity.ok("更新成功");
    }

    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        leaderService.deleteCourse(id);
        return ResponseEntity.ok("删除成功");
    }

    @PostMapping("/course/batch-assign")
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {
        try {
            leaderService.batchAssignCourse(
                    (String) request.get("name"),
                    (String) request.get("semester"),
                    (List<String>) request.get("teacherNames"),
                    (List<Object>) request.get("classIds")
            );
            return ResponseEntity.ok("批量分配成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/material/update-deadline")
    public ResponseEntity<?> updateMaterialDeadline(@RequestBody Map<String, Object> data) {
        try {
            leaderService.updateMaterialDeadline(
                    Long.valueOf(data.get("materialId").toString()),
                    (String) data.get("newDeadline")
            );
            return ResponseEntity.ok("截止时间已更新");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/applications/pending")
    public ResponseEntity<?> listPendingApplications() {
        return ResponseEntity.ok(leaderService.listPendingApplications());
    }
    // 【新增接口 1：批量发布考试】
    @PostMapping("/course/batch-publish-exam")
    public ResponseEntity<?> batchPublishExam(@RequestBody Map<String, Object> examData) {
        try {
            List<String> courseNames = (List<String>) examData.get("courseNames");
            leaderService.batchPublishExam(examData, courseNames);
            return ResponseEntity.ok("批量考试发布成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping(value = "/course/batch-material", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> batchSendMaterial(
            @RequestParam("type") String type,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "deadline", required = false) String deadline,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "courseNames") List<String> courseNames) {
        try {
            leaderService.batchSendMaterialToTeachers(type, title, content, deadline, file, courseNames);
            return ResponseEntity.ok("批量资料下发成功，共影响 " + courseNames.size() + " 个课程");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("批量下发失败: " + e.getMessage());
        }
    }
    @PostMapping("/applications/review")
    public ResponseEntity<?> reviewApplication(@RequestBody Map<String, Object> request) {
        try {
            leaderService.reviewApplication(
                    Long.valueOf(request.get("id").toString()),
                    (String) request.get("status")
            );
            return ResponseEntity.ok("操作成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
