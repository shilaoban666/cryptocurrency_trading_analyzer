package com.okx.analyzer.service;

import com.okx.analyzer.dto.AnalysisDto;
import com.okx.analyzer.dto.MonthlyStatDto;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.LiquidationRecordRepository;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.*;

@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final OkxOrderRepository        orderRepo;
    private final LiquidationRecordRepository liqRepo;

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ── 主入口 ─────────────────────────────────────────────────────────────

    public AnalysisDto analyze(LocalDateTime start, LocalDateTime end) {
        List<OkxOrder> orders = (start != null && end != null)
                ? orderRepo.findClosedOrdersBetween(start, end)
                : orderRepo.findAllClosedOrders();

        if (orders.isEmpty()) return AnalysisDto.builder().build();

        backfillHoldingMinutes(orders);

        // 基础统计
        long   total      = orders.size();
        long   winCnt     = count(orders, 1);
        long   lossCnt    = count(orders, 0);
        double winR       = rate(orders, null);
        double tPnl       = sumPnl(orders);
        double tFee       = sumFee(orders);
        double aWin       = avgPnlFiltered(orders, 1);
        double aLoss      = avgPnlFiltered(orders, 0);
        double pFactor    = profitFactor(orders);
        double mDD        = maxDrawdown(orders);
        double mDDPct     = maxDrawdownPct(orders);

        // 日级盈亏 map（复用）
        Map<String, Double> dailyMap = buildDailyMap(orders);

        return AnalysisDto.builder()
                // 一、胜率 & 盈亏
                .totalTrades(total)
                .winCount(winCnt)
                .lossCount(lossCnt)
                .winRate(winR)
                .longWinRate(rate(orders, "long"))
                .shortWinRate(rate(orders, "short"))
                .totalPnl(tPnl)
                .totalFee(tFee)
                .netPnl(r2(tPnl + tFee))
                .avgPnl(r2(total > 0 ? tPnl / total : 0))
                .avgWin(aWin)
                .avgLoss(aLoss)
                .maxWin(r2(orders.stream().mapToDouble(o->d(o.getPnl())).max().orElse(0)))
                .maxLoss(r2(orders.stream().mapToDouble(o->d(o.getPnl())).min().orElse(0)))
                // 二、进阶量化
                .profitFactor(pFactor)
                .expectedValue(r2(winR * aWin + (1 - winR) * aLoss))
                .kellyCriterion(r2(pFactor > 0 ? (winR - (1-winR)/pFactor)*100 : 0))
                .sharpeRatio(sharpe(orders))
                .calmarRatio(mDDPct > 0 ? r2((tPnl+tFee) / mDDPct) : 0)
                .maxDrawdown(mDD)
                .maxDrawdownPct(mDDPct)
                .maxConsecWins(maxConsec(orders, 1))
                .maxConsecLosses(maxConsec(orders, 0))
                .maxConsecWinAmount(maxConsecAmount(orders, 1))
                .maxConsecLossAmount(maxConsecAmount(orders, 0))
                // 三、持仓
                .avgHoldingMinutes(avgHolding(orders, null))
                .avgWinHoldingMinutes(avgHolding(orders, 1))
                .avgLossHoldingMinutes(avgHolding(orders, 0))
                // 四、频率 & 日历
                .totalTradeDays(dailyMap.size())
                .dailyAvgTrades(r2(dailyMap.isEmpty() ? 0 : (double)total / dailyMap.size()))
                .bestDayPnl(dailyMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(0))
                .bestDayDate(dailyMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""))
                .worstDayPnl(dailyMap.values().stream().mapToDouble(Double::doubleValue).min().orElse(0))
                .worstDayDate(dailyMap.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""))
                .bestMonthPnl(monthlyPnlMap(orders).values().stream().mapToDouble(Double::doubleValue).max().orElse(0))
                .bestMonthDate(monthlyPnlMap(orders).entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""))
                .worstMonthPnl(monthlyPnlMap(orders).values().stream().mapToDouble(Double::doubleValue).min().orElse(0))
                .worstMonthDate(monthlyPnlMap(orders).entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""))
                .liquidationCount((int) liqRepo.count())
                // 五、时间序列
                .pnlDates(pnlDates(orders))
                .pnlCumulative(pnlCumulative(orders))
                .dailyDates(sortedKeys(dailyMap))
                .dailyPnl(sortedValues(dailyMap))
                .rollingWinRate(rollingWinRate(orders, 20))
                .rollingIndex(rollingIndex(orders, 20))
                // 六、时段
                .hourlyTradeCount(hourlyCount(orders))
                .hourlyWinRate(hourlyWinRate(orders))
                .hourlyTotalPnl(hourlyTotalPnl(orders))
                .weekdayTradeCount(weekdayCount(orders))
                .weekdayWinRate(weekdayWinRate(orders))
                .weekdayTotalPnl(weekdayTotalPnl(orders))
                .heatmapData(heatmap(orders))
                // 七、品种
                .symbolNames(symbolNames(orders))
                .symbolTradeCount(symbolTradeCount(orders))
                .symbolWinRate(symbolWinRate(orders))
                .symbolTotalPnl(symbolTotalPnl(orders))
                .symbolAvgHolding(symbolAvgHolding(orders))
                // 八、分布
                .holdingLabels(HOLDING_LABELS)
                .holdingCounts(holdingCounts(orders))
                .holdingWinRates(holdingWinRates(orders))
                .pnlRangeLabels(pnlRangeLabels(orders))
                .pnlRangeCounts(pnlRangeCounts(orders))
                // 九、杠杆 & 月度
                .leverWinRate(leverWinRate(orders))
                .leverTradeCount(leverTradeCount(orders))
                .monthlyStats(monthlyStats(orders))
                .build();
    }

    // ── 基础统计 ──────────────────────────────────────────────────────────

    private long count(List<OkxOrder> orders, int win) {
        return orders.stream().filter(o -> Objects.equals(o.getIsWin(), win)).count();
    }

    private double rate(List<OkxOrder> orders, String posSide) {
        List<OkxOrder> f = posSide == null ? orders
                : orders.stream().filter(o -> posSide.equals(o.getPosSide())).toList();
        if (f.isEmpty()) return 0.0;
        long w = f.stream().filter(o -> o.getIsWin() != null && o.getIsWin() == 1).count();
        return r2((double) w / f.size());
    }

    private double sumPnl(List<OkxOrder> o) { return r2(o.stream().mapToDouble(x->d(x.getPnl())).sum()); }
    private double sumFee(List<OkxOrder> o) { return r2(o.stream().filter(x->x.getFee()!=null).mapToDouble(x->d(x.getFee())).sum()); }

    private double avgPnlFiltered(List<OkxOrder> orders, int win) {
        return r2(orders.stream().filter(o->Objects.equals(o.getIsWin(),win))
                        .mapToDouble(o->d(o.getPnl())).average().orElse(0));
    }

    private double profitFactor(List<OkxOrder> orders) {
        double wins  = orders.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).mapToDouble(o->d(o.getPnl())).sum();
        double loses = orders.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==0).mapToDouble(o->Math.abs(d(o.getPnl()))).sum();
        return loses==0 ? 999.0 : r2(wins/loses);
    }

    private double avgHolding(List<OkxOrder> orders, Integer win) {
        Stream<OkxOrder> s = orders.stream().filter(o->holdingMinutes(o)!=null);
        if (win != null) s = s.filter(o->Objects.equals(o.getIsWin(),win));
        return r2(s.mapToInt(o->holdingMinutes(o)).average().orElse(0));
    }

    // ── 进阶指标 ──────────────────────────────────────────────────────────

    private double maxDrawdown(List<OkxOrder> orders) {
        double peak=0, cum=0, max=0;
        for (OkxOrder o : orders) { cum+=d(o.getPnl()); if(cum>peak)peak=cum; double dd=peak-cum; if(dd>max)max=dd; }
        return r2(max);
    }

    private double maxDrawdownPct(List<OkxOrder> orders) {
        double peak=0, cum=0, max=0;
        for (OkxOrder o : orders) { cum+=d(o.getPnl()); if(cum>peak)peak=cum; double dd=peak>0?(peak-cum)/peak*100:0; if(dd>max)max=dd; }
        return r2(max);
    }

    private double sharpe(List<OkxOrder> orders) {
        Map<String,Double> daily=buildDailyMap(orders);
        if(daily.size()<2)return 0;
        double[] v=daily.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean=Arrays.stream(v).average().orElse(0);
        double std=Math.sqrt(Arrays.stream(v).map(x->(x-mean)*(x-mean)).average().orElse(0));
        return std==0?0:r2(mean/std*Math.sqrt(365));
    }

    private int maxConsec(List<OkxOrder> orders, int win) {
        int max=0, cur=0;
        for (OkxOrder o : orders) {
            if(o.getIsWin()!=null&&o.getIsWin()==win){cur++;max=Math.max(max,cur);}else cur=0;
        }
        return max;
    }

    private double maxConsecAmount(List<OkxOrder> orders, int win) {
        double max=0, cur=0;
        for (OkxOrder o : orders) {
            if(o.getIsWin()!=null&&o.getIsWin()==win){
                cur+=Math.abs(d(o.getPnl()));max=Math.max(max,cur);
            } else cur=0;
        }
        return r2(max);
    }

    // ── 日历辅助 ──────────────────────────────────────────────────────────

    private Map<String,Double> buildDailyMap(List<OkxOrder> orders) {
        return orders.stream().collect(Collectors.groupingBy(
                o->o.getCreateTime().format(DATE_FMT),
                Collectors.summingDouble(o->d(o.getPnl()))));
    }

    private Map<String,Double> monthlyPnlMap(List<OkxOrder> orders) {
        return orders.stream().collect(Collectors.groupingBy(
                o->o.getCreateTime().format(MONTH_FMT),
                Collectors.summingDouble(o->d(o.getPnl()))));
    }

    private List<String> sortedKeys(Map<String,Double> m) {
        return m.keySet().stream().sorted().toList();
    }

    private List<Double> sortedValues(Map<String,Double> m) {
        return m.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e->r2(e.getValue())).toList();
    }

    // ── 净值曲线 ──────────────────────────────────────────────────────────

    private List<String> pnlDates(List<OkxOrder> orders) {
        return orders.stream().map(o->o.getCreateTime().format(DATE_FMT)).toList();
    }

    private List<Double> pnlCumulative(List<OkxOrder> orders) {
        List<Double> res=new ArrayList<>(); double cum=0;
        for(OkxOrder o:orders){cum+=d(o.getPnl());res.add(r2(cum));}
        return res;
    }

    // ── 滚动胜率 ──────────────────────────────────────────────────────────

    private List<Double> rollingWinRate(List<OkxOrder> orders, int window) {
        List<Double> res=new ArrayList<>();
        if (orders.isEmpty()) return res;
        for(int i=1;i<=orders.size();i++){
            int start = Math.max(0, i-window);
            List<OkxOrder> sl=orders.subList(start,i);
            long w=sl.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            res.add(r2((double)w/sl.size()));
        }
        return res;
    }

    private List<Integer> rollingIndex(List<OkxOrder> orders, int window) {
        List<Integer> res=new ArrayList<>();
        for(int i=1;i<=orders.size();i++)res.add(i);
        return res;
    }

    // ── 时段分析 ──────────────────────────────────────────────────────────

    private List<Integer> hourlyCount(List<OkxOrder> orders) {
        Map<Integer,Long> m=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getHour(),Collectors.counting()));
        return IntStream.range(0,24).mapToObj(h->m.getOrDefault(h,0L).intValue()).toList();
    }

    private List<Double> hourlyWinRate(List<OkxOrder> orders) {
        Map<Integer,List<OkxOrder>> byH=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getHour()));
        return IntStream.range(0,24).mapToObj(h->{
            List<OkxOrder> g=byH.getOrDefault(h,List.of());
            if(g.isEmpty())return 0.0;
            long w=g.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            return r2((double)w/g.size());
        }).toList();
    }

    private List<Double> hourlyTotalPnl(List<OkxOrder> orders) {
        Map<Integer,Double> m=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getHour(),Collectors.summingDouble(o->d(o.getPnl()))));
        return IntStream.range(0,24).mapToObj(h->r2(m.getOrDefault(h,0.0))).toList();
    }

    private List<Integer> weekdayCount(List<OkxOrder> orders) {
        Map<Integer,Long> m=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getDayOfWeek().getValue()-1,Collectors.counting()));
        return IntStream.range(0,7).mapToObj(d->m.getOrDefault(d,0L).intValue()).toList();
    }

    private List<Double> weekdayWinRate(List<OkxOrder> orders) {
        Map<Integer,List<OkxOrder>> byD=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getDayOfWeek().getValue()-1));
        return IntStream.range(0,7).mapToObj(d->{
            List<OkxOrder> g=byD.getOrDefault(d,List.of());
            if(g.isEmpty())return 0.0;
            long w=g.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            return r2((double)w/g.size());
        }).toList();
    }

    private List<Double> weekdayTotalPnl(List<OkxOrder> orders) {
        Map<Integer,Double> m=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().getDayOfWeek().getValue()-1,Collectors.summingDouble(o->d(o.getPnl()))));
        return IntStream.range(0,7).mapToObj(i->r2(m.getOrDefault(i,0.0))).toList();
    }

    private List<List<Object>> heatmap(List<OkxOrder> orders) {
        Map<String,Long> m=orders.stream().collect(Collectors.groupingBy(
                o->(o.getCreateTime().getDayOfWeek().getValue()-1)+"_"+o.getCreateTime().getHour(),Collectors.counting()));
        List<List<Object>> res=new ArrayList<>();
        for(int d=0;d<7;d++)for(int h=0;h<24;h++)res.add(List.of(h,d,m.getOrDefault(d+"_"+h,0L)));
        return res;
    }

    // ── 品种分析 ──────────────────────────────────────────────────────────

    private List<String> symbolNames(List<OkxOrder> orders) {
        return orders.stream().map(OkxOrder::getInstId).distinct().sorted().toList();
    }

    private List<Integer> symbolTradeCount(List<OkxOrder> orders) {
        Map<String,Long> m=orders.stream().collect(Collectors.groupingBy(OkxOrder::getInstId,Collectors.counting()));
        return symbolNames(orders).stream().map(s->m.getOrDefault(s,0L).intValue()).toList();
    }

    private List<Double> symbolWinRate(List<OkxOrder> orders) {
        Map<String,List<OkxOrder>> byS=orders.stream().collect(Collectors.groupingBy(OkxOrder::getInstId));
        return symbolNames(orders).stream().map(s->{
            List<OkxOrder> g=byS.get(s);
            long w=g.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            return r2((double)w/g.size());
        }).toList();
    }

    private List<Double> symbolTotalPnl(List<OkxOrder> orders) {
        Map<String,Double> m=orders.stream().collect(Collectors.groupingBy(OkxOrder::getInstId,Collectors.summingDouble(o->d(o.getPnl()))));
        return symbolNames(orders).stream().map(s->r2(m.getOrDefault(s,0.0))).toList();
    }

    private List<Double> symbolAvgHolding(List<OkxOrder> orders) {
        Map<String,List<OkxOrder>> byS=orders.stream().collect(Collectors.groupingBy(OkxOrder::getInstId));
        return symbolNames(orders).stream().map(s->r2(byS.get(s).stream()
                .filter(o->holdingMinutes(o)!=null).mapToInt(o->holdingMinutes(o)).average().orElse(0))).toList();
    }

    // ── 持仓时间 & 盈亏分布 ───────────────────────────────────────────────

    private static final int[][] HOLDING_BUCKETS = {{0,5},{5,15},{15,30},{30,60},{60,120},{120,240},{240,480},{480,1440},{1440,Integer.MAX_VALUE}};
    private static final List<String> HOLDING_LABELS = List.of("<5min","5-15min","15-30min","30-60min","1-2h","2-4h","4-8h","8-24h",">24h");

    private List<Integer> holdingCounts(List<OkxOrder> orders) {
        return Arrays.stream(HOLDING_BUCKETS).map(b->(int)orders.stream()
                .filter(o->holdingMinutes(o)!=null&&holdingMinutes(o)>=b[0]&&holdingMinutes(o)<b[1]).count()).toList();
    }

    private List<Double> holdingWinRates(List<OkxOrder> orders) {
        return Arrays.stream(HOLDING_BUCKETS).map(b->{
            List<OkxOrder> bkt=orders.stream().filter(o->holdingMinutes(o)!=null&&holdingMinutes(o)>=b[0]&&holdingMinutes(o)<b[1]).toList();
            if(bkt.isEmpty())return 0.0;
            long w=bkt.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            return r2((double)w/bkt.size());
        }).toList();
    }

    private List<String> pnlRangeLabels(List<OkxOrder> orders) {
        double max=orders.stream().mapToDouble(o->d(o.getPnl())).max().orElse(1);
        double min=orders.stream().mapToDouble(o->d(o.getPnl())).min().orElse(-1);
        double step=Math.max(1,Math.ceil((max-min)/12));
        List<String> l=new ArrayList<>();
        for(double v=min;v<max;v+=step)l.add(String.format("%.0f~%.0f",v,v+step));
        return l;
    }

    private List<Integer> pnlRangeCounts(List<OkxOrder> orders) {
        double max=orders.stream().mapToDouble(o->d(o.getPnl())).max().orElse(1);
        double min=orders.stream().mapToDouble(o->d(o.getPnl())).min().orElse(-1);
        double step=Math.max(1,Math.ceil((max-min)/12));
        List<Integer> c=new ArrayList<>();
        for(double v=min;v<max;v+=step){final double lo=v,hi=v+step;c.add((int)orders.stream().filter(o->d(o.getPnl())>=lo&&d(o.getPnl())<hi).count());}
        return c;
    }

    // ── 杠杆 ──────────────────────────────────────────────────────────────

    private Map<String,Double> leverWinRate(List<OkxOrder> orders) {
        Map<String,List<OkxOrder>> byL=orders.stream().filter(o->o.getLever()!=null&&!o.getLever().isEmpty())
                .collect(Collectors.groupingBy(o->o.getLever()+"x"));
        Map<String,Double> res=new LinkedHashMap<>();
        byL.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->{
            long w=e.getValue().stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            res.put(e.getKey(),r2((double)w/e.getValue().size()));
        });
        return res;
    }

    private Map<String,Integer> leverTradeCount(List<OkxOrder> orders) {
        Map<String,Integer> res=new LinkedHashMap<>();
        orders.stream().filter(o->o.getLever()!=null&&!o.getLever().isEmpty())
              .collect(Collectors.groupingBy(o->o.getLever()+"x",Collectors.counting()))
              .entrySet().stream().sorted(Map.Entry.comparingByKey())
              .forEach(e->res.put(e.getKey(),e.getValue().intValue()));
        return res;
    }

    // ── 月度统计 ──────────────────────────────────────────────────────────

    private List<MonthlyStatDto> monthlyStats(List<OkxOrder> orders) {
        Map<String,List<OkxOrder>> byM=orders.stream().collect(Collectors.groupingBy(o->o.getCreateTime().format(MONTH_FMT)));
        return byM.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e->{
            List<OkxOrder> g=e.getValue();
            long w=g.stream().filter(o->o.getIsWin()!=null&&o.getIsWin()==1).count();
            double pnl=g.stream().mapToDouble(o->d(o.getPnl())).sum();
            double dd=maxDrawdown(g);
            return new MonthlyStatDto(e.getKey(),g.size(),r2(g.isEmpty()?0:(double)w/g.size()),r2(pnl),r2(dd));
        }).toList();
    }

    // ── 工具 ──────────────────────────────────────────────────────────────

    private double d(java.math.BigDecimal v) { return v==null?0.0:v.doubleValue(); }
    private double r2(double v) { return Math.round(v*100.0)/100.0; }

    private Integer holdingMinutes(OkxOrder order) {
        if (order.getHoldingMinutes() != null && order.getHoldingMinutes() >= 0) {
            return order.getHoldingMinutes();
        }
        if (order.getCreateTime() == null || order.getUpdateTime() == null) {
            return null;
        }
        long seconds = ChronoUnit.SECONDS.between(order.getCreateTime(), order.getUpdateTime());
        if (seconds <= 0) {
            return 1;
        }
        long minutes = (long) Math.ceil(seconds / 60.0);
        if (minutes > Integer.MAX_VALUE) {
            return null;
        }
        return (int) minutes;
    }
    private void backfillHoldingMinutes(List<OkxOrder> closedOrders) {
        List<OkxOrder> timeline = orderRepo.findAllFilledOrders();
        if (timeline.isEmpty()) {
            return;
        }

        Map<Long, OkxOrder> closedById = closedOrders.stream()
                .filter(o -> o.getId() != null)
                .collect(Collectors.toMap(OkxOrder::getId, o -> o, (a, b) -> a));

        Map<String, OkxOrder> latestOpenByKey = new HashMap<>();
        Map<String, OkxOrder> latestOpenByPair = new HashMap<>();

        for (OkxOrder order : timeline) {
            boolean isClose = order.getIsWin() != null;
            String key = holdingKey(order.getInstId(), order.getPosSide(), order.getSide());
            String pairKey = pairKey(order.getInstId(), order.getPosSide());

            if (!isClose) {
                latestOpenByKey.put(key, order);
                latestOpenByPair.put(pairKey, order);
                continue;
            }

            OkxOrder target = closedById.get(order.getId());
            if (target == null) {
                continue;
            }

            OkxOrder open = latestOpenByKey.get(holdingKey(order.getInstId(), order.getPosSide(), oppositeSide(order.getSide())));
            if (open == null) {
                open = latestOpenByPair.get(pairKey);
            }
            if (open == null || open.getCreateTime() == null || target.getCreateTime() == null) {
                continue;
            }

            long seconds = ChronoUnit.SECONDS.between(open.getCreateTime(), target.getCreateTime());
            if (seconds > 0 && (target.getHoldingMinutes() == null || target.getHoldingMinutes() <= 0)) {
                long minutes = (long) Math.ceil(seconds / 60.0);
                target.setHoldingMinutes((int) Math.min(Math.max(1, minutes), Integer.MAX_VALUE));
            }
        }
    }

    private String holdingKey(String instId, String posSide, String side) {
        return (instId == null ? "" : instId) + "|" + (posSide == null ? "net" : posSide) + "|" + (side == null ? "" : side);
    }

    private String pairKey(String instId, String posSide) {
        return (instId == null ? "" : instId) + "|" + (posSide == null ? "net" : posSide);
    }

    private String oppositeSide(String side) {
        if (side == null) return "";
        if ("buy".equalsIgnoreCase(side)) return "sell";
        if ("sell".equalsIgnoreCase(side)) return "buy";
        return side;
    }
}


