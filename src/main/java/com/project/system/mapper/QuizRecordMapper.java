package com.project.system.mapper;
import com.project.system.entity.QuizRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface QuizRecordMapper {
    @Insert("INSERT INTO sys_quiz_record(user_id, material_id, score, user_answers, submit_time) " +
            "VALUES(#{userId}, #{materialId}, #{score}, #{userAnswers}, NOW())")
    int insert(QuizRecord record);

    @Select("SELECT * FROM sys_quiz_record WHERE user_id = #{userId} AND material_id = #{materialId} LIMIT 1")
    QuizRecord findByUserIdAndMaterialId(@Param("userId") Long userId, @Param("materialId") Long materialId);
}