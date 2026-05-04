package com.okx.analyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.okx.analyzer.config.OkxConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OkxApiService {

    private static final int PAGE_LIMIT = 100;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String USER_AGENT = "okx-analyzer/1.0";

    private final OkxConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpClient httpClient;

    @PostConstruct
    void initHttpClient() {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_1_1);

        String proxyHost = config.getProxyHost();
        Integer proxyPort = config.getProxyPort();
        if (proxyHost != null && !proxyHost.isBlank() && proxyPort != null && proxyPort > 0) {
            log.info("[OKX] HTTP 代理已启用: {}:{}", proxyHost, proxyPort);
            builder.proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)));
        }

        this.httpClient = builder.build();
    }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                             .withZone(ZoneOffset.UTC);

    /** 拉取所有历史合约成交单（自动翻页） */
    public List<JsonNode> fetchAllContractOrders(String instType) {
        List<JsonNode> all = new ArrayList<>();
        String after = null;   // 翻页游标（上一批最早的 ordId）
        boolean archiveSuccess = false;
        long beginMs = historyBeginMs();
        log.info("[OKX] 拉取 {} 月内订单，begin={} ({})",
                config.getHistoryMonths(), beginMs, Instant.ofEpochMilli(beginMs));

        try {
            while (true) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("instType", instType);
                params.put("state", "filled");
                params.put("limit", PAGE_LIMIT);
                params.put("begin", beginMs);
                if (after != null) {
                    params.put("after", after);
                }

                JsonNode data = get(buildPath("/api/v5/trade/orders-history-archive", params));
                archiveSuccess = true;
                if (!hasRows(data)) break;

                appendAll(all, data);

                // OKX 按时间倒序返回，取最后一条的 ordId 作为下一页游标
                after = data.get(data.size() - 1).path("ordId").asText();
                log.info("[OKX] 已获取 {} 条，继续翻页 after={}", all.size(), after);

                if (data.size() < PAGE_LIMIT) break;  // 最后一页
            }
        } catch (OkxApiException e) {
            log.warn("[OKX] 归档历史订单拉取失败，将继续尝试近 7 天订单: {}", e.getMessage());
        }

        // 补抓近7天（orders-history 接口，包含更新数据）
        Map<String, Object> recentParams = new LinkedHashMap<>();
        recentParams.put("instType", instType);
        recentParams.put("state", "filled");
        recentParams.put("limit", PAGE_LIMIT);

        try {
            JsonNode recent = get(buildPath("/api/v5/trade/orders-history", recentParams));
            if (recent != null && recent.isArray()) {
                appendAll(all, recent);
            }
        } catch (OkxApiException e) {
            if (!archiveSuccess) {
                throw e;
            }
            log.warn("[OKX] 近 7 天历史订单拉取失败，已保留归档结果: {}", e.getMessage());
        }

        log.info("[OKX] 共获取 {} 条历史订单", all.size());
        return all;
    }

    /** 发送 GET 请求，返回 data 节点 */
    public JsonNode get(String requestPath) {
        validateSignedConfig();
        return executeGet(requestPath, true, "[OKX]");
    }

    /** 拉取账单历史（type=8 强平），自动翻页 */
    public List<JsonNode> fetchAllBillsByType(String type) {
        List<JsonNode> all = new ArrayList<>();
        String after = null;
        boolean archiveSuccess = false;
        long beginMs = historyBeginMs();

        try {
            while (true) {
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("type", type);
                params.put("limit", PAGE_LIMIT);
                params.put("begin", beginMs);
                if (after != null) {
                    params.put("after", after);
                }

                JsonNode data = get(buildPath("/api/v5/account/bills-archive", params));
                archiveSuccess = true;
                if (!hasRows(data)) break;

                appendAll(all, data);
                after = data.get(data.size() - 1).path("billId").asText();
                log.info("[OKX Bills] 已获取 {} 条，after={}", all.size(), after);

                if (data.size() < PAGE_LIMIT) break;
            }
        } catch (OkxApiException e) {
            log.warn("[OKX Bills] 归档账单拉取失败，将继续尝试近 7 天账单: {}", e.getMessage());
        }

        // 补拉近7天账单
        Map<String, Object> recentParams = new LinkedHashMap<>();
        recentParams.put("type", type);
        recentParams.put("limit", PAGE_LIMIT);

        try {
            JsonNode recent = get(buildPath("/api/v5/account/bills", recentParams));
            if (recent != null && recent.isArray()) {
                appendAll(all, recent);
            }
        } catch (OkxApiException e) {
            if (!archiveSuccess) {
                throw e;
            }
            log.warn("[OKX Bills] 近 7 天账单拉取失败，已保留归档结果: {}", e.getMessage());
        }

        log.info("[OKX Bills] type={} 共获取 {} 条", type, all.size());
        return all;
    }

    /** 不需要签名的公开 API（行情数据） */
    public JsonNode getPublic(String requestPath) {
        validateBaseUrl();
        return executeGet(requestPath, false, "[OKX Public]");
    }

    private JsonNode executeGet(String requestPath, boolean signed, String logPrefix) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + requestPath))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT);

            if (signed) {
                String timestamp = TS_FMT.format(Instant.now());
                String sign = sign(timestamp, "GET", requestPath, "");
                builder.header("OK-ACCESS-KEY", config.getKey())
                        .header("OK-ACCESS-SIGN", sign)
                        .header("OK-ACCESS-TIMESTAMP", timestamp)
                        .header("OK-ACCESS-PASSPHRASE", config.getPassphrase());

                if (config.isSimulated()) {
                    builder.header("x-simulated-trading", "1");
                }
            }

            HttpRequest request = builder.GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("{} HTTP 请求失败: status={} path={} body={}",
                        logPrefix, response.statusCode(), requestPath, bodyExcerpt(responseBody));
                throw new OkxApiException(requestPath, response.statusCode(), null,
                        "OKX 接口请求失败（HTTP " + response.statusCode() + "）");
            }

            JsonNode root = objectMapper.readTree(responseBody);

            if (!"0".equals(root.path("code").asText())) {
                String okxCode = root.path("code").asText();
                String okxMsg = blankToDefault(root.path("msg").asText(), "未知错误");
                log.error("{} API 错误: path={} status={} code={} msg={} body={}",
                        logPrefix, requestPath, response.statusCode(), okxCode, okxMsg, bodyExcerpt(responseBody));
                throw new OkxApiException(requestPath, response.statusCode(), okxCode,
                        "OKX 接口错误（code=" + okxCode + "）：" + okxMsg);
            }
            return root.path("data");
        } catch (IOException e) {
            log.error("{} 请求失败: path={} type={} err={}",
                    logPrefix, requestPath, e.getClass().getSimpleName(), blankToDefault(e.getMessage(), "无详细消息"), e);
            throw new OkxApiException(requestPath, null, null,
                    "OKX 网络请求失败：" + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{} 请求被中断: path={} type={} err={}",
                    logPrefix, requestPath, e.getClass().getSimpleName(), blankToDefault(e.getMessage(), "无详细消息"), e);
            throw new OkxApiException(requestPath, null, null,
                    "OKX 请求被中断", e);
        } catch (IllegalArgumentException e) {
            log.error("{} 请求参数非法: path={} err={}",
                    logPrefix, requestPath, blankToDefault(e.getMessage(), "无详细消息"), e);
            throw new OkxApiException(requestPath, null, null,
                    "OKX 请求参数非法", e);
        }
    }

    private long historyBeginMs() {
        int months = config.getHistoryMonths() > 0 ? config.getHistoryMonths() : 6;
        return Instant.now().minus(Duration.ofDays(30L * months)).toEpochMilli();
    }

    private void validateSignedConfig() {
        validateBaseUrl();
        if (isBlank(config.getKey()) || isBlank(config.getSecret()) || isBlank(config.getPassphrase())) {
            throw new OkxApiException(null, null, null, "OKX API 密钥未配置完整，请检查 key / secret / passphrase");
        }
    }

    private void validateBaseUrl() {
        if (isBlank(config.getBaseUrl())) {
            throw new OkxApiException(null, null, null, "OKX baseUrl 未配置");
        }
    }

    private void appendAll(List<JsonNode> all, JsonNode data) {
        for (JsonNode node : data) {
            all.add(node);
        }
    }

    private boolean hasRows(JsonNode data) {
        return data != null && data.isArray() && !data.isEmpty();
    }

    private String buildPath(String path, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return path;
        }
        String query = params.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().toString().isBlank())
                .map(entry -> encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
        return query.isEmpty() ? path : path + "?" + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String bodyExcerpt(String responseBody) {
        String normalized = blankToDefault(responseBody, "<empty>")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
    }

    private String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String sign(String timestamp, String method, String requestPath, String body) {
        try {
            String preHash = timestamp + method + requestPath + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(config.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(
                    mac.doFinal(preHash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("OKX 请求签名失败", e);
        }
    }
}
