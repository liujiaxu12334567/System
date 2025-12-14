package com.project.system.mapper;

import com.project.system.entity.AttendanceSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AttendanceSummaryMapper {
    List<AttendanceSummary> selectByCourseIds(@Param("courseIds") List<Long> courseIds);

    List<AttendanceSummary> selectByClassIds(@Param("classIds") List<Long> classIds);
}
