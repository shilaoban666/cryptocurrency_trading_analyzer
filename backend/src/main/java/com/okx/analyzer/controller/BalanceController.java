package com.okx.analyzer.controller;

import com.okx.analyzer.dto.BalanceDto;
import com.okx.analyzer.service.BalanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/balance")
@RequiredArgsConstructor
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping
    public BalanceDto getBalance() {
        return balanceService.getBalance();
    }
}
