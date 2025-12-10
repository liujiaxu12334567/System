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
}