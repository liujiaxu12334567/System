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

    // 用户提交回复 (同时标记已读)
    int updateReply(@Param("id") Long id, @Param("userReply") String userReply);

    // 【新增】仅标记为已读
    int updateReadStatus(@Param("id") Long id);

    // 管理员查询自己发送的通知批次
    List<Notification> selectSentBatches();

    // 根据批次ID查询详细统计
    List<Map<String, Object>> selectStatsByBatchId(@Param("batchId") String batchId);

    // 根据批次ID汇总统计
    Map<String, Object> selectStatsSummaryByBatchId(@Param("batchId") String batchId);
}
