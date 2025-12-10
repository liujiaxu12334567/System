package com.project.system.mq;

import com.project.system.entity.Notification;
import com.project.system.entity.User;
import com.project.system.mapper.NotificationMapper;
import com.project.system.mapper.UserMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NotificationConsumer {

    @Autowired private UserMapper userMapper;
    @Autowired private NotificationMapper notificationMapper;

    @RabbitListener(queues = "notification.queue")
    public void handleNotification(Map<String, Object> msg) {
        String title = (String) msg.get("title");
        String content = (String) msg.get("content");
        String teachingClasses = (String) msg.get("teachingClasses");

        if (teachingClasses == null || teachingClasses.isEmpty()) return;

        // 解析班级并查找学生
        List<String> validClassIds = Arrays.stream(teachingClasses.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());

        List<User> students = userMapper.selectAllUsers(null, "4", null, 0, Integer.MAX_VALUE).stream()
                .filter(s -> s.getClassId() != null && validClassIds.contains(String.valueOf(s.getClassId())))
                .collect(Collectors.toList());

        // 批量插入通知
        for (User student : students) {
            Notification n = new Notification();
            n.setUserId(student.getUserId());
            n.setType("GENERAL_NOTICE");
            n.setTitle(title);
            n.setMessage(content);
            notificationMapper.insert(n);
        }
        System.out.println("【MQ】消费者已处理通知，共发送给 " + students.size() + " 位学生。");
    }
}