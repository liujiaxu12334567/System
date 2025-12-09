package com.project.system.mapper;

import com.project.system.entity.Course;
import com.project.system.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param; // 引入 Param
import java.util.List;

@Mapper
public interface HomeMapper {
    List<Course> selectAllCourses();

    // 【新增】根据班级ID查询课程
    List<Course> selectCoursesByClassId(@Param("classId") Long classId);

    List<Task> selectAllTasks();
}