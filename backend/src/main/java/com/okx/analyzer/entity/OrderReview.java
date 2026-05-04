package com.okx.analyzer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "order_review")
public class OrderReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 64)
    private String ordId;

    @Column(columnDefinition = "TEXT")
    private String reviewText;

    /** 原因标签，逗号分隔 */
    @Column(columnDefinition = "TEXT")
    private String reasons;

    /** EMA指标状态，逗号分隔（亏损单专属） */
    @Column(columnDefinition = "TEXT")
    private String emaSignals;

    /** KDJ指标状态，逗号分隔（亏损单专属） */
    @Column(columnDefinition = "TEXT")
    private String kdjSignals;

    /** MACD指标状态，逗号分隔（亏损单专属） */
    @Column(columnDefinition = "TEXT")
    private String macdSignals;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
