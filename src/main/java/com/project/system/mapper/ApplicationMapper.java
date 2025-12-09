package com.project.system.mapper;

import com.project.system.entity.Application;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ApplicationMapper {
    // 提交申请 (TeacherController 调用)
    int insert(Application app);

    // 查询某位老师的申请记录 (TeacherController 调用)
    List<Application> findByTeacherId(@Param("teacherId") Long teacherId);

    // 查询所有待审核申请 (预留给 Admin/LeaderController 调用)
    List<Application> findByStatus(@Param("status") String status);
    Application findById(@Param("id") Long id);
    // (预留给 Admin/LeaderController) 审核后更新状态
    int updateStatus(@Param("id") Long id, @Param("status") String status);
}