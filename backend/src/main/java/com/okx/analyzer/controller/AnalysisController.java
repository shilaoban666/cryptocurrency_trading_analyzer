package com.okx.analyzer.controller;

import com.okx.analyzer.dto.AnalysisDto;
import com.okx.analyzer.service.AnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
public class AnalysisController {

    private final AnalysisService analysisService;

    /**
     * 获取全部分析数据
     * @param start 开始时间（可选），格式 2024-01-01T00:00:00
     * @param end   结束时间（可选）
     */
    @GetMapping
    public AnalysisDto analyze(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return analysisService.analyze(start, end);
    }
}
