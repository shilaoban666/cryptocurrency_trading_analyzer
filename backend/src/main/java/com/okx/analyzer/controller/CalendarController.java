package com.okx.analyzer.controller;

import com.okx.analyzer.entity.DailyJournal;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.DailyJournalRepository;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final OkxOrderRepository orderRepo;
    private final DailyJournalRepository journalRepo;

    @GetMapping
    public Map<String, Object> month(@RequestParam(required = false) String month) {
        YearMonth ym = parseMonth(month);
        LocalDate startDate = ym.atDay(1);
        LocalDate endDate = ym.atEndOfMonth();
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.MAX);

        List<OkxOrder> orders = orderRepo.findClosedOrdersSettledBetween(start, end);
        Map<String, DayStat> dayStats = new TreeMap<>();
        Map<String, CategoryStat> instStats = new HashMap<>();
        Map<String, CategoryStat> sideStats = new HashMap<>();

        for (OkxOrder order : orders) {
            LocalDate date = Optional.ofNullable(order.getUpdateTime())
                    .orElse(order.getCreateTime())
                    .toLocalDate();
            String dateKey = date.toString();
            double pnl = number(order.getPnl()) + number(order.getFee());
            String instId = nullToDefault(order.getInstId(), "未知品种");
            String side = nullToDefault(order.getPosSide(), "net");

            dayStats.computeIfAbsent(dateKey, ignored -> new DayStat(dateKey)).add(order, pnl);
            instStats.computeIfAbsent(instId, CategoryStat::new).add(pnl);
            sideStats.computeIfAbsent(side, CategoryStat::new).add(pnl);
        }

        Map<String, DailyJournal> journalMap = journalRepo.findByJournalDateBetweenOrderByJournalDateAsc(startDate, endDate)
                .stream()
                .collect(Collectors.toMap(j -> j.getJournalDate().toString(), j -> j));

        List<Map<String, Object>> days = new ArrayList<>();
        for (int day = 1; day <= endDate.getDayOfMonth(); day++) {
            String date = ym.atDay(day).toString();
            DayStat stat = dayStats.getOrDefault(date, new DayStat(date));
            DailyJournal journal = journalMap.get(date);
            days.add(stat.toMap(journal));
        }

        double monthPnl = days.stream().mapToDouble(d -> ((Number) d.get("pnl")).doubleValue()).sum();
        double profit = days.stream().mapToDouble(d -> Math.max(0, ((Number) d.get("pnl")).doubleValue())).sum();
        double loss = days.stream().mapToDouble(d -> Math.min(0, ((Number) d.get("pnl")).doubleValue())).sum();
        long winDays = days.stream().filter(d -> ((Number) d.get("pnl")).doubleValue() > 0).count();
        long lossDays = days.stream().filter(d -> ((Number) d.get("pnl")).doubleValue() < 0).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("month", ym.toString());
        summary.put("pnl", round(monthPnl));
        summary.put("profit", round(profit));
        summary.put("loss", round(loss));
        summary.put("winDays", winDays);
        summary.put("lossDays", lossDays);
        summary.put("tradeDays", winDays + lossDays);
        summary.put("orderCount", orders.size());

        return Map.of(
                "summary", summary,
                "days", days,
                "instStats", sortStats(instStats),
                "sideStats", sortStats(sideStats)
        );
    }

    @GetMapping("/journal/{date}")
    public Map<String, Object> getJournal(@PathVariable String date) {
        LocalDate journalDate = LocalDate.parse(date);
        return journalRepo.findByJournalDate(journalDate)
                .map(this::journalMap)
                .orElseGet(() -> emptyJournal(journalDate));
    }

    @PostMapping("/journal/{date}")
    public Map<String, Object> saveJournal(@PathVariable String date, @RequestBody Map<String, Object> body) {
        LocalDate journalDate = LocalDate.parse(date);
        DailyJournal journal = journalRepo.findByJournalDate(journalDate).orElseGet(() -> {
            DailyJournal item = new DailyJournal();
            item.setJournalDate(journalDate);
            return item;
        });
        journal.setMood(toString(body.get("mood")));
        journal.setContent(toString(body.get("content")));
        journal.setTags(joinTags(body.get("tags")));
        return journalMap(journalRepo.save(journal));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        return YearMonth.parse(month);
    }

    private List<Map<String, Object>> sortStats(Map<String, CategoryStat> stats) {
        return stats.values().stream()
                .sorted(Comparator.comparingDouble((CategoryStat s) -> Math.abs(s.pnl)).reversed())
                .map(CategoryStat::toMap)
                .toList();
    }

    private Map<String, Object> journalMap(DailyJournal journal) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", journal.getJournalDate().toString());
        map.put("mood", journal.getMood());
        map.put("tags", splitTags(journal.getTags()));
        map.put("content", journal.getContent());
        map.put("updatedAt", journal.getUpdatedAt());
        return map;
    }

    private Map<String, Object> emptyJournal(LocalDate date) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("date", date.toString());
        map.put("mood", "");
        map.put("tags", List.of());
        map.put("content", "");
        map.put("updatedAt", null);
        return map;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private String joinTags(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).filter(s -> !s.isBlank()).collect(Collectors.joining(","));
        }
        return toString(value);
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String nullToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private double number(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private final class DayStat {
        private final String date;
        private double pnl;
        private double profit;
        private double loss;
        private int orderCount;
        private int winCount;
        private int lossCount;

        private DayStat(String date) {
            this.date = date;
        }

        private void add(OkxOrder order, double value) {
            pnl += value;
            orderCount++;
            if (value >= 0) {
                profit += value;
                winCount++;
            } else {
                loss += value;
                lossCount++;
            }
        }

        private Map<String, Object> toMap(DailyJournal journal) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("date", date);
            map.put("pnl", round(pnl));
            map.put("profit", round(profit));
            map.put("loss", round(loss));
            map.put("orderCount", orderCount);
            map.put("winCount", winCount);
            map.put("lossCount", lossCount);
            map.put("hasJournal", journal != null && journal.getContent() != null && !journal.getContent().isBlank());
            map.put("mood", journal != null ? journal.getMood() : "");
            return map;
        }
    }

    private final class CategoryStat {
        private final String name;
        private double pnl;
        private double profit;
        private double loss;
        private int count;

        private CategoryStat(String name) {
            this.name = name;
        }

        private void add(double value) {
            pnl += value;
            count++;
            if (value >= 0) profit += value;
            else loss += value;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("pnl", round(pnl));
            map.put("profit", round(profit));
            map.put("loss", round(loss));
            map.put("count", count);
            return map;
        }
    }
}
