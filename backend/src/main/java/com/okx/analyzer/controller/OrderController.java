package com.okx.analyzer.controller;

import com.okx.analyzer.entity.OkxOrder;
import com.okx.analyzer.repository.OkxOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OkxOrderRepository orderRepo;

    /** 分页查询订单 */
    @GetMapping
    public Map<String, Object> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String instId,
            @RequestParam(required = false) String side,
            @RequestParam(required = false) String state) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createTime").descending());
        Page<OkxOrder> result = orderRepo.findWithFilter(instId, side, state, pageable);

        return Map.of(
                "list",  result.getContent(),
                "total", result.getTotalElements(),
                "pages", result.getTotalPages()
        );
    }

    /** 品种列表（用于筛选下拉） */
    @GetMapping("/symbols")
    public List<String> symbols() {
        return orderRepo.findDistinctInstIds();
    }

    /** 数据库中总单数 */
    @GetMapping("/count")
    public Map<String, Long> count() {
        return Map.of(
                "total",  orderRepo.count(),
                "filled", orderRepo.countByState("filled")
        );
    }
}
