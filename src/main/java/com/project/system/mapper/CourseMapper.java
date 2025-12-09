package com.project.system.mapper;

import com.project.system.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CourseMapper {
    int insertCourse(Course course);
    List<Course> selectAllCourses();
    int updateCourse(Course course);
    int deleteCourseById(Long id);
}