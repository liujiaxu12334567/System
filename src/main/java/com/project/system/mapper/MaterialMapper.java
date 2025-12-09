package com.project.system.mapper;

import com.project.system.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface MaterialMapper {
    int insert(Material material);

    // 查询某门课程的所有资料
    List<Material> selectByCourseId(@Param("courseId") Long courseId);
}