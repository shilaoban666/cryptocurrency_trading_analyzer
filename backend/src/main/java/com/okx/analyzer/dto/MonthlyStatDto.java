package com.okx.analyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MonthlyStatDto {
    private String month;      // "2024-01"
    private int    trades;
    private double winRate;
    private double totalPnl;
    private double maxDrawdown;
}
