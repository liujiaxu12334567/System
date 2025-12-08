package com.project.system.mapper;

import com.project.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     * (用于登录，会同时关联查询出班级名称)
     */
    User findByUsername(@Param("username") String username);

    /**
     * 插入新用户
     * (用于注册或管理员添加用户，支持班级ID插入)
     */
    int insertUser(User user);

    /**
     * (可选) 根据班级ID查询该班级下的所有学生
     * 供课程组长选人时使用
     */
    List<User> findStudentsByClassId(@Param("classId") Long classId);
    int insert(User user);
}