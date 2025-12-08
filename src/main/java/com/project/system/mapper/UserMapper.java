package com.project.system.mapper;

import com.project.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    // 原有的登录查询
    User findByUsername(@Param("username") String username);
    int insert(User user);

    // === 新增管理功能 ===
    // 1. 查询用户列表 (支持按用户名模糊搜索)
    List<User> selectAllUsers(@Param("username") String username);

    // 2. 更新用户信息 (用于修改角色/密码/状态)
    int updateUser(User user);

    // 3. 删除用户
    int deleteUserById(@Param("userId") Long userId);
}