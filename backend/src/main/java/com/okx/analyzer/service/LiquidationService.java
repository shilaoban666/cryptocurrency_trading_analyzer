package com.okx.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.okx.analyzer.dto.LiquidationDto;
import com.okx.analyzer.entity.LiquidationRecord;
import com.okx.analyzer.repository.LiquidationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidationService {

    private final OkxApiService okxApiService;
    private final LiquidationRecordRepository liqRepo;

    /** 从 OKX 账单 API 同步强平记录（type=8），返回新增条数 */
    @Transactional
    public int syncLiquidations() {
        List<JsonNode> bills = okxApiService.fetchAllBillsByType("8");

        int newCount = 0;
        Set<String> seen = new HashSet<>();

        for (JsonNode node : bills) {
            String billId = node.path("billId").asText();
            if (billId.isEmpty() || seen.contains(billId) || liqRepo.existsByBillId(billId)) {
                seen.add(billId);
                continue;
            }
            seen.add(billId);

            try {
                LiquidationRecord rec = parseBill(node);
                liqRepo.save(rec);
                newCount++;
            } catch (Exception e) {
                log.warn("[LiqSync] 解析失败 billId={}: {}", billId, e.getMessage());
            }
        }

        log.info("[LiqSync] 同步完成，新增 {} 条强平记录", newCount);
        return newCount;
    }

    /** 计算并返回爆仓统计 DTO */
    public LiquidationDto getStats() {
        List<LiquidationRecord> all = liqRepo.findAllByOrderByLiquidationTimeDesc();

        if (all.isEmpty()) {
            return LiquidationDto.builder()
                    .totalCount(0).totalLoss(0).avgLoss(0).maxSingleLoss(0)
                    .symbolNames(List.of()).symbolCounts(List.of()).symbolLoss(List.of())
                    .hourlyCount(Collections.nCopies(24, 0))
                    .dailyDates(List.of()).dailyCounts(List.of()).dailyLoss(List.of())
                    .records(List.of())
                    .build();
        }

        int total = all.size();
        double totalLoss = all.stream().mapToDouble(r -> safeDouble(r.getPnl())).sum();
        double maxLoss   = all.stream().mapToDouble(r -> safeDouble(r.getPnl())).min().orElse(0);

        // 按品种分组
        Map<String, List<LiquidationRecord>> bySymbol = all.stream()
                .collect(Collectors.groupingBy(LiquidationRecord::getInstId));

        String mostLiqSym = bySymbol.entrySet().stream()
                .max(Comparator.comparingInt(e -> e.getValue().size()))
                .map(Map.Entry::getKey).orElse("");
        double mostLiqLoss = bySymbol.getOrDefault(mostLiqSym, List.of())
                .stream().mapToDouble(r -> safeDouble(r.getPnl())).sum();

        List<String>  symNames  = new ArrayList<>();
        List<Integer> symCounts = new ArrayList<>();
        List<Double>  symLoss   = new ArrayList<>();
        bySymbol.entrySet().stream()
                .sorted((a, b) -> b.getValue().size() - a.getValue().size())
                .forEach(e -> {
                    symNames.add(e.getKey());
                    symCounts.add(e.getValue().size());
                    symLoss.add(r2(e.getValue().stream().mapToDouble(r -> safeDouble(r.getPnl())).sum()));
                });

        // 按小时统计（0-23）
        List<Integer> hourlyCount = new ArrayList<>(Collections.nCopies(24, 0));
        all.forEach(r -> {
            int h = r.getLiquidationTime().getHour();
            hourlyCount.set(h, hourlyCount.get(h) + 1);
        });

        // 按日期统计
        Map<String, List<LiquidationRecord>> byDay = all.stream()
                .collect(Collectors.groupingBy(r -> r.getLiquidationTime().toLocalDate().toString()));
        List<String> dailyDates = new ArrayList<>(byDay.keySet());
        Collections.sort(dailyDates);
        List<Integer> dailyCounts = dailyDates.stream().map(d -> byDay.get(d).size()).collect(Collectors.toList());
        List<Double>  dailyLoss   = dailyDates.stream()
                .map(d -> r2(byDay.get(d).stream().mapToDouble(r -> safeDouble(r.getPnl())).sum()))
                .collect(Collectors.toList());

        // 最近 50 条明细
        List<LiquidationDto.RecordDto> records = all.stream().limit(50)
                .map(r -> LiquidationDto.RecordDto.builder()
                        .time(r.getLiquidationTime().toString().replace("T", " "))
                        .instId(r.getInstId())
                        .posSide(r.getPosSide())
                        .pnl(r2(safeDouble(r.getPnl())))
                        .fee(r2(safeDouble(r.getFee())))
                        .px(r.getPx() != null ? r.getPx().doubleValue() : 0)
                        .sz(r.getSz() != null ? r.getSz().doubleValue() : 0)
                        .balanceAfter(r2(r.getBalanceAfter() != null ? r.getBalanceAfter().doubleValue() : 0))
                        .build())
                .collect(Collectors.toList());

        return LiquidationDto.builder()
                .totalCount(total)
                .totalLoss(r2(totalLoss))
                .avgLoss(r2(total > 0 ? totalLoss / total : 0))
                .maxSingleLoss(r2(maxLoss))
                .mostLiquidatedSymbol(mostLiqSym)
                .mostLiquidatedSymbolLoss(r2(mostLiqLoss))
                .symbolNames(symNames)
                .symbolCounts(symCounts)
                .symbolLoss(symLoss)
                .hourlyCount(hourlyCount)
                .dailyDates(dailyDates)
                .dailyCounts(dailyCounts)
                .dailyLoss(dailyLoss)
                .records(records)
                .build();
    }

    private LiquidationRecord parseBill(JsonNode n) {
        LiquidationRecord r = new LiquidationRecord();
        r.setBillId(n.path("billId").asText());
        r.setInstId(n.path("instId").asText());
        r.setInstType(n.path("instType").asText());
        r.setPosSide(n.path("posSide").asText("net"));
        r.setPnl(decimal(n, "pnl"));
        r.setFee(decimal(n, "fee"));
        r.setSz(decimal(n, "sz"));
        r.setPx(decimal(n, "px"));
        r.setBalanceAfter(decimal(n, "bal"));

        long ts = n.path("ts").asLong();
        r.setLiquidationTime(Instant.ofEpochMilli(ts)
                .atZone(ZoneId.of("Asia/Shanghai")).toLocalDateTime());
        return r;
    }

    private BigDecimal decimal(JsonNode n, String field) {
        String v = n.path(field).asText("").trim();
        if (v.isEmpty()) return BigDecimal.ZERO;
        try { return new BigDecimal(v); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private double safeDouble(BigDecimal bd) {
        return bd != null ? bd.doubleValue() : 0.0;
    }

    private double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
