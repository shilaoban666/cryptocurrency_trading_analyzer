package com.okx.analyzer.controller;

import com.okx.analyzer.dto.PositionAiAnalysisDto;
import com.okx.analyzer.dto.PositionDto;
import com.okx.analyzer.service.PositionAiAnalysisService;
import com.okx.analyzer.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;
    private final PositionAiAnalysisService positionAiAnalysisService;

    @GetMapping("/current")
    public List<PositionDto> current() {
        return positionService.currentPositions();
    }

    @PostMapping("/ai-analysis")
    public PositionAiAnalysisDto aiAnalysis() {
        return positionAiAnalysisService.analyzeCurrentPositions();
    }

    @GetMapping(value = "/ai-analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter aiAnalysisStream() {
        return positionAiAnalysisService.streamCurrentPositions();
    }
}
