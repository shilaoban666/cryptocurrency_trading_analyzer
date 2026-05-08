package com.okx.analyzer.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class BalanceDto {
    private double tradingBalance;   // 交易账户总权益 USDT
    private double fundingBalance;   // 资金账户 USDT
    private double totalBalance;     // 总余额
    private List<String> dates;      // 余额曲线日期
    private List<Double> curve;      // 余额曲线数据（基于历史PnL推算）
}
