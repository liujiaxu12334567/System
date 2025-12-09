package com.project.system.mapper;

import com.project.system.entity.Class;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ClassMapper {
    // 查找班级是否存在
    Class findById(@Param("classId") Long classId);

    // 插入新班级
    int insert(Class classEntity);

    // 获取所有班级 (用于前端下拉框)
    List<Class> selectAllClasses();
}