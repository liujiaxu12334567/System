package com.project.system.controller;

import com.project.system.entity.User;
import com.project.system.entity.Course;
import com.project.system.entity.Class;
import com.project.system.mapper.UserMapper;
import com.project.system.mapper.CourseMapper;
import com.project.system.mapper.ClassMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.HashSet;
import java.util.Set;

@RestController
@RequestMapping("/api/leader")
public class LeaderController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // 【Leader端特有】获取所有可选教师 (Role=3) 和课题组长 (Role=2)
    @GetMapping("/teacher/list")
    public ResponseEntity<?> listTeachersAndLeaders() {
        // 1. 获取所有普通教师 (Role=3)
        List<User> teachers = userMapper.selectUsersByRole("3");
        // 2. 获取所有课题组长 (Role=2)
        List<User> leaders = userMapper.selectUsersByRole("2");

        List<User> all = new ArrayList<>(teachers);
        all.addAll(leaders); // 合并列表

        // 注意：这里手动合并，确保 Admin 和 Leader 都能看到 Role=2 和 Role=3 的用户
        return ResponseEntity.ok(all);
    }

    // 1. 【课程管理】获取所有课程列表 (复用 CourseMapper)
    @GetMapping("/course/list")
    public ResponseEntity<?> listCourses() {
        return ResponseEntity.ok(courseMapper.selectAllCourses());
    }

    // 2. 【核心功能】发布新课程
    @PostMapping("/course/add")
    @Transactional
    public ResponseEntity<?> addCourse(@RequestBody Course course) {
        if (course.getClassId() == null) {
            return ResponseEntity.badRequest().body("发布课程必须指定班级ID。");
        }

        checkAndInsertClass(course.getClassId());

        course.setCode("L" + System.currentTimeMillis() % 10000);
        course.setStatus("进行中");
        course.setColor("blue");

        courseMapper.insertCourse(course);

        if (course.getTeacher() != null && !course.getTeacher().isEmpty() && course.getClassId() != null) {
            List<String> teacherNames = Arrays.asList(course.getTeacher().split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());

            updateTeacherTeachingClasses(teacherNames, classIds);
        }

        return ResponseEntity.ok("课程发布成功");
    }

    // 3. 【课程管理】更新课程 (用于分配单个教师)
    @PostMapping("/course/update")
    @Transactional
    public ResponseEntity<?> updateCourse(@RequestBody Course course) {
        courseMapper.updateCourse(course);

        if (course.getTeacher() != null && course.getClassId() != null) {
            List<String> teacherNames = Arrays.asList(course.getTeacher().split(","));
            List<Long> classIds = Collections.singletonList(course.getClassId());

            updateTeacherTeachingClasses(teacherNames, classIds);
        }

        return ResponseEntity.ok("更新成功");
    }

    // 4. 【课程管理】删除课程
    @PostMapping("/course/delete/{id}")
    public ResponseEntity<?> deleteCourse(@PathVariable Long id) {
        courseMapper.deleteCourseById(id);
        return ResponseEntity.ok("删除成功");
    }

    // 5. 【排课/下发内容】批量分配课程 (核心功能)
    @PostMapping("/course/batch-assign")
    @Transactional
    public ResponseEntity<?> batchAssignCourse(@RequestBody Map<String, Object> request) {
        String name = (String) request.get("name");
        String semester = (String) request.get("semester");
        List<String> teacherNames = (List<String>) request.get("teacherNames");
        List<Object> rawClassIds = (List<Object>) request.get("classIds");

        if (name == null || teacherNames == null || rawClassIds == null || teacherNames.isEmpty() || rawClassIds.isEmpty()) {
            return ResponseEntity.badRequest().body("课程信息、教师或班级列表不完整。");
        }

        List<Long> classIds = rawClassIds.stream()
                .map(obj -> {
                    if (obj instanceof Integer) return ((Integer) obj).longValue();
                    if (obj instanceof String) return Long.parseLong((String) obj);
                    return (Long) obj;
                })
                .collect(Collectors.toList());

        String baseCode = "L" + System.currentTimeMillis() % 10000;
        List<Course> coursesToInsert = new ArrayList<>();
        String teachersString = String.join(",", teacherNames);

        for (Long classId : classIds) {
            checkAndInsertClass(classId);

            Course newCourse = new Course();
            newCourse.setName(name);
            newCourse.setSemester(semester != null ? semester : "2025-1");
            newCourse.setCode(baseCode + "-" + classId);
            newCourse.setTeacher(teachersString);
            newCourse.setStatus("进行中");
            newCourse.setColor("blue");
            newCourse.setIsTop(0);
            newCourse.setClassId(classId);

            coursesToInsert.add(newCourse);
        }

        if (!coursesToInsert.isEmpty()) {
            courseMapper.insertBatchCourses(coursesToInsert);
        }

        updateTeacherTeachingClasses(teacherNames, classIds);

        return ResponseEntity.ok("成功为 " + coursesToInsert.size() + " 个班级分配了课程，并更新了相关教师的执教班级。");
    }


    // **********************************************
    // **************** HELPER METHODS ****************
    // **********************************************

    // 辅助方法：检查并插入新班级
    private void checkAndInsertClass(Long classId) {
        if (classId == null) return;

        Class existingClass = classMapper.findById(classId);

        if (existingClass == null) {
            String className = String.valueOf(classId) + "班";
            String finalMajor = "未分配专业";

            Class newClass = new Class(classId, className, finalMajor);
            classMapper.insert(newClass);
        }
    }

    // 辅助方法：更新教师执教班级的通用逻辑 (确保共存)
    private void updateTeacherTeachingClasses(List<String> teacherNames, List<Long> classIds) {

        List<User> allUsers = new ArrayList<>();
        allUsers.addAll(userMapper.selectUsersByRole("2"));
        allUsers.addAll(userMapper.selectUsersByRole("3"));

        for (String teacherName : teacherNames) {
            User cachedUser = allUsers.stream()
                    .filter(t -> teacherName.equals(t.getRealName()))
                    .findFirst()
                    .orElse(null);

            if (cachedUser != null) {
                User latestUser = userMapper.findByUsername(cachedUser.getUsername());

                if (latestUser == null) continue;

                String currentClasses = latestUser.getTeachingClasses();

                Set<String> classSet = new HashSet<>();
                if (currentClasses != null && !currentClasses.isEmpty()) {
                    Arrays.stream(currentClasses.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .forEach(classSet::add);
                }

                boolean addedNewClass = false;
                for (Long classId : classIds) {
                    String classStr = String.valueOf(classId);
                    if (classSet.add(classStr)) {
                        addedNewClass = true;
                    }
                }

                if (addedNewClass) {
                    String updatedClasses = String.join(",", classSet);

                    User userUpdate = new User();
                    userUpdate.setUserId(latestUser.getUserId());
                    userUpdate.setTeachingClasses(updatedClasses);

                    userMapper.updateUser(userUpdate);
                }
            }
        }
    }
}