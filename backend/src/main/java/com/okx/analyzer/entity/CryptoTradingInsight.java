package com.okx.analyzer.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "crypto_trading_insight")
public class CryptoTradingInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 96)
    private String contentHash;

    @Column(nullable = false, length = 512)
    private String title;

    @Column(length = 80)
    private String sourceName;

    @Column(length = 512)
    private String sourceUrl;

    @Column(length = 1024)
    private String originalUrl;

    @Column(length = 64)
    private String category;

    @Column(length = 32)
    private String insightType;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String taboo;

    @Column(columnDefinition = "TEXT")
    private String shouldDo;

    @Column(length = 80)
    private String successFactor;

    @Column(length = 64)
    private String marketPhase;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @Column(precision = 10, scale = 2)
    private BigDecimal heatScore;

    @Column(precision = 10, scale = 2)
    private BigDecimal confidenceScore;

    private LocalDateTime publishedAt;

    @Column(updatable = false)
    private LocalDateTime collectedAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        collectedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
