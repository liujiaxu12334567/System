package com.project.system.mapper;

import com.project.system.entity.QuizRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface QuizRecordMapper {

    // 1. 插入测验记录
    int insert(QuizRecord record);

    // 2. 根据用户和资料ID查找记录
    QuizRecord findByUserIdAndMaterialId(@Param("userId") Long userId, @Param("materialId") Long materialId);

    // 3. 更新 AI 反馈
    int updateAiFeedback(QuizRecord record);

    // 4. 根据资料ID查找所有记录
    List<QuizRecord> findByMaterialId(@Param("materialId") Long materialId);

    // 5. 更新分数和反馈
    int updateScoreAndFeedback(QuizRecord record);

    // 6. 删除记录 (打回重做)
    int deleteById(@Param("id") Long id);

    // 7. 根据用户ID查找所有记录
    List<QuizRecord> selectByUserId(Long userId);

    // 8. 根据ID查找单个记录
    QuizRecord findById(@Param("id") Long id);
}