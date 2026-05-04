package com.okx.analyzer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "liquidation_record")
public class LiquidationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String billId;

    @Column(nullable = false, length = 32)
    private String instId;

    @Column(length = 16)
    private String instType;

    @Column(length = 8)
    private String posSide;

    @Column(precision = 20, scale = 8)
    private BigDecimal pnl;

    @Column(precision = 20, scale = 8)
    private BigDecimal fee;

    @Column(precision = 20, scale = 8)
    private BigDecimal sz;

    @Column(precision = 20, scale = 8)
    private BigDecimal px;

    @Column(precision = 20, scale = 8)
    private BigDecimal balanceAfter;

    @Column(nullable = false)
    private LocalDateTime liquidationTime;

    @Column(updatable = false)
    private LocalDateTime syncedAt;

    @PrePersist
    void prePersist() { syncedAt = LocalDateTime.now(); }
}
