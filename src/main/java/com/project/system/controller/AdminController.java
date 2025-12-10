package com.project.system.controller;

import com.project.system.dto.BatchEnrollmentRequest;
import com.project.system.entity.Course;
import com.project.system.entity.User;
import com.project.system.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/user/list")
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleType,
            @RequestParam(required = false) String classId,
            @RequestParam(required = false, defaultValue = "1") int pageNum,
            @RequestParam(required = false, defaultValue = "10") int pageSize) {
        return ResponseEntity.ok(adminService.listUsers(keyword, roleType, classId, pageNum, pageSize));
    }

    @PostMapping("/user/add")
    public ResponseEntity<?> addUser(@RequestBody Map<String, Object> userMap) {
        try {
            adminService.addUser(userMap);
            return ResponseEntity.ok("添加成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/user/update")
    public ResponseEntity<?> updateUser(@RequestBody User user) {
        adminService.updateUser(user);
        return ResponseEntity.ok("更新成功");
    }

    @PostMapping("/user/delete/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok("删除成功");
    }

    @PostMapping("/batch/enroll")
    public ResponseEntity<?> batchEnroll(@RequestBody BatchEnrollmentRequest request) {
        try {
            return ResponseEntity.ok(adminService.batchEnroll(request));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/batch/upload")
    public ResponseEntity<?> batchEnrollFromFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetClassId") String targetClassId,
            @RequestParam("startUsername") String startUsername,
            @RequestParam("major") String major) {
        try {
            String result = adminService.batchEnrollFromFile(file, Long.parseLong(targetClassId), Long.parseLong(startUsername), major);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/course/list")
    public ResponseEntity<?> listCourses() {
        return ResponseEntity.ok(adminService.listCourses());
    }

    @PostMapping("/course/add")
    public ResponseEntity<?> addCourse(@RequestBody Course course) {
        adminService.addCourse(course);
        return ResponseEntity.ok("课程发布成功");
    }

    @PostMapping("/course/batch-assign")
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {
        adminService.batchAssignCourse(
                (String) request.get("name"),
                (String) request.get("semester"),
                (List<String>) request.get("teacherNames"),
                (List<Object>) request.get("classIds")
        );
        return ResponseEntity.ok("批量分配成功");
    }

    @PostMapping("/course/update")
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        adminService.updateCourse(course);
        return ResponseEntity.ok("更新成功");
    }

    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        adminService.deleteCourse(id);
        return ResponseEntity.ok("删除成功");
    }

    @GetMapping("/classes")
    public ResponseEntity<?> listClasses() {
        return ResponseEntity.ok(adminService.listClasses());
    }

    @GetMapping("/applications/pending")
    public ResponseEntity<?> listPendingApplications() {
        return ResponseEntity.ok(adminService.listPendingApplications());
    }

    @PostMapping("/applications/review")
    public ResponseEntity<?> reviewApplication(@RequestBody Map<String, Object> request) {
        try {
            adminService.reviewApplication(Long.valueOf(request.get("id").toString()), (String) request.get("status"));
            return ResponseEntity.ok("操作成功");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}