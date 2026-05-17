package com.okx.analyzer.service;

import com.okx.analyzer.entity.CryptoTradingInsight;
import com.okx.analyzer.repository.CryptoTradingInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CryptoTradingInsightService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_ITEMS_PER_FEED = 16;
    private static final List<FeedSource> FEEDS = List.of(
            new FeedSource("Cointelegraph BTC", "https://cointelegraph.com/rss/tag/bitcoin", 28),
            new FeedSource("Cointelegraph Market", "https://cointelegraph.com/rss/category/market-analysis", 30),
            new FeedSource("Decrypt", "https://decrypt.co/feed", 24),
            new FeedSource("CoinDesk", "https://feeds.feedburner.com/CoinDesk", 26),
            new FeedSource("Bitcoin Magazine", "https://bitcoinmagazine.com/feed", 22)
    );

    private final CryptoTradingInsightRepository insightRepo;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Transactional
    public Map<String, Object> listInsights(int limit) {
        int safeLimit = Math.max(20, Math.min(limit, 300));
        if (insightRepo.count() == 0) {
            return refreshInsights(safeLimit);
        }
        return response(safeLimit, 0);
    }

    @Transactional
    public Map<String, Object> refreshInsights() {
        return refreshInsights(180);
    }

    @Transactional
    public Map<String, Object> refreshInsights(int limit) {
        int added = 0;
        for (FeedSource source : FEEDS) {
            try {
                added += collectFeed(source);
            } catch (Exception e) {
                log.warn("[Insights] feed collect failed, source={}, error={}", source.name(), rootMessage(e));
            }
        }

        if (insightRepo.count() == 0) {
            added += saveSeedInsights();
        }

        return response(limit, added);
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "Asia/Shanghai")
    @Transactional
    public void scheduledRefresh() {
        Map<String, Object> result = refreshInsights();
        log.info("[Insights] scheduled refresh done, added={}, total={}",
                result.get("added"), result.get("total"));
    }

    private int collectFeed(FeedSource source) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(source.url()))
                .timeout(Duration.ofSeconds(18))
                .header("User-Agent", "okx-analyzer/1.0 (+daily trading insight collector)")
                .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        Document doc = parseXml(response.body());
        List<Element> items = rssItems(doc);
        int added = 0;
        int count = 0;
        for (Element item : items) {
            if (++count > MAX_ITEMS_PER_FEED) break;
            Optional<CryptoTradingInsight> parsed = parseItem(source, item);
            if (parsed.isEmpty()) continue;

            CryptoTradingInsight insight = parsed.get();
            if (insightRepo.existsByContentHash(insight.getContentHash())) continue;

            insightRepo.save(insight);
            added++;
        }
        log.info("[Insights] source={} added={}", source.name(), added);
        return added;
    }

    private Optional<CryptoTradingInsight> parseItem(FeedSource source, Element item) {
        String title = cleanText(childText(item, "title"));
        String link = cleanText(childText(item, "link"));
        if (link.isBlank()) link = cleanText(childAttribute(item, "link", "href"));
        if (title.isBlank() || link.isBlank()) return Optional.empty();

        String description = stripHtml(childText(item, "description", "summary"));
        LocalDateTime publishedAt = parseTime(childText(item, "pubDate", "published", "updated"));
        String text = (title + " " + description).toLowerCase(Locale.ROOT);
        String category = category(text);
        String insightType = insightType(text);
        String successFactor = successFactor(category);
        String marketPhase = marketPhase(text);
        List<String> tags = tags(text, category, successFactor, marketPhase);
        int keywordHits = keywordHits(text);

        CryptoTradingInsight insight = new CryptoTradingInsight();
        insight.setContentHash(hash(title + "|" + link));
        insight.setTitle(truncate(title, 500));
        insight.setSourceName(source.name());
        insight.setSourceUrl(source.url());
        insight.setOriginalUrl(link);
        insight.setCategory(category);
        insight.setInsightType(insightType);
        insight.setSummary(buildSummary(title, description, category, marketPhase));
        insight.setTaboo(taboo(category, text));
        insight.setShouldDo(shouldDo(category, text));
        insight.setSuccessFactor(successFactor);
        insight.setMarketPhase(marketPhase);
        insight.setTags(String.join(",", tags));
        insight.setHeatScore(score(heatScore(source.weight(), publishedAt, keywordHits)));
        insight.setConfidenceScore(score(confidenceScore(description, keywordHits)));
        insight.setPublishedAt(publishedAt);
        return Optional.of(insight);
    }

    private Map<String, Object> response(int limit, int added) {
        int safeLimit = Math.max(20, Math.min(limit, 300));
        List<CryptoTradingInsight> list =
                insightRepo.findByOrderByHeatScoreDescPublishedAtDesc(PageRequest.of(0, safeLimit));
        Optional<CryptoTradingInsight> latest = insightRepo.findTopByOrderByCollectedAtDesc();
        LocalDateTime todayStart = LocalDateTime.now(ZONE).toLocalDate().atStartOfDay();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", insightRepo.count());
        result.put("added", added);
        result.put("todayAdded", insightRepo.countByCollectedAtAfter(todayStart));
        result.put("lastCollectedAt", latest.map(CryptoTradingInsight::getCollectedAt).orElse(null));
        result.put("sources", FEEDS.stream().map(FeedSource::name).toList());
        return result;
    }

    private int saveSeedInsights() {
        List<CryptoTradingInsight> seeds = List.of(
                seed("交易第一原则：先保命，再谈收益", "风险控制", "忌讳", "亏损截断",
                        "不要用一次交易决定账户命运，不要在没有止损和最大亏损预算时进场。",
                        "每笔交易先定义失效点、最大亏损、仓位上限，再等待价格验证。"),
                seed("高杠杆连续加仓是合约账户最常见的毁灭路径", "仓位管理", "忌讳", "风险预算",
                        "不要逆势补仓摊平，更不要在亏损扩大时提高杠杆。",
                        "把补仓当成新的交易计划，只有趋势、结构和风险收益比重新达标才执行。"),
                seed("趋势单要让利润奔跑，震荡单要快速兑现", "趋势跟随", "做法", "顺势等待",
                        "不要在强趋势里过早止盈，也不要在震荡里幻想单边行情。",
                        "用均线、前高前低、成交量和波动率区分趋势与区间。"),
                seed("FOMO 追涨杀跌通常来自没有交易计划", "情绪纪律", "忌讳", "纪律执行",
                        "不要因为短线拉盘、社群观点或亏损后的急躁而临时追单。",
                        "开仓前写下入场理由、失效条件和离场动作，盘中只执行。"),
                seed("复盘不是记录盈亏，而是识别重复错误", "计划复盘", "策略原则", "可验证流程",
                        "不要只看结果对错，忽略是否遵守计划和是否冒了不必要的风险。",
                        "把每笔交易拆成入场质量、仓位质量、退出质量和情绪状态。"),
                seed("资金费率和未平仓量能暴露拥挤方向", "市场结构", "做法", "流动性识别",
                        "不要在资金费率极端、OI 过热时盲目追随拥挤方向。",
                        "结合价格是否继续创新高/新低，判断是趋势确认还是杠杆挤压。"),
                seed("大行情常先清掉高杠杆，再选择方向", "市场结构", "风险提示", "流动性识别",
                        "不要把插针当成随机噪声，也不要把强平区域附近的止损放得过于密集。",
                        "关注前高前低、密集成交区和清算区，避开容易被扫的位置。"),
                seed("只交易自己能解释清楚的机会", "交易框架", "策略原则", "信号一致性",
                        "不要为了交易次数而降低标准，也不要混用相互冲突的指标。",
                        "用少数核心信号建立一致框架：趋势、位置、量能、风险收益比。")
        );

        int added = 0;
        for (CryptoTradingInsight seed : seeds) {
            if (insightRepo.existsByContentHash(seed.getContentHash())) continue;
            insightRepo.save(seed);
            added++;
        }
        return added;
    }

    private CryptoTradingInsight seed(String title, String category, String type, String factor,
                                      String taboo, String shouldDo) {
        CryptoTradingInsight insight = new CryptoTradingInsight();
        insight.setContentHash(hash("seed|" + title));
        insight.setTitle(title);
        insight.setSourceName("内置原则");
        insight.setSourceUrl("system://seed");
        insight.setOriginalUrl("system://seed/" + hash(title).substring(0, 12));
        insight.setCategory(category);
        insight.setInsightType(type);
        insight.setSummary("内置交易原则：" + shouldDo);
        insight.setTaboo(taboo);
        insight.setShouldDo(shouldDo);
        insight.setSuccessFactor(factor);
        insight.setMarketPhase("通用环境");
        insight.setTags(String.join(",", List.of("BTC", "ETH", category, factor)));
        insight.setHeatScore(score(72));
        insight.setConfidenceScore(score(88));
        insight.setPublishedAt(LocalDateTime.now(ZONE));
        return insight;
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setExpandEntityReferences(false);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl");
        setFeature(factory, "http://xml.org/sax/features/external-general-entities");
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities");
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();
        return doc;
    }

    private void setFeature(DocumentBuilderFactory factory, String feature) {
        try {
            boolean enabled = !feature.contains("external-");
            factory.setFeature(feature, enabled);
        } catch (Exception ignored) {
            log.debug("[Insights] XML parser does not support feature {}", feature);
        }
    }

    private List<Element> rssItems(Document doc) {
        NodeList nodes = doc.getElementsByTagName("item");
        if (nodes.getLength() == 0) nodes = doc.getElementsByTagName("entry");

        List<Element> items = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) items.add(element);
        }
        return items;
    }

    private String childText(Element item, String... names) {
        for (String name : names) {
            NodeList children = item.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) continue;
                String nodeName = node.getNodeName();
                String localName = node.getLocalName();
                if (name.equalsIgnoreCase(nodeName) || name.equalsIgnoreCase(localName)) {
                    return node.getTextContent() == null ? "" : node.getTextContent();
                }
            }
        }
        return "";
    }

    private String childAttribute(Element item, String childName, String attr) {
        NodeList children = item.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element child)) continue;
            String nodeName = child.getNodeName();
            String localName = child.getLocalName();
            if (childName.equalsIgnoreCase(nodeName) || childName.equalsIgnoreCase(localName)) {
                String value = child.getAttribute(attr);
                if (value != null && !value.isBlank()) return value;
            }
        }
        return "";
    }

    private LocalDateTime parseTime(String raw) {
        String value = cleanText(raw);
        if (value.isBlank()) return LocalDateTime.now(ZONE);
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .withZoneSameInstant(ZONE)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return OffsetDateTime.parse(value)
                    .atZoneSameInstant(ZONE)
                    .toLocalDateTime();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }
        return LocalDateTime.now(ZONE);
    }

    private String category(String text) {
        if (has(text, "liquidation", "risk", "drawdown", "stop loss", "crash", "loss", "volatile", "volatility")) {
            return "风险控制";
        }
        if (has(text, "position", "leverage", "sizing", "exposure", "margin", "collateral")) {
            return "仓位管理";
        }
        if (has(text, "fomo", "fear", "greed", "panic", "sentiment", "emotion", "discipline")) {
            return "情绪纪律";
        }
        if (has(text, "trend", "breakout", "momentum", "moving average", "ema", "support", "resistance")) {
            return "趋势跟随";
        }
        if (has(text, "plan", "strategy", "backtest", "journal", "review", "setup")) {
            return "计划复盘";
        }
        if (has(text, "funding", "open interest", "liquidity", "order book", "derivatives", "options")) {
            return "市场结构";
        }
        if (has(text, "etf", "whale", "institution", "inflow", "outflow", "on-chain", "miner")) {
            return "资金流向";
        }
        if (has(text, "fed", "rate cut", "inflation", "macro", "dollar", "treasury")) {
            return "宏观周期";
        }
        return "交易框架";
    }

    private String insightType(String text) {
        if (has(text, "mistake", "avoid", "fomo", "panic", "overtrade", "liquidation", "leverage")) {
            return "忌讳";
        }
        if (has(text, "warning", "risk", "crash", "drop", "sell-off", "loss", "danger")) {
            return "风险提示";
        }
        if (has(text, "how", "strategy", "setup", "breakout", "support", "resistance", "plan")) {
            return "做法";
        }
        return "策略原则";
    }

    private String successFactor(String category) {
        return switch (category) {
            case "风险控制" -> "亏损截断";
            case "仓位管理" -> "风险预算";
            case "情绪纪律" -> "纪律执行";
            case "趋势跟随" -> "顺势等待";
            case "计划复盘" -> "可验证流程";
            case "市场结构" -> "流动性识别";
            case "资金流向" -> "跟踪主导资金";
            case "宏观周期" -> "顺周期过滤";
            default -> "信号一致性";
        };
    }

    private String marketPhase(String text) {
        if (has(text, "bull", "rally", "surge", "ath", "all-time high")) return "多头趋势";
        if (has(text, "bear", "crash", "sell-off", "plunge", "drop", "downtrend")) return "空头压力";
        if (has(text, "volatile", "volatility", "swing")) return "高波动";
        if (has(text, "range", "sideways", "consolidation")) return "震荡整理";
        if (has(text, "funding", "liquidation", "open interest", "leverage")) return "杠杆清算";
        return "结构观察";
    }

    private List<String> tags(String text, String category, String factor, String phase) {
        List<String> tags = new ArrayList<>();
        if (has(text, "bitcoin", "btc")) tags.add("BTC");
        if (has(text, "ethereum", "ether", "eth")) tags.add("ETH");
        if (has(text, "solana", "sol")) tags.add("SOL");
        if (has(text, "funding")) tags.add("资金费率");
        if (has(text, "open interest")) tags.add("OI");
        if (has(text, "liquidation")) tags.add("清算");
        if (has(text, "whale")) tags.add("大户");
        tags.add(category);
        tags.add(factor);
        tags.add(phase);
        return tags.stream().distinct().limit(7).toList();
    }

    private String buildSummary(String title, String description, String category, String phase) {
        String excerpt = truncate(cleanText(description), 110);
        String base = excerpt.isBlank() ? title : excerpt;
        return "从公开市场信息提炼：" + category + "是核心，当前语境偏「" + phase + "」。" +
                "可关注「" + truncate(base, 90) + "」背后的交易条件，而不是直接照搬观点。";
    }

    private String taboo(String category, String text) {
        if ("仓位管理".equals(category)) return "不要满仓、高杠杆、亏损后连续补仓，也不要把单笔风险放到账户无法承受的位置。";
        if ("情绪纪律".equals(category)) return "不要因为 FOMO、恐慌、社群情绪或刚亏完的报复心理临时开单。";
        if ("趋势跟随".equals(category)) return "不要在趋势未破坏时频繁反向摸顶摸底，也不要在震荡里用趋势单逻辑死扛。";
        if ("市场结构".equals(category)) return "不要忽视资金费率、未平仓量、清算密集区和关键流动性位置。";
        if ("计划复盘".equals(category)) return "不要只记录盈亏，不记录入场理由、执行偏差和离场质量。";
        if ("资金流向".equals(category)) return "不要把单一资金流新闻当成买卖信号，尤其不要追逐已经过热的拥挤方向。";
        if ("宏观周期".equals(category)) return "不要在重大宏观事件前忽视波动扩张和滑点风险。";
        return has(text, "leverage") ? "不要在信号不一致时提高杠杆放大噪声。" : "不要无计划入场，也不要让亏损单变成长期赌方向。";
    }

    private String shouldDo(String category, String text) {
        if ("仓位管理".equals(category)) return "按账户权益设定单笔风险上限，分层记录初始仓、补仓仓和无效仓。";
        if ("情绪纪律".equals(category)) return "用预案替代临场冲动：入场、止损、止盈和不交易条件都提前写清楚。";
        if ("趋势跟随".equals(category)) return "先确认趋势方向、关键位置和量能，再用回踩/突破后的失效点管理交易。";
        if ("市场结构".equals(category)) return "把资金费率、OI、清算区、成交量和价格结构放在同一张决策表里。";
        if ("计划复盘".equals(category)) return "按交易前计划、交易中执行、交易后结果三段复盘，找出可重复改进项。";
        if ("资金流向".equals(category)) return "跟踪 ETF/链上/大户/交易所净流向，但必须用价格确认过滤噪声。";
        if ("宏观周期".equals(category)) return "重大数据和议息窗口降低杠杆，等待波动落地后再寻找结构确认。";
        return has(text, "bitcoin") ? "优先在 BTC/ETH 高流动性标的上执行清晰信号。" : "只交易能解释清楚、能量化风险收益比的机会。";
    }

    private int heatScore(int sourceWeight, LocalDateTime publishedAt, int keywordHits) {
        int score = 42 + sourceWeight + Math.min(keywordHits * 3, 16);
        long hours = Duration.between(publishedAt.atZone(ZONE), LocalDateTime.now(ZONE).atZone(ZONE)).toHours();
        if (hours <= 24) score += 18;
        else if (hours <= 72) score += 12;
        else if (hours <= 168) score += 7;
        else score += 2;
        return Math.max(45, Math.min(score, 99));
    }

    private int confidenceScore(String description, int keywordHits) {
        int score = 68 + Math.min(keywordHits * 4, 20);
        if (description != null && description.length() > 80) score += 6;
        return Math.max(60, Math.min(score, 96));
    }

    private int keywordHits(String text) {
        int hits = 0;
        for (String keyword : List.of(
                "bitcoin", "ethereum", "btc", "eth", "risk", "leverage", "liquidation", "funding",
                "open interest", "trend", "breakout", "support", "resistance", "volatility",
                "whale", "inflow", "outflow", "strategy", "macro", "fomo", "greed")) {
            if (text.contains(keyword)) hits++;
        }
        return hits;
    }

    private boolean has(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String stripHtml(String value) {
        if (value == null) return "";
        return cleanText(value.replaceAll("<[^>]+>", " "));
    }

    private String cleanText(String value) {
        if (value == null) return "";
        return value.replace('\u00a0', ' ')
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String value, int max) {
        if (value == null) return "";
        String clean = cleanText(value);
        return clean.length() <= max ? clean : clean.substring(0, max - 1) + "…";
    }

    private BigDecimal score(int value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("hash failed", e);
        }
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage();
    }

    private record FeedSource(String name, String url, int weight) {
    }
}
