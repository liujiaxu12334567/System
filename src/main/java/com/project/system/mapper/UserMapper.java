package com.project.system.mapper;

import com.project.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    User findByUsername(@Param("username") String username);
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
}