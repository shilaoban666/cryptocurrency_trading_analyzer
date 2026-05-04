package com.okx.analyzer.controller;

import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.entity.OrderReview;
import com.okx.analyzer.repository.OkxOrderRepository;
import com.okx.analyzer.repository.OrderReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final OkxOrderRepository orderRepo;
    private final OrderReviewRepository reviewRepo;

    /** 复盘订单列表（仅已关仓单，含复盘状态与指标数据） */
    @GetMapping("/orders")
    public Map<String, Object> orders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) Integer isWin,
            @RequestParam(required = false) String reviewed) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createTime").descending());
        Page<OkxOrder> orderPage = orderRepo.findClosedWithFilter(instId, isWin, pageable);

        List<String> ordIds = orderPage.getContent().stream().map(OkxOrder::getOrdId).toList();
        Map<String, OrderReview> reviewMap = reviewRepo.findAllByOrdIdIn(ordIds)
                .stream().collect(Collectors.toMap(OrderReview::getOrdId, r -> r));

        List<Map<String, Object>> list = orderPage.getContent().stream().map(o -> {
            OrderReview r = reviewMap.get(o.getOrdId());
            boolean hasReview = r != null &&
                    ((r.getReviewText() != null && !r.getReviewText().isBlank())
                     || (r.getReasons()    != null && !r.getReasons().isBlank())
                     || (r.getEmaSignals() != null && !r.getEmaSignals().isBlank())
                     || (r.getKdjSignals() != null && !r.getKdjSignals().isBlank())
                     || (r.getMacdSignals()!= null && !r.getMacdSignals().isBlank()));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("ordId",          o.getOrdId());
            item.put("instId",         o.getInstId());
            item.put("posSide",        o.getPosSide());
            item.put("pnl",            o.getPnl());
            item.put("fee",            o.getFee());
            item.put("lever",          o.getLever());
            item.put("createTime",     o.getCreateTime());
            item.put("holdingMinutes", o.getHoldingMinutes());
            item.put("isWin",          o.getIsWin());
            item.put("hasReview",      hasReview);
            item.put("reviewText",     r != null ? r.getReviewText() : null);
            item.put("reasons",        splitField(r == null ? null : r.getReasons()));
            item.put("emaSignals",     splitField(r == null ? null : r.getEmaSignals()));
            item.put("kdjSignals",     splitField(r == null ? null : r.getKdjSignals()));
            item.put("macdSignals",    splitField(r == null ? null : r.getMacdSignals()));
            return item;
        }).toList();

        List<Map<String, Object>> filtered = list;
        long total = orderPage.getTotalElements();
        if ("yes".equals(reviewed)) {
            filtered = list.stream().filter(m -> Boolean.TRUE.equals(m.get("hasReview"))).toList();
            total = filtered.size();
        } else if ("no".equals(reviewed)) {
            filtered = list.stream().filter(m -> !Boolean.TRUE.equals(m.get("hasReview"))).toList();
            total = filtered.size();
        }

        return Map.of("list", filtered, "total", total);
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
        List<OrderReview> reviews = reviewRepo.findAll();
        List<String> ordIds = reviews.stream().map(OrderReview::getOrdId).toList();
        Map<String, OkxOrder> orderMap = orderRepo.findAllByOrdIdIn(ordIds)
                .stream().collect(Collectors.toMap(OkxOrder::getOrdId, o -> o));

        Map<String, Long> winReasons   = new LinkedHashMap<>();
        Map<String, Long> lossReasons  = new LinkedHashMap<>();
        Map<String, Long> emaStats     = new LinkedHashMap<>();
        Map<String, Long> kdjStats     = new LinkedHashMap<>();
        Map<String, Long> macdStats    = new LinkedHashMap<>();

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
            }
        }

        long totalClosed   = orderRepo.countClosedOrders();
        long reviewedCount = reviews.stream()
                .filter(r -> isNotBlank(r.getReviewText()) || isNotBlank(r.getReasons())
                          || isNotBlank(r.getEmaSignals())  || isNotBlank(r.getKdjSignals())
                          || isNotBlank(r.getMacdSignals()))
                .count();

        return Map.of(
                "winReasons",       sortDesc(winReasons),
                "lossReasons",      sortDesc(lossReasons),
                "emaSignals",       sortDesc(emaStats),
                "kdjSignals",       sortDesc(kdjStats),
                "macdSignals",      sortDesc(macdStats),
                "reviewedCount",    reviewedCount,
                "totalClosedCount", totalClosed
        );
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private List<String> splitField(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.asList(value.split(","));
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

    private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }
}
