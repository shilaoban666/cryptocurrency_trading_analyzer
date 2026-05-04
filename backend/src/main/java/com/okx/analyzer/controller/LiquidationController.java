package com.okx.analyzer.controller;

import com.okx.analyzer.dto.LiquidationDto;
import com.okx.analyzer.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/liquidation")
@RequiredArgsConstructor
public class LiquidationController {

    private final LiquidationService liquidationService;

    /** 获取爆仓统计 */
    @GetMapping
    public LiquidationDto getStats() {
        return liquidationService.getStats();
    }

    /** 从 OKX 同步强平记录 */
    @PostMapping("/sync")
    public Map<String, Object> sync() {
        long start = System.currentTimeMillis();
        int newCount = liquidationService.syncLiquidations();
        return Map.of(
                "success", true,
                "newRecords", newCount,
                "costMs", System.currentTimeMillis() - start
        );
    }
}
