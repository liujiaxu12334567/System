package com.project.system.mapper;

import com.project.system.entity.Course;
import com.project.system.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface HomeMapper {
    List<Course> selectAllCourses();
    List<Task> selectAllTasks();
}