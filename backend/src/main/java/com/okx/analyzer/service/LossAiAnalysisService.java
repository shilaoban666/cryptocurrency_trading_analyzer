package com.okx.analyzer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.analyzer.config.DeepSeekConfig;
import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.entity.OrderReview;
import com.okx.analyzer.repository.OkxOrderRepository;
import com.okx.analyzer.repository.OrderReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LossAiAnalysisService {

    private static final String PROVIDER = "DeepSeek";
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeepSeekConfig config;
    private final OkxOrderRepository orderRepo;
    private final OrderReviewRepository reviewRepo;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public Map<String, Object> analyzeLosses() {
        validateConfig();
        LocalDateTime now = LocalDateTime.now(ZONE);
        List<OkxOrder> closedOrders = orderRepo.findAllClosedOrders();
        Map<String, OrderReview> reviewMap = loadReviewMap(closedOrders);
        List<OkxOrder> lossOrders = closedOrders.stream()
                .filter(o -> netPnl(o) < 0)
                .toList();

        if (lossOrders.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("model", config.getModel());
            empty.put("generatedAt", now.format(DT_FMT));
            empty.put("headline", "当前没有可分析的亏损平仓单");
            empty.put("summary", "后端没有找到净盈亏小于 0 或标记为亏损的已平仓订单。");
            empty.put("sampleSize", 0);
            empty.put("rootCauses", List.of());
            empty.put("actionPlan", List.of());
            empty.put("riskRules", List.of());
            empty.put("dataChecks", List.of("亏损样本数为 0，无法做亏损归因。"));
            return empty;
        }

        Map<String, Object> context = buildContext(now, closedOrders, lossOrders, reviewMap);
        String raw = callDeepSeek(context);
        return parseResult(raw, now, context);
    }

    private Map<String, Object> buildContext(LocalDateTime now, List<OkxOrder> closedOrders,
                                             List<OkxOrder> lossOrders, Map<String, OrderReview> reviewMap) {
        long wins = closedOrders.stream().filter(o -> Objects.equals(o.getIsWin(), 1)).count();
        long reviewedLoss = lossOrders.stream().filter(o -> hasReview(reviewMap.get(o.getOrdId()))).count();
        double totalWin = closedOrders.stream().mapToDouble(o -> Math.max(0, netPnl(o))).sum();
        double totalLoss = lossOrders.stream().mapToDouble(this::lossAmount).sum();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("generatedAt", now.format(DT_FMT));
        context.put("timezone", "Asia/Shanghai");
        context.put("scope", "OKX BTC/ETH USDT perpetual closed orders and manual reviews");
        context.put("summary", Map.of(
                "closedOrderCount", closedOrders.size(),
                "winCount", wins,
                "lossCount", lossOrders.size(),
                "winRatePct", pct(wins, closedOrders.size()),
                "totalWinUsdt", r2(totalWin),
                "totalLossUsdt", r2(totalLoss),
                "netPnlUsdt", r2(closedOrders.stream().mapToDouble(this::netPnl).sum()),
                "avgLossUsdt", r2(avg(lossOrders.stream().mapToDouble(this::lossAmount).toArray())),
                "maxLossUsdt", r2(lossOrders.stream().mapToDouble(this::lossAmount).max().orElse(0)),
                "lossReviewCoveragePct", pct(reviewedLoss, lossOrders.size())
        ));
        context.put("lossReasonTags", topReasonTags(lossOrders, reviewMap, "reasons", 15));
        context.put("emaLossTags", topReasonTags(lossOrders, reviewMap, "ema", 12));
        context.put("kdjLossTags", topReasonTags(lossOrders, reviewMap, "kdj", 12));
        context.put("macdLossTags", topReasonTags(lossOrders, reviewMap, "macd", 12));
        context.put("lossByLeverage", lossByLeverage(closedOrders));
        context.put("lossByDirection", lossByDirection(lossOrders));
        context.put("lossByHoldingBucket", lossByHoldingBucket(lossOrders));
        context.put("lossByHour", lossByHour(lossOrders));
        context.put("lossByWeekday", lossByWeekday(lossOrders));
        context.put("monthlyLossTrend", monthlyLossTrend(lossOrders));
        context.put("worstLossOrders", worstLossOrders(lossOrders, reviewMap, 20));
        context.put("dataRules", List.of(
                "netPnlUsdt = pnl + fee; fee is normally negative",
                "lossAmountUsdt = absolute value of negative netPnl; if netPnl is not negative, use absolute negative pnl",
                "winRate uses closed orders with isWin = 1 over all closed orders",
                "loss analysis charts use netPnlUsdt < 0 because fees can turn tiny pnl wins into net losses"
        ));
        return context;
    }

    private String callDeepSeek(Map<String, Object> context) {
        String endpoint = trimTrailingSlash(config.getBaseUrl()) + "/chat/completions";
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", config.getModel());
        requestBody.put("stream", false);
        requestBody.put("temperature", 0.15);
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
            log.info("[DeepSeek Loss] requesting model={} payload={} bytes", config.getModel(), body.getBytes(StandardCharsets.UTF_8).length);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(Math.max(25, config.getTimeoutSeconds())))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getKey().trim())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[DeepSeek Loss] API failed status={} body={}", response.statusCode(), bodyExcerpt(response.body()));
                throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                        "DeepSeek 亏损分析接口请求失败（HTTP " + response.statusCode() + "）");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText("");
            if (content == null || content.isBlank()) {
                content = message.path("reasoning_content").asText("");
            }
            if (content == null || content.isBlank()) {
                throw new AiApiException(PROVIDER, "/chat/completions", response.statusCode(),
                        "DeepSeek 未返回亏损分析内容");
            }
            return content;
        } catch (JsonProcessingException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 亏损分析请求体序列化失败", e);
        } catch (IOException e) {
            throw new AiApiException(PROVIDER, "/chat/completions", null,
                    "DeepSeek 亏损分析网络请求失败：" + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiApiException(PROVIDER, "/chat/completions", null, "DeepSeek 亏损分析请求被中断", e);
        }
    }

    private Map<String, Object> parseResult(String raw, LocalDateTime now, Map<String, Object> context) {
        JsonNode root = tryParseJsonObject(raw);
        if (root == null) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("model", config.getModel());
            fallback.put("generatedAt", now.format(DT_FMT));
            fallback.put("headline", "DeepSeek 返回内容无法结构化");
            fallback.put("summary", raw);
            fallback.put("rootCauses", List.of());
            fallback.put("actionPlan", List.of("人工阅读 DeepSeek 原文，并按最大亏损、杠杆和复盘标签复核。"));
            fallback.put("riskRules", List.of("任何单笔计划亏损不得超过账户预设风险预算。"));
            fallback.put("dataChecks", context.get("dataRules"));
            fallback.put("contextSummary", context.get("summary"));
            return fallback;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", config.getModel());
        result.put("generatedAt", now.format(DT_FMT));
        result.put("headline", text(root.path("headline").asText("亏损归因已生成")));
        result.put("summary", text(root.path("summary").asText("")));
        result.put("rootCauses", objectList(root.path("rootCauses")));
        result.put("actionPlan", stringList(root.path("actionPlan")));
        result.put("riskRules", stringList(root.path("riskRules")));
        result.put("dataChecks", stringList(root.path("dataChecks")));
        result.put("contextSummary", context.get("summary"));
        return result;
    }

    private String systemPrompt() {
        return """
                你是专业的加密货币合约交易复盘分析师，只基于用户提供的真实订单统计和复盘标签分析亏损原因。
                不要编造价格、新闻、链上数据或不存在的行情。重点判断：亏损是否来自逆势、追单、止损慢、仓位/杠杆过重、持仓拖延、时段问题、技术信号失效、复盘覆盖不足。
                输出必须是一个 JSON 对象，不要 Markdown，不要代码块，不要额外解释。
                """;
    }

    private String userPrompt(Map<String, Object> context) throws JsonProcessingException {
        String contextJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(context);
        return """
                请输出以下 JSON 字段：
                {
                  "headline": "一句话结论，最多 36 个中文字符",
                  "summary": "用 80-160 个中文字符总结亏损主因，必须引用样本数、胜率或总亏损等证据",
                  "rootCauses": [
                    {"title":"主因名称","severity":"高/中/低","evidence":"引用输入数据里的证据","fix":"具体修正动作"}
                  ],
                  "actionPlan": ["5 到 8 条可执行动作，按优先级排序"],
                  "riskRules": ["4 到 6 条以后必须遵守的风险规则"],
                  "dataChecks": ["3 到 5 条说明数据口径和需要继续补充的字段"]
                }
                要求 rootCauses 至少 5 条，必须结合复盘标签、杠杆、持仓时间、方向、时段和最大亏损样本做判断。
                输入数据：
                """ + contextJson;
    }

    private Map<String, OrderReview> loadReviewMap(List<OkxOrder> orders) {
        List<String> ordIds = orders.stream().map(OkxOrder::getOrdId).toList();
        if (ordIds.isEmpty()) return Map.of();
        return reviewRepo.findAllByOrdIdIn(ordIds).stream()
                .collect(Collectors.toMap(OrderReview::getOrdId, r -> r, (a, b) -> a));
    }

    private List<Map<String, Object>> topReasonTags(List<OkxOrder> lossOrders, Map<String, OrderReview> reviewMap,
                                                    String field, int limit) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (OkxOrder order : lossOrders) {
            OrderReview review = reviewMap.get(order.getOrdId());
            if (review == null) continue;
            String csv = switch (field) {
                case "ema" -> review.getEmaSignals();
                case "kdj" -> review.getKdjSignals();
                case "macd" -> review.getMacdSignals();
                default -> review.getReasons();
            };
            for (String item : split(csv)) {
                counts.merge(item, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "count", e.getValue()))
                .toList();
    }

    private List<Map<String, Object>> lossByLeverage(List<OkxOrder> closedOrders) {
        return closedOrders.stream()
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
                    row.put("winRatePct", pct(wins, group.size()));
                    row.put("totalLossUsdt", r2(losses.stream().mapToDouble(this::lossAmount).sum()));
                    row.put("avgLossUsdt", r2(avg(losses.stream().mapToDouble(this::lossAmount).toArray())));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> lossByDirection(List<OkxOrder> lossOrders) {
        return lossOrders.stream()
                .collect(Collectors.groupingBy(o -> blankToDefault(o.getPosSide(), "net"), LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("direction", e.getKey());
                    row.put("lossCount", e.getValue().size());
                    row.put("totalLossUsdt", r2(e.getValue().stream().mapToDouble(this::lossAmount).sum()));
                    row.put("avgLossUsdt", r2(avg(e.getValue().stream().mapToDouble(this::lossAmount).toArray())));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> lossByHoldingBucket(List<OkxOrder> lossOrders) {
        int[][] buckets = {{0, 5}, {5, 15}, {15, 30}, {30, 60}, {60, 120}, {120, 240}, {240, 480}, {480, 1440}, {1440, Integer.MAX_VALUE}};
        String[] labels = {"<5min", "5-15min", "15-30min", "30-60min", "1-2h", "2-4h", "4-8h", "8-24h", ">24h"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            int lo = buckets[i][0], hi = buckets[i][1];
            List<OkxOrder> group = lossOrders.stream()
                    .filter(o -> o.getHoldingMinutes() != null && o.getHoldingMinutes() >= lo && o.getHoldingMinutes() < hi)
                    .toList();
            rows.add(Map.of(
                    "bucket", labels[i],
                    "lossCount", group.size(),
                    "totalLossUsdt", r2(group.stream().mapToDouble(this::lossAmount).sum()),
                    "avgLossUsdt", r2(avg(group.stream().mapToDouble(this::lossAmount).toArray()))
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> lossByHour(List<OkxOrder> lossOrders) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            final int h = hour;
            List<OkxOrder> group = lossOrders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().getHour() == h)
                    .toList();
            rows.add(Map.of(
                    "hour", hour,
                    "lossCount", group.size(),
                    "totalLossUsdt", r2(group.stream().mapToDouble(this::lossAmount).sum())
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> lossByWeekday(List<OkxOrder> lossOrders) {
        String[] names = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            List<OkxOrder> group = lossOrders.stream()
                    .filter(o -> o.getCreateTime() != null && o.getCreateTime().getDayOfWeek().getValue() == d)
                    .toList();
            rows.add(Map.of(
                    "weekday", names[day - 1],
                    "lossCount", group.size(),
                    "totalLossUsdt", r2(group.stream().mapToDouble(this::lossAmount).sum())
            ));
        }
        return rows;
    }

    private List<Map<String, Object>> monthlyLossTrend(List<OkxOrder> lossOrders) {
        return lossOrders.stream()
                .filter(o -> o.getCreateTime() != null)
                .collect(Collectors.groupingBy(o -> o.getCreateTime().toLocalDate().withDayOfMonth(1).toString().substring(0, 7),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(e -> Map.<String, Object>of(
                        "month", e.getKey(),
                        "lossCount", e.getValue().size(),
                        "totalLossUsdt", r2(e.getValue().stream().mapToDouble(this::lossAmount).sum()),
                        "avgLossUsdt", r2(avg(e.getValue().stream().mapToDouble(this::lossAmount).toArray()))
                ))
                .toList();
    }

    private List<Map<String, Object>> worstLossOrders(List<OkxOrder> lossOrders, Map<String, OrderReview> reviewMap, int limit) {
        return lossOrders.stream()
                .sorted(Comparator.comparingDouble(this::lossAmount).reversed())
                .limit(limit)
                .map(o -> {
                    OrderReview review = reviewMap.get(o.getOrdId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("time", o.getCreateTime() == null ? "" : o.getCreateTime().format(DT_FMT));
                    row.put("instId", o.getInstId());
                    row.put("direction", o.getPosSide());
                    row.put("lever", leverLabel(o.getLever()));
                    row.put("lossUsdt", r2(lossAmount(o)));
                    row.put("feeUsdt", r2(fee(o)));
                    row.put("holdingMinutes", o.getHoldingMinutes());
                    row.put("reviewReasons", split(review == null ? null : review.getReasons()));
                    row.put("emaSignals", split(review == null ? null : review.getEmaSignals()));
                    row.put("kdjSignals", split(review == null ? null : review.getKdjSignals()));
                    row.put("macdSignals", split(review == null ? null : review.getMacdSignals()));
                    return row;
                })
                .toList();
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

    private List<Map<String, Object>> objectList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<Map<String, Object>> list = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isObject()) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            item.fields().forEachRemaining(e -> row.put(e.getKey(), e.getValue().isNumber()
                    ? e.getValue().numberValue()
                    : e.getValue().asText("")));
            list.add(row);
        }
        return list;
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

    private List<String> split(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean hasReview(OrderReview r) {
        return r != null && (notBlank(r.getReviewText()) || notBlank(r.getReasons())
                || notBlank(r.getEmaSignals()) || notBlank(r.getKdjSignals()) || notBlank(r.getMacdSignals()));
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
        return value == null ? 0 : value.doubleValue();
    }

    private double avg(double[] values) {
        return values.length == 0 ? 0 : Arrays.stream(values).average().orElse(0);
    }

    private double pct(long numerator, long denominator) {
        return denominator <= 0 ? 0 : r2((double) numerator / denominator * 100);
    }

    private double r2(double value) {
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

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private String bodyExcerpt(String body) {
        String text = text(body).replaceAll("\\s+", " ");
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }
}
