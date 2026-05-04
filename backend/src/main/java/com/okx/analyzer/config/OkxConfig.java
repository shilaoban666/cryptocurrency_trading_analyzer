package com.okx.analyzer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "okx.api")
public class OkxConfig {
    private String key;
    private String secret;
    private String passphrase;
    private String baseUrl;
    private boolean simulated;
    private String proxyHost;
    private Integer proxyPort;
    private int historyMonths = 6;
}
