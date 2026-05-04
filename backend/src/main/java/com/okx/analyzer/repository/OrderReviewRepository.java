package com.okx.analyzer.repository;

import com.okx.analyzer.entity.OrderReview;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrderReviewRepository extends JpaRepository<OrderReview, Long> {
    Optional<OrderReview> findByOrdId(String ordId);
    List<OrderReview> findAllByOrdIdIn(List<String> ordIds);
}
