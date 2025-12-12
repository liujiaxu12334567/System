package com.project.system.mapper;

import com.project.system.entity.CourseClass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseClassMapper {
    List<CourseClass> selectByCourseIds(@Param("courseIds") List<Long> courseIds);
}
