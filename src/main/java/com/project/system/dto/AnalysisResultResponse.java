package com.project.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.sql.Timestamp;

@Data
public class AnalysisResultResponse {
    private Long id;
    private Long courseId;
    private String metric;
    private String valueJson;
    private JsonNode value;
    private Timestamp generatedAt;
    private String eventId;
}

