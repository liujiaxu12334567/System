// src/main/java/com/project/system/mapper/CourseMapper.java

package com.project.system.mapper;

import com.project.system.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CourseMapper {
    int insertCourse(Course course);
    int insertBatchCourses(@Param("list") List<Course> list);

    List<Course> selectAllCourses();
    int updateCourse(Course course);
    int deleteCourseById(Long id);

    // 【新增/核心方法】根据任课教师姓名查询课程 (用于 Leader 限定scope)
    List<Course> selectCoursesByTeacherName(@Param("teacherName") String teacherName);
}