package com.okx.analyzer.repository;

import com.okx.analyzer.entity.CryptoTradingInsight;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CryptoTradingInsightRepository extends JpaRepository<CryptoTradingInsight, Long> {
    boolean existsByContentHash(String contentHash);

    Optional<CryptoTradingInsight> findTopByOrderByCollectedAtDesc();

    long countByCollectedAtAfter(LocalDateTime collectedAt);

    List<CryptoTradingInsight> findByOrderByHeatScoreDescPublishedAtDesc(Pageable pageable);
}
