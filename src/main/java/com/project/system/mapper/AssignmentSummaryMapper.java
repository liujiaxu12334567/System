package com.project.system.mapper;

import com.project.system.entity.AssignmentSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AssignmentSummaryMapper {
    List<AssignmentSummary> selectByCourseIds(@Param("courseIds") List<Long> courseIds);
}
