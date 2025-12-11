package com.project.system.mapper;

import com.project.system.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface NotificationMapper {
    int insert(Notification notification);

    List<Notification> selectByUserId(@Param("userId") Long userId);

    // 【新增】用户提交回复
    int updateReply(@Param("id") Long id, @Param("userReply") String userReply);

    // 【新增】管理员查询自己发送的通知批次 (去重显示)
    List<Notification> selectSentBatches();

    // 【新增】根据批次ID查询详细统计 (包含接收人信息)
    List<Map<String, Object>> selectStatsByBatchId(@Param("batchId") String batchId);
}