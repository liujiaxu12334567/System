package com.project.system.mapper;

import com.project.system.entity.OnlineQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OnlineQuestionMapper {
    int insert(OnlineQuestion question);
    List<OnlineQuestion> selectByCourseIds(@Param("courseIds") List<Long> courseIds);
    OnlineQuestion selectById(@Param("id") Long id);
    long countByCourseIds(@Param("courseIds") List<Long> courseIds);
    List<OnlineQuestion> selectAllByCourseIds(@Param("courseIds") List<Long> courseIds);
    int deleteByCourseId(@Param("courseId") Long courseId);
    List<Long> selectIdsByCourseId(@Param("courseId") Long courseId);
}
