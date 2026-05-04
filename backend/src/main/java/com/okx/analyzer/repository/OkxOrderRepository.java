package com.okx.analyzer.repository;

import com.okx.analyzer.entity.OkxOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OkxOrderRepository extends JpaRepository<OkxOrder, Long> {

    boolean existsByOrdId(String ordId);

    Optional<OkxOrder> findTopByOrderByCreateTimeDesc();

    /** 所有已成交的关仓单（pnl 非零） */
    @Query("SELECT o FROM OkxOrder o WHERE o.state = 'filled' AND o.isWin IS NOT NULL ORDER BY o.createTime ASC")
    List<OkxOrder> findAllClosedOrders();

    /** 按时间范围查询关仓单 */
    @Query("SELECT o FROM OkxOrder o WHERE o.state = 'filled' AND o.isWin IS NOT NULL " +
           "AND o.createTime BETWEEN :start AND :end ORDER BY o.createTime ASC")
    List<OkxOrder> findClosedOrdersBetween(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** 分页查所有单 */
    @Query("SELECT o FROM OkxOrder o WHERE " +
           "(:instId IS NULL OR o.instId = :instId) AND " +
           "(:side IS NULL OR o.posSide = :side) AND " +
           "(:state IS NULL OR o.state = :state) " +
           "ORDER BY o.createTime DESC")
    Page<OkxOrder> findWithFilter(@Param("instId") String instId,
                                   @Param("side") String side,
                                   @Param("state") String state,
                                   Pageable pageable);

    /** 所有品种列表 */
    @Query("SELECT DISTINCT o.instId FROM OkxOrder o ORDER BY o.instId")
    List<String> findDistinctInstIds();

    /** 总单数 */
    long countByState(String state);

    /** 已关仓单数量（用于复盘进度统计）*/
    @Query("SELECT COUNT(o) FROM OkxOrder o WHERE o.state = 'filled' AND o.isWin IS NOT NULL")
    long countClosedOrders();

    /** 复盘页订单列表：已关仓单，支持按品种/结果筛选 */
    @Query("SELECT o FROM OkxOrder o WHERE o.state = 'filled' AND o.isWin IS NOT NULL " +
           "AND (:instId IS NULL OR o.instId = :instId) " +
           "AND (:isWin IS NULL OR o.isWin = :isWin) " +
           "ORDER BY o.createTime DESC")
    Page<OkxOrder> findClosedWithFilter(@Param("instId") String instId,
                                        @Param("isWin") Integer isWin,
                                        Pageable pageable);

    /** 批量按 ordId 查询（用于复盘状态关联）*/
    List<OkxOrder> findAllByOrdIdIn(List<String> ordIds);
}
