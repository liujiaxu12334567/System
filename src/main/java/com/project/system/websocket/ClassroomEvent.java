package com.project.system.websocket;

import lombok.Data;

@Data
public class ClassroomEvent {
    private String type;      // question/hand/race/answer/call
    private Long courseId;
    private Long questionId;
    private Object payload;   // question或answer等具体数据

    public ClassroomEvent(String type, Long courseId, Long questionId, Object payload) {
        this.type = type;
        this.courseId = courseId;
        this.questionId = questionId;
        this.payload = payload;
    }
}
