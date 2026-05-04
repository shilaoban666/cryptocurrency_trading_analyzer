package com.okx.analyzer.repository;

import com.okx.analyzer.entity.LiquidationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface LiquidationRecordRepository extends JpaRepository<LiquidationRecord, Long> {

    boolean existsByBillId(String billId);

    List<LiquidationRecord> findAllByOrderByLiquidationTimeDesc();

    @Query("SELECT DISTINCT r.instId FROM LiquidationRecord r ORDER BY r.instId")
    List<String> findDistinctInstIds();

    long count();
}
