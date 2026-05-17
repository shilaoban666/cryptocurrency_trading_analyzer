package com.okx.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.analyzer.config.DeepSeekConfig;
import com.okx.analyzer.dto.AnalysisDto;
import com.okx.analyzer.dto.BalanceDto;
import com.okx.analyzer.dto.MonthlyStatDto;
import com.okx.analyzer.dto.PositionAiAnalysisDto;
import com.okx.analyzer.dto.PositionDto;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionAiAnalysisService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PROVIDER = "DeepSeek";
    private static final List<String> TRACKED_INSTRUMENTS = List.of("BTC-USDT-SWAP", "ETH-USDT-SWAP");

    private final DeepSeekConfig config;
    private final PositionService positionService;
    private final AnalysisService analysisService;
    private final BalanceService balanceService;
    private final OkxApiService okxApiService;
    private final OkxOrderRepository orderRepo;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(24);

    @PreDestroy
    void shutdownExecutor() {
        ioExecutor.shutdownNow();
    }

    public PositionAiAnalysisDto analyzeCurrentPositions() {
        validateConfig();

        long startedAt = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now(ZONE);
        Map<String, Object> context = prepareContextSnapshot(now);
        log.info("[DeepSeek] 上下文准备完成，耗时 {} ms", System.currentTimeMillis() - startedAt);

        String content = callDeepSeek(context);
        log.info("[DeepSeek] 当前持仓分析完成，总耗时 {} ms", System.currentTimeMillis() - startedAt);
        return parseResult(content, now);
    }

    public SseEmitter streamCurrentPositions() {
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        try {
            validateConfig();
        } catch (Exception e) {
            sendErrorEvent(emitter, e);
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            long startedAt = System.currentTimeMillis();
            LocalDateTime now = LocalDateTime.now(ZONE);
            try {
                sendEvent(emitter, "stage", Map.of("message", "正在准备仓位、账户、行情和交易统计数据"));
                Map<String, Object> context = prepareContextSnapshot(now);
                long contextElapsed = System.currentTimeMillis() - startedAt;
                log.info("[DeepSeek SSE] 上下文准备完成，耗时 {} ms", contextElapsed);
                sendEvent(emitter, "stage", Map.of(
                        "message", "数据已准备完成，正在流式请求 DeepSeek",
                        "elapsedMs", contextElapsed
                ));

                String rawText = callDeepSeekStream(context, delta -> sendEvent(emitter, "delta", Map.of("delta", delta)));
                PositionAiAnalysisDto result = parseResult(rawText, now);
                long elapsed = System.currentTimeMillis() - startedAt;
                sendEvent(emitter, "done", Map.of(
                        "result", result,
                        "rawText", rawText,
                        "elapsedMs", elapsed
                ));
                log.info("[DeepSeek SSE] 当前持仓流式分析完成，总耗时 {} ms", elapsed);
                emitter.complete();
            } catch (Exception e) {
                log.warn("[DeepSeek SSE] 当前持仓流式分析失败: {}", rootMessage(e), e);
                sendErrorEvent(emitter, e);
            }
        }, ioExecutor);

        return emitter;
    }

    private Map<String, Object> prepareContextSnapshot(LocalDateTime now) {
        CompletableFuture<List<PositionDto>> positionsFuture =
                async("当前持仓", positionService::currentPositions, List.of());
        CompletableFuture<AnalysisDto> analysisFuture =
                async("历史交易统计", () -> analysisService.analyze(null, null), AnalysisDto.builder().build());
        CompletableFuture<BalanceDto> balanceFuture =
                async("账户资金", balanceService::getBalance, BalanceDto.builder().build());
        CompletableFuture<List<Map<String, Object>>> recentOrdersFuture =
                async("最近平仓订单", () -> recentClosedOrdersPayload(20), List.of());
        CompletableFuture<Map<String, Object>> marketFuture =
                async("BTC/ETH 行情快照", this::marketPayload, emptyMarketPayload());

        CompletableFuture.allOf(positionsFuture, analysisFuture, balanceFuture, recentOrdersFuture, marketFuture).join();
        Map<String, Object> context = buildContext(
                now,
                positionsFuture.join(),
                analysisFuture.join(),
                balanceFuture.join(),
                recentOrdersFuture.join(),
                marketFuture.join()
        );
        return context;
    }

    private Map<String, Object> buildContext(LocalDateTime now, List<PositionDto> positions,
                                             AnalysisDto analysis, BalanceDto balance,
                                             List<Map<String, Object>> recentClosedOrders,
                                             Map<String, Object> marketSnapshot) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("generatedAt", now.format(DT_FMT));
        context.put("timezone", "Asia/Shanghai");
        context.put("tradingProfile", Map.of(
                "exchange", "OKX",
                "market", "USDT 永续合约",
                "mainSymbols", "BTC 和 ETH",
                "goal", "判断当前仓位、行情状态、是否补仓/减仓/止损/继续持有"
        ));
        context.put("currentPositionSummary", positionSummary(positions));
        context.put("currentPositions", positions);
        context.put("account", balancePayload(balance));
        context.put("historicalTradingStats", analysisPayload(analysis));
        context.put("recentClosedOrders", recentClosedOrders);
        context.put("marketSnapshot", marketSnapshot);
        return context;
    }

    private Map<String, Object> positionSummary(List<PositionDto> positions) {
        int count = positions.size();
        int longCount = (int) positions.stream().filter(p -> "long".equals(p.getDirection())).count();
        int shortCount = (int) positions.stream().filter(p -> "short".equals(p.getDirection())).count();
        int alignedCount = (int) positions.stream().filter(p -> "顺势".equals(p.getAlignment())).count();
        int addCount = (int) positions.stream().filter(p -> p.getAddCount() > 1).count();
        int divergenceCount = (int) positions.stream().filter(p -> text(p.getDivergence()).contains("背离")).count();
        double totalUpl = positions.stream().mapToDouble(PositionDto::getUpl).sum();
        double totalNotional = positions.stream().mapToDouble(p -> Math.abs(p.getNotional())).sum();
        double avgHoldingMinutes = positions.stream().mapToLong(PositionDto::getHoldingMinutes).average().orElse(0);
        double minLiquidationDistancePct = positions.stream()
                .mapToDouble(PositionDto::getLiquidationDistancePct)
                .filter(v -> v > 0)
                .min()
                .orElse(0);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("positionCount", count);
        summary.put("longCount", longCount);
        summary.put("shortCount", shortCount);
        summary.put("totalFloatingPnlUsdt", r2(totalUpl));
        summary.put("totalNotionalUsdt", r2(totalNotional));
        summary.put("alignedPositionCount", alignedCount);
        summary.put("addPositionCount", addCount);
        summary.put("divergencePositionCount", divergenceCount);
        summary.put("avgHoldingMinutes", r2(avgHoldingMinutes));
        summary.put("minLiquidationDistancePct", r2(minLiquidationDistancePct));
        return summary;
    }

    private Map<String, Object> balancePayload(BalanceDto balance) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tradingBalance", r2(balance.getTradingBalance()));
        payload.put("fundingBalance", r2(balance.getFundingBalance()));
        payload.put("totalBalance", r2(balance.getTotalBalance()));

        List<String> dates = nullToEmpty(balance.getDates());
        List<Double> curve = nullToEmpty(balance.getCurve());
        List<Map<String, Object>> recentCurve = new ArrayList<>();
        int start = Math.max(0, Math.min(dates.size(), curve.size()) - 12);
        for (int i = start; i < Math.min(dates.size(), curve.size()); i++) {
            recentCurve.add(Map.of("date", dates.get(i), "balance", r2(curve.get(i))));
        }
        payload.put("recentBalanceCurve", recentCurve);
        return payload;
    }

    private Map<String, Object> analysisPayload(AnalysisDto a) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalTrades", a.getTotalTrades());
        payload.put("winCount", a.getWinCount());
        payload.put("lossCount", a.getLossCount());
        payload.put("winRatePct", r2(a.getWinRate() * 100));
        payload.put("longWinRatePct", r2(a.getLongWinRate() * 100));
        payload.put("shortWinRatePct", r2(a.getShortWinRate() * 100));
        payload.put("netPnlUsdt", r2(a.getNetPnl()));
        payload.put("avgWinUsdt", r2(a.getAvgWin()));
        payload.put("avgLossUsdt", r2(a.getAvgLoss()));
        payload.put("profitFactor", r2(a.getProfitFactor()));
        payload.put("expectedValueUsdt", r2(a.getExpectedValue()));
        payload.put("maxDrawdownUsdt", r2(a.getMaxDrawdown()));
        payload.put("maxDrawdownPct", r2(a.getMaxDrawdownPct()));
        payload.put("maxConsecWins", a.getMaxConsecWins());
        payload.put("maxConsecLosses", a.getMaxConsecLosses());
        payload.put("avgHoldingMinutes", r2(a.getAvgHoldingMinutes()));
        payload.put("avgWinHoldingMinutes", r2(a.getAvgWinHoldingMinutes()));
        payload.put("avgLossHoldingMinutes", r2(a.getAvgLossHoldingMinutes()));
        payload.put("dailyAvgTrades", r2(a.getDailyAvgTrades()));
        payload.put("liquidationCount", a.getLiquidationCount());
        payload.put("rollingWinRateLast20Pct", nullToEmpty(a.getRollingWinRate()).stream()
                .skip(Math.max(0, nullToEmpty(a.getRollingWinRate()).size() - 20))
                .map(v -> r2(v * 100))
                .toList());
        payload.put("dailyPnlLast20", tailPairs(a.getDailyDates(), a.getDailyPnl(), "date", "pnl", 20));
        payload.put("monthlyStatsLast12", monthlyStatsPayload(a.getMonthlyStats()));
        payload.put("leverWinRate", a.getLeverWinRate());
        payload.put("leverTradeCount", a.getLeverTradeCount());
        return payload;
    }

    private List<Map<String, Object>> monthlyStatsPayload(List<MonthlyStatDto> stats) {
        List<MonthlyStatDto> list = nullToEmpty(stats);
        return list.stream()
                .skip(Math.max(0, list.size() - 12))
                .map(item -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("month", item.getMonth());
                    m.put("tradeCount", item.getTrades());
                    m.put("winRatePct", r2(item.getWinRate() * 100));
                    m.put("totalPnlUsdt", r2(item.getTotalPnl()));
                    m.put("avgPnlUsdt", item.getTrades() > 0 ? r2(item.getTotalPnl() / item.getTrades()) : 0);
                    m.put("maxDrawdownUsdt", r2(item.getMaxDrawdown()));
                    return m;
                })
                .toList();
    }

    private List<Map<String, Object>> recentClosedOrdersPayload(int limit) {
        List<OkxOrder> orders = orderRepo.findAllClosedOrders();
        return orders.stream()
                .skip(Math.max(0, orders.size() - limit))
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", o.getCreateTime() == null ? "" : o.getCreateTime().format(DT_FMT));
                    m.put("instId", o.getInstId());
                    m.put("side", o.getSide());
                    m.put("posSide", o.getPosSide());
                    m.put("pnlUsdt", r2(d(o.getPnl())));
                    m.put("feeUsdt", r2(d(o.getFee())));
                    m.put("holdingMinutes", o.getHoldingMinutes());
                    m.put("isWin", o.getIsWin());
                    m.put("isLiquidation", o.getIsLiquidation());
                    return m;
                })
                .toList();
    }

    private Map<String, Object> marketPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        List<CompletableFuture<Map<String, Object>>> futures = TRACKED_INSTRUMENTS.stream()
                .map(instId -> async("行情 " + instId, () -> instrumentMarketPayload(instId), instrumentFallback(instId)))
                .toList();
        List<Map<String, Object>> instruments = futures.stream().map(CompletableFuture::join).toList();
        payload.put("instruments", instruments);
        payload.put("btcEthRelativeStrength", relativeStrength(instruments));
        return payload;
    }

    private Map<String, Object> instrumentMarketPayload(String instId) {
        List<String> dataErrors = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<JsonNode> tickerFuture = async("ticker " + instId,
                () -> safePublic(path("/api/v5/market/ticker", Map.of("instId", instId)), dataErrors), null);
        CompletableFuture<JsonNode> candle1hFuture = async("1H candles " + instId,
                () -> safePublic(path("/api/v5/market/candles", Map.of("instId", instId, "bar", "1H", "limit", "50")), dataErrors), null);
        CompletableFuture<JsonNode> candle4hFuture = async("4H candles " + instId,
                () -> safePublic(path("/api/v5/market/candles", Map.of("instId", instId, "bar", "4H", "limit", "50")), dataErrors), null);
        CompletableFuture<JsonNode> fundingFuture = async("funding " + instId,
                () -> safePublic(path("/api/v5/public/funding-rate-history", Map.of("instId", instId, "limit", "20")), dataErrors), null);
        CompletableFuture<JsonNode> oiFuture = async("OI " + instId,
                () -> safePublic(path("/api/v5/rubik/stat/contracts/open-interest-history", Map.of("instId", instId, "period", "1H", "limit", "50")), dataErrors), null);
        CompletableFuture<JsonNode> topTraderFuture = async("top trader long short " + instId,
                () -> safePublic(path("/api/v5/rubik/stat/contracts/long-short-position-ratio-contract-top-trader",
                        Map.of("instId", instId, "period", "1H", "limit", "50")), dataErrors), null);
        CompletableFuture<JsonNode> accountRatioFuture = async("account long short " + instId,
                () -> safePublic(path("/api/v5/rubik/stat/contracts/long-short-account-ratio-contract",
                        Map.of("instId", instId, "period", "1H", "limit", "50")), dataErrors), null);
        CompletableFuture<JsonNode> takerFuture = async("taker flow " + instId,
                () -> safePublic(path("/api/v5/rubik/stat/taker-volume-contract",
                        Map.of("instId", instId, "period", "1H", "limit", "50")), dataErrors), null);

        CompletableFuture.allOf(
                tickerFuture, candle1hFuture, candle4hFuture, fundingFuture, oiFuture,
                topTraderFuture, accountRatioFuture, takerFuture
        ).join();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instId", instId);
        payload.put("ticker", tickerPayload(tickerFuture.join()));
        payload.put("technical1H", candleTechnicalPayload(candle1hFuture.join()));
        payload.put("technical4H", candleTechnicalPayload(candle4hFuture.join()));
        payload.put("funding", fundingPayload(fundingFuture.join()));
        payload.put("openInterest", ratioSeriesPayload(oiFuture.join(), 1, "oi"));
        payload.put("topTraderLongShortPosition", ratioSeriesPayload(topTraderFuture.join(), 1, "longShortRatio"));
        payload.put("accountLongShortRatio", ratioSeriesPayload(accountRatioFuture.join(), 1, "longShortRatio"));
        payload.put("takerFlow", takerFlowPayload(takerFuture.join()));
        payload.put("dataErrors", List.copyOf(dataErrors));
        return payload;
    }

    private Map<String, Object> instrumentFallback(String instId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instId", instId);
        payload.put("dataErrors", List.of("行情快照获取失败"));
        return payload;
    }

    private Map<String, Object> tickerPayload(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        JsonNode node = firstArrayItem(data);
        if (node == null) return payload;

        double last = node.path("last").asDouble(0);
        double open24h = node.path("open24h").asDouble(0);
        payload.put("last", r2(last));
        payload.put("open24h", r2(open24h));
        payload.put("change24hPct", open24h > 0 ? r2((last - open24h) / open24h * 100) : 0);
        payload.put("high24h", r2(node.path("high24h").asDouble(0)));
        payload.put("low24h", r2(node.path("low24h").asDouble(0)));
        payload.put("volCcy24h", r2(node.path("volCcy24h").asDouble(0)));
        payload.put("volCcyQuote24h", r2(node.path("volCcyQuote24h").asDouble(0)));
        payload.put("ts", node.path("ts").asText(""));
        return payload;
    }

    private Map<String, Object> candleTechnicalPayload(JsonNode candles) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (candles == null || !candles.isArray() || candles.size() < 20) return payload;

        List<Candle> series = new ArrayList<>();
        for (int i = candles.size() - 1; i >= 0; i--) {
            JsonNode n = candles.get(i);
            series.add(new Candle(
                    n.get(0).asText(),
                    n.get(1).asDouble(0),
                    n.get(2).asDouble(0),
                    n.get(3).asDouble(0),
                    n.get(4).asDouble(0),
                    n.size() > 5 ? n.get(5).asDouble(0) : 0
            ));
        }

        List<Double> closes = series.stream().map(Candle::close).toList();
        double last = closes.get(closes.size() - 1);
        double ema20 = ema(closes, 20);
        double ema50 = ema(closes, Math.min(50, closes.size()));
        double rsi14 = rsi(closes, 14);
        double atrPct = atrPct(series, 14);
        double prev24 = closes.get(Math.max(0, closes.size() - 25));
        double high20 = series.stream().skip(Math.max(0, series.size() - 20)).mapToDouble(Candle::high).max().orElse(0);
        double low20 = series.stream().skip(Math.max(0, series.size() - 20)).mapToDouble(Candle::low).min().orElse(0);
        double volumeLatest = series.get(series.size() - 1).volume();
        double volumeAvg20 = series.stream()
                .skip(Math.max(0, series.size() - 20))
                .mapToDouble(Candle::volume)
                .average()
                .orElse(0);

        payload.put("lastClose", r2(last));
        payload.put("ema20", r2(ema20));
        payload.put("ema50", r2(ema50));
        payload.put("rsi14", r2(rsi14));
        payload.put("atr14Pct", r2(atrPct));
        payload.put("priceChange24BarsPct", prev24 > 0 ? r2((last - prev24) / prev24 * 100) : 0);
        payload.put("high20", r2(high20));
        payload.put("low20", r2(low20));
        payload.put("volumeLatest", r2(volumeLatest));
        payload.put("volumeVsAvg20Pct", volumeAvg20 > 0 ? r2((volumeLatest - volumeAvg20) / volumeAvg20 * 100) : 0);
        payload.put("structure", structure(last, ema20, ema50));
        payload.put("recentCloses", closes.stream()
                .skip(Math.max(0, closes.size() - 12))
                .map(this::r2)
                .toList());
        return payload;
    }

    private Map<String, Object> fundingPayload(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (data == null || !data.isArray() || data.isEmpty()) return payload;

        List<Double> rates = new ArrayList<>();
        JsonNode latest = null;
        long latestTs = Long.MIN_VALUE;
        for (JsonNode node : data) {
            double rate = node.path("fundingRate").asDouble(0) * 100;
            rates.add(rate);
            long ts = node.path("fundingTime").asLong(0);
            if (ts > latestTs) {
                latestTs = ts;
                latest = node;
            }
        }
        double latestRate = latest == null ? 0 : latest.path("fundingRate").asDouble(0) * 100;
        payload.put("latestFundingRatePct", r4(latestRate));
        payload.put("avgFundingRatePct", r4(rates.stream().mapToDouble(Double::doubleValue).average().orElse(0)));
        payload.put("maxFundingRatePct", r4(rates.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
        payload.put("minFundingRatePct", r4(rates.stream().mapToDouble(Double::doubleValue).min().orElse(0)));
        payload.put("latestFundingTime", latest == null ? "" : latest.path("fundingTime").asText(""));
        return payload;
    }

    private Map<String, Object> ratioSeriesPayload(JsonNode data, int valueIndex, String valueName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (data == null || !data.isArray() || data.isEmpty()) return payload;

        List<Double> values = new ArrayList<>();
        for (JsonNode node : data) {
            if (node.isArray() && node.size() > valueIndex) {
                values.add(node.get(valueIndex).asDouble(0));
            }
        }
        if (values.isEmpty()) return payload;

        double latest = values.get(values.size() - 1);
        double first = values.get(Math.max(0, values.size() - 24));
        payload.put(valueName + "Latest", r4(latest));
        payload.put(valueName + "Avg20", r4(values.stream()
                .skip(Math.max(0, values.size() - 20))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0)));
        payload.put(valueName + "Change24Pct", first != 0 ? r2((latest - first) / Math.abs(first) * 100) : 0);
        payload.put(valueName + "Recent", values.stream()
                .skip(Math.max(0, values.size() - 12))
                .map(this::r4)
                .toList());
        return payload;
    }

    private Map<String, Object> takerFlowPayload(JsonNode data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (data == null || !data.isArray() || data.isEmpty()) return payload;

        List<Double> imbalance = new ArrayList<>();
        for (JsonNode node : data) {
            if (!node.isArray() || node.size() < 3) continue;
            double sell = node.get(1).asDouble(0);
            double buy = node.get(2).asDouble(0);
            double total = buy + sell;
            imbalance.add(total > 0 ? (buy - sell) / total * 100 : 0);
        }
        if (imbalance.isEmpty()) return payload;

        double latest = imbalance.get(imbalance.size() - 1);
        payload.put("latestBuySellImbalancePct", r2(latest));
        payload.put("avg20BuySellImbalancePct", r2(imbalance.stream()
                .skip(Math.max(0, imbalance.size() - 20))
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0)));
        payload.put("recentBuySellImbalancePct", imbalance.stream()
                .skip(Math.max(0, imbalance.size() - 12))
                .map(this::r2)
                .toList());
        return payload;
    }

    private Map<String, Object> relativeStrength(List<Map<String, Object>> instruments) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> btc = instruments.stream()
                .filter(m -> "BTC-USDT-SWAP".equals(m.get("instId")))
                .findFirst()
                .orElse(Map.of());
        Map<String, Object> eth = instruments.stream()
                .filter(m -> "ETH-USDT-SWAP".equals(m.get("instId")))
                .findFirst()
                .orElse(Map.of());
        double btc24 = numberAt(btc, "technical1H", "priceChange24BarsPct");
        double eth24 = numberAt(eth, "technical1H", "priceChange24BarsPct");
        payload.put("btc24hPct", btc24);
        payload.put("eth24hPct", eth24);
        payload.put("ethMinusBtcPct", r2(eth24 - btc24));
        payload.put("strongerSymbol", eth24 > btc24 ? "ETH" : btc24 > eth24 ? "BTC" : "相近");
        return payload;
    }

    private String callDeepSeek(Map<String, Object> context) {
        String endpoint = trimTrailingSlash(config.getBaseUrl()) + "/chat/completions";
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", false);
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("reasoning_effort", blankToDefault(config.getReasoningEffort(), "high"));
        requestBody.put("thinking", Map.of("type", config.isThinkingEnabled() ? "enabled" : "disabled"));

        try {
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt()),
                    Map.of("role", "user", "content", userPrompt(context))
            ));
            String body = objectMapper.writeValueAsString(requestBody);
            log.info("[DeepSeek] 开始请求模型 model={} payload={} bytes", config.getModel(), body.getBytes(StandardCharsets.UTF_8).length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(20, config.getTimeoutSeconds())))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("[DeepSeek] 模型响应 status={}", response.statusCode());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[DeepSeek] API 请求失败: status={} body={}", response.statusCode(), bodyExcerpt(response.body()));
                throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                        "DeepSeek 接口请求失败（HTTP " + response.statusCode() + "）");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            if (content == null || content.isBlank()) {
                content = message.path("reasoning_content").asText("");
            }
            if (content == null || content.isBlank()) {
                throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                        "DeepSeek 未返回可展示的分析内容");
            }
            return content;
        } catch (JsonProcessingException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 请求体序列化失败", e);
        } catch (IOException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null,
                    "DeepSeek 网络请求失败：" + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 请求被中断", e);
        }
    }

    private String callDeepSeekStream(Map<String, Object> context, Consumer<String> onDelta) {
        String endpoint = trimTrailingSlash(config.getBaseUrl()) + "/chat/completions";
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", true);
        requestBody.put("temperature", 0.2);
        requestBody.put("max_tokens", config.getMaxTokens());
        requestBody.put("response_format", Map.of("type", "json_object"));
        requestBody.put("stream_options", Map.of("include_usage", true));
        // 流式页面优先低延迟，避免先长时间生成 reasoning_content 才返回最终 JSON。
        requestBody.put("thinking", Map.of("type", "disabled"));

        try {
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt()),
                    Map.of("role", "user", "content", userPrompt(context))
            ));
            String body = objectMapper.writeValueAsString(requestBody);
            log.info("[DeepSeek SSE] 开始请求模型 model={} payload={} bytes", config.getModel(), body.getBytes(StandardCharsets.UTF_8).length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(20, config.getTimeoutSeconds())))
                    .header("Accept", "text/event-stream")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            log.info("[DeepSeek SSE] 模型响应 status={}", response.statusCode());
            try (Stream<String> lines = response.body()) {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = lines.collect(Collectors.joining("\n"));
                    log.warn("[DeepSeek SSE] API 请求失败: status={} body={}", response.statusCode(), bodyExcerpt(errorBody));
                    throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                            "DeepSeek 接口请求失败（HTTP " + response.statusCode() + "）");
                }

                StringBuilder content = new StringBuilder();
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (line == null || line.isBlank() || !line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if ("[DONE]".equals(payload)) {
                        break;
                    }
                    appendStreamDelta(payload, content, onDelta);
                }
                if (content.isEmpty()) {
                    throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                            "DeepSeek 流式响应未返回可展示的分析内容");
                }
                return content.toString();
            }
        } catch (JsonProcessingException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 请求体序列化失败", e);
        } catch (IOException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null,
                    "DeepSeek 流式网络请求失败：" + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 流式请求被中断", e);
        }
    }

    private void appendStreamDelta(String payload, StringBuilder content, Consumer<String> onDelta) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode error = root.path("error");
            if (error.isObject()) {
                throw new AiApiException(PROVIDER, "/chat/completions", null,
                        "DeepSeek 流式响应错误：" + error.path("message").asText("未知错误"));
            }
            JsonNode delta = root.path("choices").path(0).path("delta");
            String text = delta.path("content").asText("");
            if (text == null || text.isEmpty()) {
                return;
            }
            content.append(text);
            onDelta.accept(text);
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            log.debug("[DeepSeek SSE] 跳过无法解析的流式片段: {}", bodyExcerpt(payload));
        }
    }

    private PositionAiAnalysisDto parseResult(String content, LocalDateTime generatedAt) {
        JsonNode root = tryParseJsonObject(content);
        if (root == null) {
            return PositionAiAnalysisDto.builder()
                    .model(config.getModel())
                    .generatedAt(generatedAt)
                    .headline("DeepSeek 返回内容暂时无法整理")
                    .riskLevel("未知")
                    .actionBias("人工复核")
                    .biasScore(0)
                    .confidence(0)
                    .positionAnalysis(content)
                    .marketAnalysis("")
                    .actionRecommendation("")
                    .rawText(content)
                    .build();
        }

        return PositionAiAnalysisDto.builder()
                .model(config.getModel())
                .generatedAt(generatedAt)
                .headline(text(root.path("headline").asText("")))
                .riskLevel(text(root.path("riskLevel").asText("未知")))
                .actionBias(text(root.path("actionBias").asText("观望")))
                .biasScore(clampInt(root.path("biasScore").asInt(0), -100, 100))
                .confidence(clampInt(root.path("confidence").asInt(0), 0, 100))
                .positionAnalysis(text(root.path("positionAnalysis").asText("")))
                .marketAnalysis(text(root.path("marketAnalysis").asText("")))
                .actionRecommendation(text(root.path("actionRecommendation").asText("")))
                .keySignals(stringList(root.path("keySignals")))
                .riskWarnings(stringList(root.path("riskWarnings")))
                .actionChecklist(stringList(root.path("actionChecklist")))
                .rawText(content)
                .build();
    }

    private JsonNode tryParseJsonObject(String content) {
        String json = stripJsonFence(content);
        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    JsonNode node = objectMapper.readTree(json.substring(start, end + 1));
                    return node != null && node.isObject() ? node : null;
                } catch (Exception ignoredAgain) {
                    return null;
                }
            }
            return null;
        }
    }

    private String stripJsonFence(String content) {
        String s = text(content).trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "");
            s = s.replaceFirst("\\s*```$", "");
        }
        return s.trim();
    }

    private String systemPrompt() {
        return """
                你是一个专业的加密货币永续合约交易风控分析师，专门分析 BTC/ETH 持仓、OKX 行情、主力/散户多空、资金费率、OI、主动买卖量和交易者历史行为。
                你必须只根据用户提供的数据做判断，不得编造不存在的价格、仓位、指标或新闻。
                你需要特别关注：当前持仓是否顺势、是否亏损补仓、是否扛单、是否存在顶背离/底背离、SMC 偏多/偏空、流动性扫单、OI 与价格是否同向、资金费率是否拥挤、主力和散户是否背离。
                结论要直接、可执行，但必须带条件触发和风险边界；不要承诺收益。所有建议都属于交易复盘和风控参考，非投资建议。
                只输出一个 JSON 对象，不要输出 Markdown、代码块或额外解释。
                """;
    }

    private String userPrompt(Map<String, Object> context) throws JsonProcessingException {
        String contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        return """
                下面是当前仓位、账户资金、历史交易表现、最近平仓操作、BTC/ETH 行情技术指标、主力/散户多空、主动买卖量、资金费率和 OI 数据。

                请输出 JSON，字段必须完全使用以下英文 key：
                {
                  "headline": "一句话总判断，最多 40 个中文字符",
                  "riskLevel": "低/中/高/极高",
                  "actionBias": "立即减仓/防守减仓/观望等待/继续持有/顺势加仓/人工复核 之一，也可非常接近",
                  "biasScore": -100 到 100 的整数，-100 代表强减仓或强看空，0 代表观望，100 代表强持有或顺势加仓",
                  "confidence": 0 到 100 的整数",
                  "positionAnalysis": "当前仓位解析：说明仓位方向、浮盈亏、持仓时间、补仓、爆仓距离、是否扛单、是否和市场背离",
                  "marketAnalysis": "行情解析：说明 BTC/ETH 趋势、RSI/MACD/EMA 结构、资金费率、OI、主力/散户多空、主动买卖量，并指出顶背离/底背离/SMC 偏向",
                  "actionRecommendation": "动作推荐：给出持有、减仓、止损、止盈、等待确认、禁止补仓等条件，必须包含非投资建议",
                  "keySignals": ["3 到 6 条最重要信号"],
                  "riskWarnings": ["2 到 5 条风险提示"],
                  "actionChecklist": ["3 到 6 条下一步检查项"]
                }

                要求：positionAnalysis、marketAnalysis、actionRecommendation 每段控制在 160 个中文字符以内，直接给结论，不写长篇解释。

                输入数据：
                """ + contextJson;
    }

    private JsonNode safePublic(String requestPath, List<String> errors) {
        try {
            return okxApiService.getPublic(requestPath);
        } catch (Exception e) {
            errors.add(requestPath + " -> " + e.getMessage());
            return null;
        }
    }

    private String path(String base, Map<String, String> params) {
        return base + "?" + params.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private JsonNode firstArrayItem(JsonNode data) {
        return data != null && data.isArray() && !data.isEmpty() ? data.get(0) : null;
    }

    private String structure(double last, double ema20, double ema50) {
        if (last > ema20 && ema20 > ema50) return "多头排列";
        if (last < ema20 && ema20 < ema50) return "空头排列";
        if (last > ema20 && ema20 < ema50) return "反弹修复";
        if (last < ema20 && ema20 > ema50) return "回落破位";
        return "震荡";
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

    private double rsi(List<Double> closes, int period) {
        if (closes.size() <= period) return 50;
        double gain = 0;
        double loss = 0;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            double diff = closes.get(i) - closes.get(i - 1);
            if (diff >= 0) {
                gain += diff;
            } else {
                loss += Math.abs(diff);
            }
        }
        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - 100 / (1 + rs);
    }

    private double atrPct(List<Candle> candles, int period) {
        if (candles.size() <= period) return 0;
        List<Candle> tail = candles.subList(candles.size() - period, candles.size());
        double sum = 0;
        for (int i = 0; i < tail.size(); i++) {
            Candle c = tail.get(i);
            double prevClose = i == 0
                    ? candles.get(candles.size() - period - 1).close()
                    : tail.get(i - 1).close();
            double tr = Math.max(c.high() - c.low(), Math.max(Math.abs(c.high() - prevClose), Math.abs(c.low() - prevClose)));
            sum += tr;
        }
        double last = candles.get(candles.size() - 1).close();
        return last > 0 ? sum / period / last * 100 : 0;
    }

    private List<Map<String, Object>> tailPairs(List<String> keys, List<Double> values,
                                                String keyName, String valueName, int limit) {
        List<String> k = nullToEmpty(keys);
        List<Double> v = nullToEmpty(values);
        int size = Math.min(k.size(), v.size());
        int start = Math.max(0, size - limit);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = start; i < size; i++) {
            rows.add(Map.of(keyName, k.get(i), valueName, r2(v.get(i))));
        }
        return rows;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> list = new ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) list.add(value);
        }
        return list;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data, MediaType.APPLICATION_JSON));
            }
        } catch (IOException | IllegalStateException e) {
            throw new AiApiException(PROVIDER, "sse:" + eventName, null, "浏览器 SSE 连接已断开", e);
        }
    }

    private void sendErrorEvent(SseEmitter emitter, Throwable e) {
        try {
            synchronized (emitter) {
                emitter.send(SseEmitter.event()
                        .name("fail")
                        .data(Map.of("message", rootMessage(e)), MediaType.APPLICATION_JSON));
            }
        } catch (Exception ignored) {
        } finally {
            emitter.complete();
        }
    }

    private <T> CompletableFuture<T> async(String name, Supplier<T> supplier, T fallback) {
        return CompletableFuture.supplyAsync(() -> {
            long startedAt = System.currentTimeMillis();
            T value = supplier.get();
            long elapsed = System.currentTimeMillis() - startedAt;
            if (elapsed > 1500) {
                log.info("[DeepSeek] {} 获取耗时 {} ms", name, elapsed);
            }
            return value;
        }, ioExecutor).exceptionally(ex -> {
            log.warn("[DeepSeek] {} 获取失败: {}", name, rootMessage(ex));
            return fallback;
        });
    }

    private Map<String, Object> emptyMarketPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruments", List.of());
        payload.put("btcEthRelativeStrength", Map.of());
        return payload;
    }

    private double numberAt(Map<String, Object> map, String childKey, String fieldKey) {
        Object child = map.get(childKey);
        if (!(child instanceof Map<?, ?> childMap)) return 0;
        Object value = childMap.get(fieldKey);
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    private void validateConfig() {
        if (config.getKey() == null || config.getKey().isBlank()) {
            throw new IllegalArgumentException("DeepSeek API key 未配置，请设置 deepseek.api.key 或环境变量 DEEPSEEK_API_KEY");
        }
        if (config.getBaseUrl() == null || config.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("DeepSeek base-url 未配置");
        }
        if (config.getModel() == null || config.getModel().isBlank()) {
            throw new IllegalArgumentException("DeepSeek model 未配置");
        }
    }

    private String bodyExcerpt(String body) {
        String text = text(body).replaceAll("\\s+", " ");
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }

    private String trimTrailingSlash(String value) {
        String text = text(value).trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private double d(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double r4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return Map.of();
        return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
    }

    private record Candle(String ts, double open, double high, double low, double close, double volume) {
    }
}
