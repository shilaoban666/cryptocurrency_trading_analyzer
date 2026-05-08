package com.okx.analyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "deepseek.api")
public class DeepSeekConfig {
    private String key;
    private String baseUrl = "https://api.deepseek.com";
    private String model = "deepseek-v4-pro";
    private int timeoutSeconds = 90;
    private int maxTokens = 2200;
    private boolean thinkingEnabled = true;
    private String reasoningEffort = "high";
}
