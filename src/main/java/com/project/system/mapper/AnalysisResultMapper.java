package com.project.system.mapper;

import com.project.system.entity.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisResultMapper {
    List<AnalysisResult> selectByCourseId(@Param("courseId") Long courseId);

    void insert(AnalysisResult result);

    AnalysisResult selectLatestByCourseIdAndMetric(@Param("courseId") Long courseId, @Param("metric") String metric);

    List<AnalysisResult> selectLatestByCourseIdsAndMetric(
            @Param("courseIds") List<Long> courseIds,
            @Param("metric") String metric);

    List<AnalysisResult> selectByCourseIdAndMetricLimit(
            @Param("courseId") Long courseId,
            @Param("metric") String metric,
            @Param("limit") int limit);
}

