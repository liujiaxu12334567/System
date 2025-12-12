package com.project.system.mapper;

import com.project.system.entity.CourseChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CourseChatMapper {
    int insert(CourseChat chat);
    List<CourseChat> selectByCourseId(@Param("courseId") Long courseId, @Param("limit") int limit);
}
