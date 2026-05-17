package com.okx.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderSyncService {

    private final OkxApiService okxApiService;
    private final OkxOrderRepository orderRepo;

    private static final BigDecimal PNL_THRESHOLD = new BigDecimal("0.0001");

    /** 同步指定合约类型的历史订单，返回新增条数 */
    @Transactional
    public int syncOrders(String instType) {
        List<JsonNode> rawOrders = okxApiService.fetchAllContractOrders(instType);

        Set<String> seen = new HashSet<>();
        int newCount = 0;

        for (JsonNode node : rawOrders) {
            String ordId = node.path("ordId").asText();
            if (seen.contains(ordId)) {
                seen.add(ordId);
                continue;
            }
            seen.add(ordId);

            try {
                Optional<OkxOrder> existing = orderRepo.findByOrdId(ordId);
                if (existing.isPresent()) {
                    OkxOrder order = existing.get();
                    if (order.getRawData() == null || order.getRawData().isBlank()) {
                        order.setRawData(node.toString());
                        orderRepo.save(order);
                    }
                    continue;
                }

                OkxOrder order = parseOrder(node);
                orderRepo.save(order);
                newCount++;
            } catch (Exception e) {
                log.warn("[Sync] 解析订单失败 ordId={}: {}", ordId, e.getMessage());
            }
        }

        log.info("[Sync] instType={} 同步完成，新增 {} 条", instType, newCount);
        return newCount;
    }

    private OkxOrder parseOrder(JsonNode n) {
        OkxOrder o = new OkxOrder();
        o.setOrdId(n.path("ordId").asText());
        o.setInstId(n.path("instId").asText());
        o.setInstType(n.path("instType").asText());
        o.setSide(n.path("side").asText());
        o.setPosSide(n.path("posSide").asText("net"));
        o.setOrdType(n.path("ordType").asText());
        o.setSz(decimal(n, "sz"));
        o.setPx(decimal(n, "px"));
        o.setAvgPx(decimal(n, "avgPx"));
        o.setFillSz(decimal(n, "fillSz"));
        o.setPnl(decimal(n, "pnl"));
        o.setFee(decimal(n, "fee"));
        o.setLever(n.path("lever").asText(""));
        o.setState(n.path("state").asText());
        o.setRawData(n.toString());

        long cMs = n.path("cTime").asLong();
        long uMs = n.path("uTime").asLong();
        o.setCreateTime(toLocalDateTime(cMs));
        o.setUpdateTime(toLocalDateTime(uMs));
        o.setHoldingMinutes((int) ((uMs - cMs) / 60_000));

        // 判断胜负：pnl != 0 的是关仓单
        BigDecimal pnl = o.getPnl() != null ? o.getPnl() : BigDecimal.ZERO;
        if (pnl.abs().compareTo(PNL_THRESHOLD) > 0) {
            o.setIsWin(pnl.compareTo(BigDecimal.ZERO) > 0 ? 1 : 0);
        }

        // 判断是否为强平单
        if ("liquidation".equalsIgnoreCase(o.getOrdType())) {
            o.setIsLiquidation(1);
        }

        return o;
    }

    private BigDecimal decimal(JsonNode n, String field) {
        String v = n.path(field).asText("");
        if (v.isEmpty() || v.equals("")) return BigDecimal.ZERO;
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalDateTime toLocalDateTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs)
                      .atZone(ZoneId.of("Asia/Shanghai"))
                      .toLocalDateTime();
    }
}
