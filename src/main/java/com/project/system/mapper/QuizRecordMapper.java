package com.project.system.mapper;

import com.project.system.entity.QuizRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface QuizRecordMapper {

    // 【修改】确保 insert 包含 ai_feedback 字段（即使默认为 null）
    @Insert("INSERT INTO sys_quiz_record(user_id, material_id, score, user_answers, ai_feedback, submit_time) " +
            "VALUES(#{userId}, #{materialId}, #{score}, #{userAnswers}, #{aiFeedback}, NOW())")
    int insert(QuizRecord record);

    @Select("SELECT * FROM sys_quiz_record WHERE user_id = #{userId} AND material_id = #{materialId} LIMIT 1")
    QuizRecord findByUserIdAndMaterialId(@Param("userId") Long userId, @Param("materialId") Long materialId);

    // 【新增】单独更新 AI 反馈字段的方法
    @Update("UPDATE sys_quiz_record SET ai_feedback = #{aiFeedback} WHERE id = #{id}")
    int updateAiFeedback(QuizRecord record);
}