package com.okx.analyzer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "okx_order")
public class OkxOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String ordId;

    @Column(nullable = false, length = 32)
    private String instId;

    @Column(nullable = false, length = 16)
    private String instType;

    @Column(nullable = false, length = 8)
    private String side;

    @Column(length = 8)
    private String posSide;

    @Column(nullable = false, length = 16)
    private String ordType;

    @Column(precision = 20, scale = 8)
    private BigDecimal sz;

    @Column(precision = 20, scale = 8)
    private BigDecimal px;

    @Column(precision = 20, scale = 8)
    private BigDecimal avgPx;

    @Column(precision = 20, scale = 8)
    private BigDecimal fillSz;

    @Column(precision = 20, scale = 8)
    private BigDecimal pnl;

    @Column(precision = 20, scale = 8)
    private BigDecimal fee;

    @Column(length = 8)
    private String lever;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(nullable = false)
    private LocalDateTime createTime;

    @Column(nullable = false)
    private LocalDateTime updateTime;

    private Integer holdingMinutes;

    /** 1=盈 0=亏 null=开仓单 */
    private Integer isWin;

    /** 1=强平单 0=普通单 */
    private Integer isLiquidation;

    @Column(columnDefinition = "TEXT")
    private String rawData;

    @Column(updatable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    void prePersist() { syncedAt = LocalDateTime.now(); }
}
