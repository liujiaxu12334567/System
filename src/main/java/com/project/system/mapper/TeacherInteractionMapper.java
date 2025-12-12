package com.project.system.mapper;

import com.project.system.entity.TeacherInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TeacherInteractionMapper {
    List<TeacherInteraction> selectByCourseIds(@Param("courseIds") List<Long> courseIds);
}
