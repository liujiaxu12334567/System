package com.project.system.mapper;

import com.project.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    User findByUsername(@Param("username") String username);
    User findByRealNameAndRoleType(@Param("realName") String realName, @Param("roleType") Integer roleType);
    User selectById(@Param("userId") Long userId);
    int insert(User user);

    // 【修改点】支持筛选和分页参数
    List<User> selectAllUsers(
            @Param("keyword") String keyword,
            @Param("roleType") String roleType,
            @Param("classId") String classId,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    // 【新增】查询总记录数 (用于分页)
    long countAllUsers(
            @Param("keyword") String keyword,
            @Param("roleType") String roleType,
            @Param("classId") String classId
    );

    int updateUser(User user);
    int deleteUserById(@Param("userId") Long userId);
    List<User> selectUsersByRole(@Param("roleType") String roleType);
    List<User> selectStudentsByClassIds(@Param("classIds") List<Long> classIds);
    int insertBatchStudents(@Param("list") List<com.project.system.entity.User> list);
    List<Long> selectDistinctClassIds();
    long countStudentsByTeachingClasses(
            @Param("keyword") String keyword,
            @Param("classId") String classId,
            @Param("classIds") List<String> classIds
    );

    // 【新增】教师端分页查询学生列表
    List<User> selectStudentsByTeachingClasses(
            @Param("keyword") String keyword,
            @Param("classId") String classId,
            @Param("classIds") List<String> classIds,
            @Param("offset") int offset,
            @Param("pageSize") int pageSize
    );

    // 【新增】按班级统计学生人数（用于课程应到人数）
    long countStudentsByClassId(@Param("classId") Long classId);

    // 【新增】按班级ID列表统计学生总数
    long countStudentsByClassIds(@Param("classIds") List<Long> classIds);
}
