package com.project.system.mapper;

import com.project.system.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MaterialMapper {
    int insert(Material material);
    List<Material> selectByCourseId(@Param("courseId") Long courseId);

    // 【新增】根据ID查询单个资料（为了获取题目内容给AI）
    Material findById(@Param("id") Long id);

    // 【修复/新增】更新资料内容（用于修改截止时间）
    int updateContent(@Param("id") Long id, @Param("content") String content);
}