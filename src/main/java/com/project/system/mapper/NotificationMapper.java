package com.project.system.mapper;

import com.project.system.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface NotificationMapper {
    /** 插入新通知 */
    int insert(Notification notification);

    /** 根据用户ID查询最新通知 */
    List<Notification> selectByUserId(@Param("userId") Long userId);
}