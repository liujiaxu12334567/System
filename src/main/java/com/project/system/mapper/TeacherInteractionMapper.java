package com.project.system.mapper;

import com.project.system.entity.TeacherInteraction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Date;
import java.util.List;

@Mapper
public interface TeacherInteractionMapper {
    List<TeacherInteraction> selectByCourseIds(@Param("courseIds") List<Long> courseIds);

    int deleteByCourseIdAndDateAndClassIds(
            @Param("courseId") Long courseId,
            @Param("date") Date date,
            @Param("classIds") List<Long> classIds
    );

    int batchInsert(@Param("list") List<TeacherInteraction> list);
}
