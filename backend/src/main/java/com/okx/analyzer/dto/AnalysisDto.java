package com.okx.analyzer.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class AnalysisDto {

    // ════════════════════════════════════════════
    //  一、核心胜率 & 盈亏
    // ════════════════════════════════════════════
    private long   totalTrades;          // 总关仓单数
    private long   winCount;
    private long   lossCount;
    private double winRate;              // 胜率（0-1）
    private double longWinRate;          // 做多胜率
    private double shortWinRate;         // 做空胜率
    private double totalPnl;             // 总盈亏 USDT
    private double totalFee;             // 总手续费（负数）
    private double netPnl;               // 净盈亏 = totalPnl + totalFee
    private double avgPnl;               // 每单平均盈亏
    private double avgWin;               // 赢单平均盈利
    private double avgLoss;              // 输单平均亏损
    private double maxWin;               // 单笔最大盈利
    private double maxLoss;              // 单笔最大亏损

    // ════════════════════════════════════════════
    //  二、进阶量化指标
    // ════════════════════════════════════════════
    private double profitFactor;         // 盈亏比 = 总盈 / |总亏|
    private double expectedValue;        // 期望值 = 胜率×均盈 + 败率×均亏
    private double kellyCriterion;       // 凯利公式建议仓位 %（过度激进，实际用1/4）
    private double sharpeRatio;          // 夏普比率（年化简化版）
    private double calmarRatio;          // 卡玛比率 = 净盈亏 / 最大回撤%
    private double maxDrawdown;          // 最大回撤金额 USDT
    private double maxDrawdownPct;       // 最大回撤比例 %
    private int    maxConsecWins;        // 最大连胜次数
    private int    maxConsecLosses;      // 最大连败次数
    private double maxConsecWinAmount;   // 最大连胜累计金额
    private double maxConsecLossAmount;  // 最大连败累计亏损

    // ════════════════════════════════════════════
    //  三、持仓时间指标
    // ════════════════════════════════════════════
    private double avgHoldingMinutes;    // 全体平均持仓分钟
    private double avgWinHoldingMinutes; // 赢单平均持仓分钟
    private double avgLossHoldingMinutes;// 输单平均持仓分钟

    // ════════════════════════════════════════════
    //  四、交易频率 & 日历指标
    // ════════════════════════════════════════════
    private int    totalTradeDays;       // 有交易的天数
    private double dailyAvgTrades;       // 日均交易次数
    private double bestDayPnl;           // 单日最佳盈亏
    private String bestDayDate;          // 最佳日期
    private double worstDayPnl;          // 单日最差盈亏
    private String worstDayDate;         // 最差日期
    private double bestMonthPnl;         // 最佳月份盈亏
    private String bestMonthDate;
    private double worstMonthPnl;
    private String worstMonthDate;
    private int    liquidationCount;     // 爆仓/强平次数

    // ════════════════════════════════════════════
    //  五、时间序列数据（图表用）
    // ════════════════════════════════════════════
    private List<String> pnlDates;
    private List<Double> pnlCumulative;  // 净值曲线

    private List<String> dailyDates;
    private List<Double> dailyPnl;

    private List<Double> rollingWinRate; // 滚动20单胜率趋势
    private List<Integer> rollingIndex;  // 对应第几单

    // ════════════════════════════════════════════
    //  六、时段分析
    // ════════════════════════════════════════════
    private List<Integer> hourlyTradeCount;
    private List<Double>  hourlyWinRate;
    private List<Double>  hourlyTotalPnl;   // 各小时总盈亏

    private List<Integer> weekdayTradeCount;
    private List<Double>  weekdayWinRate;
    private List<Double>  weekdayTotalPnl;

    private List<List<Object>> heatmapData;  // [hour, weekday, count]

    // ════════════════════════════════════════════
    //  七、品种 & 方向分析
    // ════════════════════════════════════════════
    private List<String>  symbolNames;
    private List<Integer> symbolTradeCount;
    private List<Double>  symbolWinRate;
    private List<Double>  symbolTotalPnl;
    private List<Double>  symbolAvgHolding;

    // ════════════════════════════════════════════
    //  八、分布分析
    // ════════════════════════════════════════════
    private List<String>  holdingLabels;
    private List<Integer> holdingCounts;
    private List<Double>  holdingWinRates;

    private List<String>  pnlRangeLabels;
    private List<Integer> pnlRangeCounts;

    // ════════════════════════════════════════════
    //  九、杠杆 & 月度
    // ════════════════════════════════════════════
    private Map<String, Double>  leverWinRate;
    private Map<String, Integer> leverTradeCount;

    private List<MonthlyStatDto> monthlyStats;
}
