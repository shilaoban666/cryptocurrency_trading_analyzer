package com.okx.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.okx.analyzer.dto.BalanceDto;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final OkxApiService okxApiService;
    private final OkxOrderRepository orderRepo;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public BalanceDto getBalance() {
        double tradingBalance = 0.0;
        double fundingBalance = 0.0;

        try {
            JsonNode data = okxApiService.fetchAccountBalance();
            if (data != null && data.isArray() && !data.isEmpty()) {
                // totalEq = 统一账户总权益（USD计价）
                tradingBalance = data.get(0).path("totalEq").asDouble(0);
            }
        } catch (Exception e) {
            log.warn("[Balance] 获取交易账户余额失败: {}", e.getMessage());
        }

        try {
            JsonNode data = okxApiService.fetchAssetBalance();
            if (data != null && data.isArray()) {
                for (JsonNode asset : data) {
                    if ("USDT".equals(asset.path("ccy").asText())) {
                        fundingBalance = asset.path("bal").asDouble(0);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Balance] 获取资金账户余额失败: {}", e.getMessage());
        }

        double totalBalance = tradingBalance + fundingBalance;

        // 用历史订单的净盈亏推算余额历史曲线
        List<OkxOrder> orders = orderRepo.findAllClosedOrders();
        List<String> dates = new ArrayList<>();
        List<Double> curve = new ArrayList<>();

        if (!orders.isEmpty()) {
            Map<String, Double> dailyNetPnl = new LinkedHashMap<>();
            for (OkxOrder o : orders) {
                String day = (o.getUpdateTime() != null ? o.getUpdateTime() : o.getCreateTime())
                        .format(DATE_FMT);
                double pnl = o.getPnl() != null ? o.getPnl().doubleValue() : 0;
                double fee = o.getFee() != null ? o.getFee().doubleValue() : 0;
                dailyNetPnl.merge(day, pnl + fee, Double::sum);
            }

            double totalNetPnl = orders.stream().mapToDouble(o -> {
                double p = o.getPnl() != null ? o.getPnl().doubleValue() : 0;
                double f = o.getFee() != null ? o.getFee().doubleValue() : 0;
                return p + f;
            }).sum();

            List<Map.Entry<String, Double>> sorted = new ArrayList<>(dailyNetPnl.entrySet());
            sorted.sort(Map.Entry.comparingByKey());

            double cumulative = 0;
            for (Map.Entry<String, Double> entry : sorted) {
                cumulative += entry.getValue();
                dates.add(entry.getKey());
                // 用当前余额减去总盈亏再加上截止该天的累计盈亏，还原历史余额
                curve.add(Math.max(0, totalBalance - totalNetPnl + cumulative));
            }
        }

        return BalanceDto.builder()
                .tradingBalance(tradingBalance)
                .fundingBalance(fundingBalance)
                .totalBalance(totalBalance)
                .dates(dates)
                .curve(curve)
                .build();
    }
}
