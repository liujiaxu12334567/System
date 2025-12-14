package com.project.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class CourseBatchAssignRequest {
    // 推荐使用：严格模式以课程组ID为准
    private Long groupId;

    // 兼容字段：当 groupId 为空时可用 name+semester 定位课程组（不建议）
    private String name;
    private String semester;

    // 兼容字段：历史接口使用组长姓名；现在以课程组 leader 为准
    private String managerName;

    // 每个班级指定一个授课老师
    private List<Item> assignments;

    @Data
    public static class Item {
        private Long classId;
        private Long teacherId;
    }
}

