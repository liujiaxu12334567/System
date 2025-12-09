package com.project.system.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Material {
    private Long id;
    private Long courseId;
    private String type;
    private String content;
    private String fileName;
    private String filePath;
    private LocalDateTime createTime;
}