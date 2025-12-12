// src/main/java/com/project/system/entity/Course.java

package com.project.system.entity;

import lombok.Data;

@Data
public class Course {
    private Long id;
    private String name;      // 课程名
    private String semester;  // 学期
    private String teacher;   // 教师
    private String code;      // 课程代码
    private String status;    // 状态
    private String color;     // 颜色样式
    private Integer isTop;    // 是否置顶

    private String managerName; // 【新增】课题组长姓名 (关键字段)

    private Long classId;     // 所属班级 ID（单班级旧字段，保留兼容）
    private String responsibleClassIds; // 负责班级列表（逗号分隔），支持多班级
    private Long studentCount;          // 应到人数（运行时填充，非持久化）
}
