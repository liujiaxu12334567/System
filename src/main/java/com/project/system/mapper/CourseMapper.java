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

    // 【核心方法】根据课题组长姓名查询其负责的课程
    List<Course> selectCoursesByManagerName(@Param("managerName") String managerName);
}