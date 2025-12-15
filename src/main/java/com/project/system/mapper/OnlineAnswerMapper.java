package com.project.system.mapper;

import com.project.system.entity.OnlineAnswer;
import com.project.system.dto.StudentCountAgg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OnlineAnswerMapper {
    int insert(OnlineAnswer answer);
    List<OnlineAnswer> selectByQuestionId(@Param("questionId") Long questionId);
    long countByCourseIds(@Param("courseIds") List<Long> courseIds);
    int updateAnswerTextById(@Param("id") Long id, @Param("answerText") String answerText);
    List<OnlineAnswer> selectByCourseIds(@Param("courseIds") List<Long> courseIds);
    OnlineAnswer selectById(@Param("id") Long id);
    int deleteByQuestionIds(@Param("questionIds") List<Long> questionIds);

    List<StudentCountAgg> countAnswersByCourseIdsAndStudentIds(
            @Param("courseIds") List<Long> courseIds,
            @Param("studentIds") List<Long> studentIds);
}
