package com.okx.analyzer.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class PositionAiAnalysisDto {
    private String model;
    private LocalDateTime generatedAt;
    private String headline;
    private String riskLevel;
    private String actionBias;
    private Integer biasScore;
    private Integer confidence;
    private String positionAnalysis;
    private String marketAnalysis;
    private String actionRecommendation;
    private List<String> keySignals;
    private List<String> riskWarnings;
    private List<String> actionChecklist;
    private String rawText;
    private Map<String, Object> usage;
}
