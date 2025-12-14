package com.project.system.mapper;

import com.project.system.entity.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisResultMapper {
    List<AnalysisResult> selectByCourseId(@Param("courseId") Long courseId);

    void insert(AnalysisResult result);
}
