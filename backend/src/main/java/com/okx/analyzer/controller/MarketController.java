package com.okx.analyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.okx.analyzer.service.OkxApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketController {

    private final OkxApiService okxApiService;

    /**
     * 代理 OKX 公开行情 K 线数据
     * GET /api/market/candles?instId=BTC-USDT-SWAP&bar=1H&limit=300
     * 返回格式：[[ts, open, high, low, close, vol, ...], ...]
     */
    @GetMapping("/candles")
    public ResponseEntity<?> getCandles(
            @RequestParam String instId,
            @RequestParam(defaultValue = "1H") String bar,
            @RequestParam(defaultValue = "300") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 300);
        String path = "/api/v5/market/candles?instId=" + instId
                + "&bar=" + bar
                + "&limit=" + safeLimit;

        JsonNode data = okxApiService.getPublic(path);
        return ResponseEntity.ok(data);
    }

    /**
     * 代理 OKX 公开资金费率历史
     * GET /api/market/funding-rate-history?instId=BTC-USDT-SWAP&limit=100
     */
    @GetMapping("/funding-rate-history")
    public ResponseEntity<?> getFundingRateHistory(
            @RequestParam String instId,
            @RequestParam(defaultValue = "100") int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 100);
        String path = "/api/v5/public/funding-rate-history?instId=" + instId
                + "&limit=" + safeLimit;

        JsonNode data = okxApiService.getPublic(path);
        return ResponseEntity.ok(data);
    }
}
