package com.okx.analyzer.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class LiquidationDto {

    // ── 总览 ──
    private int    totalCount;
    private double totalLoss;
    private double avgLoss;
    private double maxSingleLoss;
    private String mostLiquidatedSymbol;
    private double mostLiquidatedSymbolLoss;

    // ── 按品种分布 ──
    private List<String>  symbolNames;
    private List<Integer> symbolCounts;
    private List<Double>  symbolLoss;

    // ── 按小时分布 ──
    private List<Integer> hourlyCount;

    // ── 按日期趋势 ──
    private List<String>  dailyDates;
    private List<Integer> dailyCounts;
    private List<Double>  dailyLoss;

    // ── 详细记录列表（最近50条）──
    private List<RecordDto> records;

    @Data
    @Builder
    public static class RecordDto {
        private String time;
        private String instId;
        private String posSide;
        private double pnl;
        private double fee;
        private double px;
        private double sz;
        private double balanceAfter;
    }
}
