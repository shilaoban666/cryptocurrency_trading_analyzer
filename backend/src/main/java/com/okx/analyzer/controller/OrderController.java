package com.okx.analyzer.controller;

import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OkxOrderRepository orderRepo;

    /** 分页查询订单 */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String state) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createTime").descending());
        Page<OkxOrder> result = orderRepo.findWithFilter(instId, side, state, pageable);

        return Map.of(
                "list",  result.getContent(),
                "total", result.getTotalElements(),
                "pages", result.getTotalPages()
        );
    }

    /** 品种列表（用于筛选下拉） */
    @GetMapping("/symbols")
    public List<String> symbols() {
        return orderRepo.findDistinctInstIds();
    }

    /** 数据库中总单数 */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of(
                "total",  orderRepo.count(),
                "filled", orderRepo.countByState("filled")
        );
    }

    /** 亏损单明细：用于爆仓分析页里的亏损分析，不依赖强平记录 */
    @GetMapping("/losses")
    public Map<String, Object> losses(@RequestParam(defaultValue = "2000") int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 5000));

        List<OkxOrder> allLosses = orderRepo.findAllClosedOrders().stream()
                .filter(o -> netPnl(o).compareTo(BigDecimal.ZERO) < 0)
                .sorted(Comparator.comparing(this::settledAt).reversed())
                .toList();

        List<Map<String, Object>> list = allLosses.stream()
                .limit(safeLimit)
                .map(this::lossRow)
                .toList();

        double totalLoss = allLosses.stream().mapToDouble(o -> netPnl(o).abs().doubleValue()).sum();
        double totalFee = allLosses.stream().mapToDouble(o -> zero(o.getFee()).abs().doubleValue()).sum();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", allLosses.size());
        summary.put("returned", list.size());
        summary.put("totalLoss", round(totalLoss));
        summary.put("totalFee", round(totalFee));
        summary.put("avgLoss", round(allLosses.isEmpty() ? 0 : totalLoss / allLosses.size()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("summary", summary);
        return result;
    }

    private Map<String, Object> lossRow(OkxOrder o) {
        BigDecimal pnl = zero(o.getPnl());
        BigDecimal fee = zero(o.getFee());
        BigDecimal net = pnl.add(fee);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", o.getId());
        row.put("ordId", o.getOrdId());
        row.put("time", settledAt(o).toString().replace("T", " "));
        row.put("instId", o.getInstId());
        row.put("instType", o.getInstType());
        row.put("side", o.getSide());
        row.put("posSide", o.getPosSide());
        row.put("ordType", o.getOrdType());
        row.put("lever", o.getLever());
        row.put("createTime", o.getCreateTime() != null ? o.getCreateTime().toString().replace("T", " ") : null);
        row.put("updateTime", o.getUpdateTime() != null ? o.getUpdateTime().toString().replace("T", " ") : null);
        row.put("sz", round(o.getSz()));
        row.put("px", round(o.getPx()));
        row.put("avgPx", round(o.getAvgPx()));
        row.put("fillSz", round(o.getFillSz()));
        row.put("pnl", round(pnl));
        row.put("fee", round(fee));
        row.put("netPnl", round(net));
        row.put("lossAmount", round(net.abs()));
        BigDecimal notional = notional(o);
        row.put("notional", round(notional));
        row.put("lossPct", notional.compareTo(BigDecimal.ZERO) > 0
                ? round(net.abs().divide(notional, 8, java.math.RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)))
                : 0);
        row.put("holdingMinutes", o.getHoldingMinutes());
        row.put("isLiquidation", Objects.equals(o.getIsLiquidation(), 1));
        return row;
    }

    private BigDecimal notional(OkxOrder o) {
        BigDecimal price = firstPositive(o.getAvgPx(), o.getPx());
        BigDecimal size = firstPositive(o.getFillSz(), o.getSz());
        return price.multiply(size).abs();
    }

    private BigDecimal firstPositive(BigDecimal first, BigDecimal second) {
        BigDecimal a = zero(first);
        if (a.compareTo(BigDecimal.ZERO) > 0) return a;
        BigDecimal b = zero(second);
        return b.compareTo(BigDecimal.ZERO) > 0 ? b : BigDecimal.ZERO;
    }

    private BigDecimal netPnl(OkxOrder o) {
        return zero(o.getPnl()).add(zero(o.getFee()));
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private LocalDateTime settledAt(OkxOrder o) {
        return o.getUpdateTime() != null ? o.getUpdateTime() : o.getCreateTime();
    }

    private Double round(BigDecimal value) {
        return value == null ? null : round(value.doubleValue());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
