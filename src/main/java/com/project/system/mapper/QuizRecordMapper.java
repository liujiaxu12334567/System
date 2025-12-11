package com.project.system.mapper;

import com.project.system.entity.QuizRecord;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface QuizRecordMapper {

    @Insert("INSERT INTO sys_quiz_record(user_id, material_id, score, user_answers, ai_feedback, submit_time) " +
            "VALUES(#{userId}, #{materialId}, #{score}, #{userAnswers}, #{aiFeedback}, NOW())")
    int insert(QuizRecord record);

    @Select("SELECT * FROM sys_quiz_record WHERE user_id = #{userId} AND material_id = #{materialId} LIMIT 1")
    QuizRecord findByUserIdAndMaterialId(@Param("userId") Long userId, @Param("materialId") Long materialId);

    @Update("UPDATE sys_quiz_record SET ai_feedback = #{aiFeedback} WHERE id = #{id}")
    int updateAiFeedback(QuizRecord record);

    @Select("SELECT * FROM sys_quiz_record WHERE material_id = #{materialId} ORDER BY submit_time DESC")
    List<QuizRecord> findByMaterialId(@Param("materialId") Long materialId);

    @Update("UPDATE sys_quiz_record SET score = #{score}, ai_feedback = #{aiFeedback} WHERE id = #{id}")
    int updateScoreAndFeedback(QuizRecord record);

    // 【🔥 核心修复 🔥】 确保表名是 sys_quiz_record，而不是 sys_course 或 sys_material
    @Delete("DELETE FROM sys_quiz_record WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    @Select("SELECT * FROM sys_quiz_record WHERE user_id = #{userId}")
    List<QuizRecord> selectByUserId(Long userId);
    @Select("SELECT * FROM sys_quiz_record WHERE id = #{id}")
    QuizRecord findById(@Param("id") Long id);
}