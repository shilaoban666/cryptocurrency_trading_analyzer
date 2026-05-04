package com.okx.analyzer.controller;

import com.okx.analyzer.service.OrderSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private static final Set<String> SUPPORTED_INST_TYPES = Set.of("SWAP", "FUTURES");

    private final OrderSyncService syncService;

    /** 同步合约历史订单（SWAP=永续合约，FUTURES=交割合约） */
    @PostMapping("/{instType}")
    public Map<String, Object> sync(@PathVariable String instType) {
        long start = System.currentTimeMillis();
        int newCount = syncService.syncOrders(normalizeInstType(instType));
        return Map.of(
                "success", true,
                "newOrders", newCount,
                "costMs", System.currentTimeMillis() - start
        );
    }

    /** 同步所有合约类型 */
    @PostMapping("/all")
    public Map<String, Object> syncAll() {
        int swap    = syncService.syncOrders("SWAP");
        int futures = syncService.syncOrders("FUTURES");
        return Map.of("success", true, "swap", swap, "futures", futures);
    }

    private String normalizeInstType(String instType) {
        String normalized = instType == null ? "" : instType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_INST_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("instType 只支持 SWAP 或 FUTURES");
        }
        return normalized;
    }
}
