package com.project.system.mapper;

import com.project.system.entity.AnalysisResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AnalysisResultMapper {
    List<AnalysisResult> selectByCourseId(@Param("courseId") Long courseId);

    void insert(AnalysisResult result);

    // 【新增】查询指定课程指定指标的最新记录
    AnalysisResult selectLatestByCourseIdAndMetric(@Param("courseId") Long courseId, @Param("metric") String metric);

    List<AnalysisResult> selectByCourseIdAndMetricLimit(
            @Param("courseId") Long courseId,
            @Param("metric") String metric,
            @Param("limit") int limit);
}
