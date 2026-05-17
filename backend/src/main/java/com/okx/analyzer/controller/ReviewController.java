package com.okx.analyzer.controller;

import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.entity.OrderReview;
import com.okx.analyzer.repository.OkxOrderRepository;
import com.okx.analyzer.repository.OrderReviewRepository;
import com.okx.analyzer.service.LossAiAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final OkxOrderRepository orderRepo;
    private final OrderReviewRepository reviewRepo;
    private final LossAiAnalysisService lossAiAnalysisService;

    /** 复盘订单列表（仅已关仓单，含复盘状态与指标数据） */
    @GetMapping("/orders")
    public Map<String, Object> orders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) Integer isWin,
            @RequestParam(required = false) String reviewed) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        List<OkxOrder> base = orderRepo.findAllClosedOrders().stream()
                .filter(o -> instId == null || instId.isBlank() || instId.equals(o.getInstId()))
                .filter(o -> isWin == null || Objects.equals(o.getIsWin(), isWin))
                .sorted(Comparator.comparing(OkxOrder::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        List<String> ordIds = base.stream().map(OkxOrder::getOrdId).toList();
        Map<String, OrderReview> reviewMap = ordIds.isEmpty()
                ? Map.of()
                : reviewRepo.findAllByOrdIdIn(ordIds).stream()
                        .collect(Collectors.toMap(OrderReview::getOrdId, r -> r, (a, b) -> a));

        List<Map<String, Object>> filtered = base.stream()
                .map(o -> reviewRow(o, reviewMap.get(o.getOrdId())))
                .filter(m -> {
                    if ("yes".equals(reviewed)) return Boolean.TRUE.equals(m.get("hasReview"));
                    if ("no".equals(reviewed)) return !Boolean.TRUE.equals(m.get("hasReview"));
                    return true;
                })
                .toList();

        int total = filtered.size();
        int from = Math.min(safePage * safeSize, total);
        int to = Math.min(from + safeSize, total);
        return Map.of("list", filtered.subList(from, to), "total", total);
    }

    /** 保存/更新复盘（含指标信号） */
    @PostMapping("/{ordId}")
    public Map<String, Object> save(
            @PathVariable String ordId,
            @RequestBody Map<String, Object> body) {

        OrderReview review = reviewRepo.findByOrdId(ordId).orElseGet(() -> {
            OrderReview r = new OrderReview();
            r.setOrdId(ordId);
            return r;
        });

        applyField(body, "reviewText",  v -> review.setReviewText((String) v));
        applyList(body,  "reasons",     review::setReasons);
        applyList(body,  "emaSignals",  review::setEmaSignals);
        applyList(body,  "kdjSignals",  review::setKdjSignals);
        applyList(body,  "macdSignals", review::setMacdSignals);

        OrderReview saved = reviewRepo.save(review);
        return Map.of(
                "reviewText",  saved.getReviewText()  != null ? saved.getReviewText() : "",
                "reasons",     splitField(saved.getReasons()),
                "emaSignals",  splitField(saved.getEmaSignals()),
                "kdjSignals",  splitField(saved.getKdjSignals()),
                "macdSignals", splitField(saved.getMacdSignals()),
                "updatedAt",   saved.getUpdatedAt()
        );
    }

    /** 复盘统计：原因 + 指标信号分布 + 进度 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<OkxOrder> closedOrders = orderRepo.findAllClosedOrders();
        List<OrderReview> reviews = reviewRepo.findAll();
        List<String> ordIds = reviews.stream().map(OrderReview::getOrdId).toList();
        Map<String, OkxOrder> orderMap = ordIds.isEmpty()
                ? Map.of()
                : orderRepo.findAllByOrdIdIn(ordIds).stream()
                        .collect(Collectors.toMap(OkxOrder::getOrdId, o -> o, (a, b) -> a));
        Map<String, OrderReview> reviewMap = reviews.stream()
                .collect(Collectors.toMap(OrderReview::getOrdId, r -> r, (a, b) -> a));

        Map<String, Long> winReasons   = new LinkedHashMap<>();
        Map<String, Long> lossReasons  = new LinkedHashMap<>();
        Map<String, Long> emaStats     = new LinkedHashMap<>();
        Map<String, Long> kdjStats     = new LinkedHashMap<>();
        Map<String, Long> macdStats    = new LinkedHashMap<>();
        Map<String, Long> lossCombos   = new LinkedHashMap<>();

        for (OrderReview r : reviews) {
            OkxOrder order = orderMap.get(r.getOrdId());
            if (order == null || order.getIsWin() == null) continue;

            // 原因统计
            countSignals(r.getReasons(), order.getIsWin() == 1 ? winReasons : lossReasons);

            // 指标统计（仅亏损单）
            if (order.getIsWin() == 0) {
                countSignals(r.getEmaSignals(),  emaStats);
                countSignals(r.getKdjSignals(),  kdjStats);
                countSignals(r.getMacdSignals(), macdStats);
                countLossCombos(r, lossCombos);
            }
        }

        long totalClosed = closedOrders.size();
        long winCount = closedOrders.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
        long lossCount = closedOrders.stream().filter(o -> Objects.equals(o.getIsWin(), 0)).count();
        long reviewedCount = closedOrders.stream().filter(o -> hasReview(reviewMap.get(o.getOrdId()))).count();
        long reviewedWinCount = closedOrders.stream()
                .filter(o -> Objects.equals(o.getIsWin(), 1) && hasReview(reviewMap.get(o.getOrdId()))).count();
        long reviewedLossCount = closedOrders.stream()
                .filter(o -> Objects.equals(o.getIsWin(), 0) && hasReview(reviewMap.get(o.getOrdId()))).count();

        List<OkxOrder> lossOrders = closedOrders.stream()
                .filter(o -> netPnl(o) < 0)
                .toList();
        long netLossCount = lossOrders.size();
        long netWinCount = closedOrders.stream().filter(o -> netPnl(o) > 0).count();
        long reviewedNetLossCount = lossOrders.stream().filter(o -> hasReview(reviewMap.get(o.getOrdId()))).count();
        long reviewedNetWinCount = closedOrders.stream()
                .filter(o -> netPnl(o) > 0 && hasReview(reviewMap.get(o.getOrdId()))).count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("winReasons", sortDesc(winReasons));
        result.put("lossReasons", sortDesc(lossReasons));
        result.put("emaSignals", sortDesc(emaStats));
        result.put("kdjSignals", sortDesc(kdjStats));
        result.put("macdSignals", sortDesc(macdStats));
        result.put("lossSignalCombos", sortDesc(lossCombos));
        result.put("reviewedCount", reviewedCount);
        result.put("totalClosedCount", totalClosed);
        result.put("winCount", winCount);
        result.put("lossCount", lossCount);
        result.put("closedWinRate", rate(winCount, totalClosed));
        result.put("netWinCount", netWinCount);
        result.put("netLossCount", netLossCount);
        result.put("netWinRate", rate(netWinCount, totalClosed));
        result.put("reviewedWinCount", reviewedWinCount);
        result.put("reviewedLossCount", reviewedLossCount);
        result.put("reviewedWinRate", rate(reviewedWinCount, reviewedCount));
        result.put("reviewCoverage", rate(reviewedCount, totalClosed));
        result.put("lossReviewCoverage", rate(reviewedNetLossCount, netLossCount));
        result.put("totalPnl", round(closedOrders.stream().mapToDouble(this::pnl).sum()));
        result.put("totalFee", round(closedOrders.stream().mapToDouble(this::fee).sum()));
        result.put("netPnl", round(closedOrders.stream().mapToDouble(this::netPnl).sum()));
        result.put("totalWinAmount", round(closedOrders.stream().mapToDouble(o -> Math.max(0, netPnl(o))).sum()));
        result.put("totalLossAmount", round(lossOrders.stream().mapToDouble(this::lossAmount).sum()));
        result.put("avgWin", round(avg(closedOrders.stream().filter(o -> netPnl(o) > 0).mapToDouble(this::netPnl).toArray())));
        result.put("avgLoss", round(avg(lossOrders.stream().mapToDouble(this::lossAmount).toArray())));
        result.put("maxLoss", round(lossOrders.stream().mapToDouble(this::lossAmount).max().orElse(0)));
        result.put("avgWinHoldingMinutes", round(avg(closedOrders.stream()
                .filter(o -> netPnl(o) > 0 && o.getHoldingMinutes() != null)
                .mapToDouble(OkxOrder::getHoldingMinutes).toArray())));
        result.put("avgLossHoldingMinutes", round(avg(lossOrders.stream()
                .filter(o -> o.getHoldingMinutes() != null)
                .mapToDouble(OkxOrder::getHoldingMinutes).toArray())));
        result.put("reviewMatrix", reviewMatrix(netWinCount, netLossCount, reviewedNetWinCount, reviewedNetLossCount));
        result.put("lossAmountBuckets", lossAmountBuckets(lossOrders));
        result.put("lossByLeverage", lossByLeverage(closedOrders));
        result.put("directionStats", directionStats(closedOrders));
        result.put("hourStats", hourStats(closedOrders));
        result.put("weekdayStats", weekdayStats(closedOrders));
        result.put("monthlyStats", monthlyStats(closedOrders, reviewMap));
        result.put("worstLossOrders", worstLossOrders(lossOrders, 12));
        return result;
    }

    @PostMapping("/loss-ai-analysis")
    public Map<String, Object> lossAiAnalysis() {
        return lossAiAnalysisService.analyzeLosses();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<String> splitField(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void countSignals(String csv, Map<String, Long> target) {
        if (csv == null || csv.isBlank()) return;
        for (String s : csv.split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) target.merge(v, 1L, Long::sum);
        }
    }

    private void applyField(Map<String, Object> body, String key, java.util.function.Consumer<Object> setter) {
        if (body.containsKey(key)) setter.accept(body.get(key));
    }

    @SuppressWarnings("unchecked")
    private void applyList(Map<String, Object> body, String key, java.util.function.Consumer<String> setter) {
        if (!body.containsKey(key)) return;
        List<String> list = (List<String>) body.get(key);
        setter.accept(list == null || list.isEmpty() ? null : String.join(",", list));
    }

    private Map<String, Long> sortDesc(Map<String, Long> map) {
        return map.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> reviewRow(OkxOrder o, OrderReview r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("ordId", o.getOrdId());
        item.put("instId", o.getInstId());
        item.put("posSide", o.getPosSide());
        item.put("pnl", o.getPnl());
        item.put("fee", o.getFee());
        item.put("netPnl", round(netPnl(o)));
        item.put("lever", o.getLever());
        item.put("createTime", o.getCreateTime());
        item.put("holdingMinutes", o.getHoldingMinutes());
        item.put("isWin", o.getIsWin());
        item.put("hasReview", hasReview(r));
        item.put("reviewText", r != null ? r.getReviewText() : null);
        item.put("reasons", splitField(r == null ? null : r.getReasons()));
        item.put("emaSignals", splitField(r == null ? null : r.getEmaSignals()));
        item.put("kdjSignals", splitField(r == null ? null : r.getKdjSignals()));
        item.put("macdSignals", splitField(r == null ? null : r.getMacdSignals()));
        return item;
    }

    private boolean hasReview(OrderReview r) {
        return r != null &&
                (isNotBlank(r.getReviewText()) || isNotBlank(r.getReasons())
                 || isNotBlank(r.getEmaSignals()) || isNotBlank(r.getKdjSignals())
                 || isNotBlank(r.getMacdSignals()));
    }

    private void countLossCombos(OrderReview r, Map<String, Long> target) {
        List<String> reasons = splitField(r.getReasons());
        List<String> signals = new ArrayList<>();
        signals.addAll(splitField(r.getEmaSignals()));
        signals.addAll(splitField(r.getKdjSignals()));
        signals.addAll(splitField(r.getMacdSignals()));
        if (reasons.isEmpty() && signals.isEmpty()) return;
        if (reasons.isEmpty()) {
            signals.forEach(s -> target.merge("未标原因 / " + s, 1L, Long::sum));
            return;
        }
        if (signals.isEmpty()) {
            reasons.forEach(reason -> target.merge(reason + " / 未标指标", 1L, Long::sum));
            return;
        }
        for (String reason : reasons) {
            signals.stream().limit(3).forEach(signal -> target.merge(reason + " / " + signal, 1L, Long::sum));
        }
    }

    private List<Map<String, Object>> reviewMatrix(long winCount, long lossCount, long reviewedWinCount, long reviewedLossCount) {
        return List.of(
                matrixRow("盈利单", winCount, reviewedWinCount),
                matrixRow("亏损单", lossCount, reviewedLossCount)
        );
    }

    private Map<String, Object> matrixRow(String name, long total, long reviewed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", name);
        row.put("reviewed", reviewed);
        row.put("pending", Math.max(0, total - reviewed));
        row.put("total", total);
        row.put("coverage", rate(reviewed, total));
        return row;
    }

    private List<Map<String, Object>> lossAmountBuckets(List<OkxOrder> losses) {
        double[] edges = {0, 50, 100, 250, 500, 1000, 2500, Double.MAX_VALUE};
        String[] labels = {"0-50U", "50-100U", "100-250U", "250-500U", "500-1000U", "1000-2500U", "2500U+"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            double lo = edges[i], hi = edges[i + 1];
            List<OkxOrder> bucket = losses.stream()
                    .filter(o -> lossAmount(o) >= lo && lossAmount(o) < hi)
                    .toList();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("bucket", labels[i]);
            row.put("count", bucket.size());
            row.put("totalLoss", round(bucket.stream().mapToDouble(this::lossAmount).sum()));
            row.put("avgHoldingMinutes", round(avg(bucket.stream()
                    .filter(o -> o.getHoldingMinutes() != null)
                    .mapToDouble(OkxOrder::getHoldingMinutes).toArray())));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> lossByLeverage(List<OkxOrder> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(o -> leverLabel(o.getLever()), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> parseLever(e.getKey())))
                .map(e -> {
                    List<OkxOrder> group = e.getValue();
                    List<OkxOrder> losses = group.stream().filter(o -> netPnl(o) < 0).toList();
                    long wins = group.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("lever", e.getKey());
                    row.put("tradeCount", group.size());
                    row.put("lossCount", losses.size());
                    row.put("winRate", rate(wins, group.size()));
                    row.put("totalLoss", round(losses.stream().mapToDouble(this::lossAmount).sum()));
                    row.put("avgLoss", round(avg(losses.stream().mapToDouble(this::lossAmount).toArray())));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> directionStats(List<OkxOrder> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(o -> blankToDefault(o.getPosSide(), "net"), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    List<OkxOrder> group = e.getValue();
                    List<OkxOrder> losses = group.stream().filter(o -> netPnl(o) < 0).toList();
                    long wins = group.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("direction", e.getKey());
                    row.put("tradeCount", group.size());
                    row.put("winRate", rate(wins, group.size()));
                    row.put("netPnl", round(group.stream().mapToDouble(this::netPnl).sum()));
                    row.put("totalLoss", round(losses.stream().mapToDouble(this::lossAmount).sum()));
                    row.put("avgHoldingMinutes", round(avg(group.stream()
                            .filter(o -> o.getHoldingMinutes() != null)
                            .mapToDouble(OkxOrder::getHoldingMinutes).toArray())));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> hourStats(List<OkxOrder> orders) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            final int h = hour;
            List<OkxOrder> group = orders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().getHour() == h)
                    .toList();
            List<OkxOrder> losses = group.stream().filter(o -> netPnl(o) < 0).toList();
            long wins = group.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("hour", hour);
            row.put("tradeCount", group.size());
            row.put("winRate", rate(wins, group.size()));
            row.put("netPnl", round(group.stream().mapToDouble(this::netPnl).sum()));
            row.put("totalLoss", round(losses.stream().mapToDouble(this::lossAmount).sum()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> weekdayStats(List<OkxOrder> orders) {
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            List<OkxOrder> group = orders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().getDayOfWeek().getValue() == d)
                    .toList();
            List<OkxOrder> losses = group.stream().filter(o -> netPnl(o) < 0).toList();
            long wins = group.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("weekday", names[day - 1]);
            row.put("tradeCount", group.size());
            row.put("winRate", rate(wins, group.size()));
            row.put("netPnl", round(group.stream().mapToDouble(this::netPnl).sum()));
            row.put("totalLoss", round(losses.stream().mapToDouble(this::lossAmount).sum()));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> monthlyStats(List<OkxOrder> orders, Map<String, OrderReview> reviewMap) {
        return orders.stream()
                .filter(o -> o.getCreateTime() != null)
                .collect(Collectors.groupingBy(o -> o.getCreateTime().toLocalDate().withDayOfMonth(1).toString().substring(0, 7),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    List<OkxOrder> group = e.getValue();
                    long wins = group.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
                    long reviewed = group.stream().filter(o -> hasReview(reviewMap.get(o.getOrdId()))).count();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("month", e.getKey());
                    row.put("tradeCount", group.size());
                    row.put("winRate", rate(wins, group.size()));
                    row.put("reviewCoverage", rate(reviewed, group.size()));
                    row.put("netPnl", round(group.stream().mapToDouble(this::netPnl).sum()));
                    row.put("avgPnl", round(avg(group.stream().mapToDouble(this::netPnl).toArray())));
                    row.put("lossCount", group.stream().filter(o -> netPnl(o) < 0).count());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> worstLossOrders(List<OkxOrder> losses, int limit) {
        return losses.stream()
                .sorted(Comparator.comparingDouble(this::lossAmount).reversed())
                .limit(limit)
                .map(o -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("ordId", o.getOrdId());
                    row.put("instId", o.getInstId());
                    row.put("posSide", o.getPosSide());
                    row.put("lever", leverLabel(o.getLever()));
                    row.put("loss", round(lossAmount(o)));
                    row.put("netPnl", round(netPnl(o)));
                    row.put("holdingMinutes", o.getHoldingMinutes());
                    row.put("createTime", o.getCreateTime());
                    return row;
                })
                .toList();
    }

    private double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0.0 : round((double) numerator / denominator);
    }

    private double avg(double[] values) {
        return values.length == 0 ? 0 : Arrays.stream(values).average().orElse(0);
    }

    private double lossAmount(OkxOrder order) {
        double net = netPnl(order);
        if (net < 0) return Math.abs(net);
        return Math.abs(Math.min(0, pnl(order)));
    }

    private double netPnl(OkxOrder order) {
        return pnl(order) + fee(order);
    }

    private double pnl(OkxOrder order) {
        return bd(order.getPnl());
    }

    private double fee(OkxOrder order) {
        return bd(order.getFee());
    }

    private double bd(BigDecimal value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String leverLabel(String lever) {
        String value = blankToDefault(lever, "未知").trim();
        if ("未知".equals(value)) return value;
        return value.endsWith("x") || value.endsWith("X") ? value.toLowerCase() : value + "x";
    }

    private double parseLever(String lever) {
        try {
            return Double.parseDouble(lever.replace("x", "").replace("X", ""));
        } catch (Exception ignored) {
            return Double.MAX_VALUE;
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }
}
