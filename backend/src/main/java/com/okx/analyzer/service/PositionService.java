package com.okx.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.okx.analyzer.dto.PositionDto;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final OkxApiService okxApiService;
    private final OkxOrderRepository orderRepo;

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public List<PositionDto> currentPositions() {
        JsonNode data = okxApiService.get("/api/v5/account/positions?instType=SWAP");
        List<OkxOrder> orders = orderRepo.findAllFilledOrders();
        List<PositionDto> result = new ArrayList<>();

        if (data == null || !data.isArray()) {
            return result;
        }

        for (JsonNode node : data) {
            double pos = d(node, "pos");
            if (Math.abs(pos) < 0.00000001) {
                continue;
            }

            String instId = node.path("instId").asText("");
            String posSide = node.path("posSide").asText("net");
            String direction = direction(posSide, pos);
            double markPx = firstPositive(d(node, "markPx"), fetchLastPrice(instId));
            double avgPx = firstPositive(d(node, "avgPx"), d(node, "openAvgPx"));
            double liqPx = d(node, "liqPx");
            double upl = d(node, "upl");
            double uplRatioPct = d(node, "uplRatio") * 100.0;
            double notional = Math.abs(pos) * markPx;
            LocalDateTime openTime = estimateOpenTime(orders, instId, posSide, direction);
            if (openTime == null) {
                openTime = timeFromMs(node.path("cTime").asText(""));
            }
            if (openTime == null) {
                openTime = timeFromMs(node.path("uTime").asText(""));
            }
            long holdingMinutes = openTime == null ? 0 : Math.max(0, ChronoUnit.MINUTES.between(openTime, LocalDateTime.now(ZONE)));
            int addCount = estimateAddCount(orders, instId, posSide, direction, openTime);
            String trend = marketTrend(instId);
            String alignment = isAligned(direction, trend) ? "顺势" : "逆势/背离";
            String divergence = divergence(upl, direction, trend, addCount);
            double liqDistance = markPx > 0 && liqPx > 0 ? Math.abs(markPx - liqPx) / markPx * 100.0 : 0;

            result.add(PositionDto.builder()
                    .instId(instId)
                    .posSide(posSide)
                    .direction(direction)
                    .size(pos)
                    .avgPx(r2(avgPx))
                    .markPx(r2(markPx))
                    .upl(r2(upl))
                    .uplRatioPct(r2(uplRatioPct))
                    .notional(r2(notional))
                    .liqPx(r2(liqPx))
                    .lever(node.path("lever").asText(""))
                    .marginMode(node.path("mgnMode").asText(""))
                    .holdingMinutes(holdingMinutes)
                    .addCount(addCount)
                    .addStatus(addCount > 1 ? "疑似补仓" : "单次建仓")
                    .marketTrend(trend)
                    .alignment(alignment)
                    .divergence(divergence)
                    .liquidationDistancePct(r2(liqDistance))
                    .build());
        }

        return result;
    }

    private LocalDateTime estimateOpenTime(List<OkxOrder> orders, String instId, String posSide, String direction) {
        String openSide = "long".equals(direction) ? "buy" : "sell";
        String closeSide = "long".equals(direction) ? "sell" : "buy";
        LocalDateTime latestClose = null;

        for (OkxOrder order : orders) {
            if (!samePosition(order, instId, posSide)) continue;
            if (order.getIsWin() != null && closeSide.equalsIgnoreCase(order.getSide())) {
                latestClose = order.getCreateTime();
            }
        }

        LocalDateTime firstOpen = null;
        for (OkxOrder order : orders) {
            if (!samePosition(order, instId, posSide)) continue;
            if (!openSide.equalsIgnoreCase(order.getSide())) continue;
            if (latestClose != null && !order.getCreateTime().isAfter(latestClose)) continue;
            if (firstOpen == null || order.getCreateTime().isBefore(firstOpen)) {
                firstOpen = order.getCreateTime();
            }
        }
        return firstOpen;
    }

    private int estimateAddCount(List<OkxOrder> orders, String instId, String posSide, String direction, LocalDateTime openTime) {
        if (openTime == null) return 0;
        String openSide = "long".equals(direction) ? "buy" : "sell";
        int count = 0;
        for (OkxOrder order : orders) {
            if (!samePosition(order, instId, posSide)) continue;
            if (!openSide.equalsIgnoreCase(order.getSide())) continue;
            if (!order.getCreateTime().isBefore(openTime)) {
                count++;
            }
        }
        return count;
    }

    private boolean samePosition(OkxOrder order, String instId, String posSide) {
        if (!Objects.equals(order.getInstId(), instId)) return false;
        String orderSide = order.getPosSide() == null || order.getPosSide().isBlank() ? "net" : order.getPosSide();
        String targetSide = posSide == null || posSide.isBlank() ? "net" : posSide;
        return "net".equals(orderSide) || "net".equals(targetSide) || Objects.equals(orderSide, targetSide);
    }

    private String marketTrend(String instId) {
        try {
            JsonNode candles = okxApiService.getPublic("/api/v5/market/candles?instId=" + instId + "&bar=1H&limit=80");
            if (candles == null || !candles.isArray() || candles.size() < 30) return "震荡";
            List<Double> closes = new ArrayList<>();
            for (int i = candles.size() - 1; i >= 0; i--) {
                closes.add(candles.get(i).get(4).asDouble(0));
            }
            double ema20 = ema(closes, 20);
            double ema50 = ema(closes, 50);
            double last = closes.get(closes.size() - 1);
            if (last > ema20 && ema20 > ema50) return "上行";
            if (last < ema20 && ema20 < ema50) return "下行";
            return "震荡";
        } catch (Exception e) {
            return "未知";
        }
    }

    private double fetchLastPrice(String instId) {
        try {
            JsonNode data = okxApiService.getPublic("/api/v5/market/ticker?instId=" + instId);
            if (data != null && data.isArray() && !data.isEmpty()) {
                return data.get(0).path("last").asDouble(0);
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String direction(String posSide, double pos) {
        if ("long".equalsIgnoreCase(posSide)) return "long";
        if ("short".equalsIgnoreCase(posSide)) return "short";
        return pos >= 0 ? "long" : "short";
    }

    private boolean isAligned(String direction, String trend) {
        return ("long".equals(direction) && "上行".equals(trend)) || ("short".equals(direction) && "下行".equals(trend));
    }

    private String divergence(double upl, String direction, String trend, int addCount) {
        boolean aligned = isAligned(direction, trend);
        if (upl >= 0 && aligned) return "盈利且顺势";
        if (upl < 0 && !aligned && addCount > 1) return "亏损补仓且背离";
        if (upl < 0 && !aligned) return "亏损且方向背离";
        if (upl < 0 && addCount > 1) return "亏损补仓";
        return "需要观察";
    }

    private LocalDateTime timeFromMs(String msText) {
        if (msText == null || msText.isBlank()) return null;
        try {
            long ms = Long.parseLong(msText);
            if (ms <= 0) return null;
            return Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDateTime();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double ema(List<Double> values, int period) {
        if (values.isEmpty()) return 0;
        double k = 2.0 / (period + 1);
        double ema = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            ema = values.get(i) * k + ema * (1 - k);
        }
        return ema;
    }

    private double d(JsonNode node, String field) {
        return node.path(field).asDouble(0);
    }

    private double firstPositive(double a, double b) {
        return a > 0 ? a : b;
    }

    private double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
