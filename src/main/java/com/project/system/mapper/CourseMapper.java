// src/main/java/com.project.system.mapper/CourseMapper.java

package com.project.system.mapper;

import com.project.system.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface CourseMapper {
    int insertCourse(Course course);
    // 【新增】批量插入课程 (用于分配给多个班级)
    int insertBatchCourses(@Param("list") List<Course> list);

    List<Course> selectAllCourses();
    int updateCourse(Course course);
    int deleteCourseById(Long id);
}