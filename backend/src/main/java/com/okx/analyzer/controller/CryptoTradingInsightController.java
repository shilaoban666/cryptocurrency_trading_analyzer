package com.okx.analyzer.controller;

import com.okx.analyzer.service.CryptoTradingInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class CryptoTradingInsightController {

    private final CryptoTradingInsightService insightService;

    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "120") int limit) {
        return insightService.listInsights(limit);
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh() {
        return insightService.refreshInsights();
    }
}
