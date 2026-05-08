package com.okx.analyzer.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PositionDto {
    private String instId;
    private String posSide;
    private String direction;
    private double size;
    private double avgPx;
    private double markPx;
    private double upl;
    private double uplRatioPct;
    private double notional;
    private double liqPx;
    private String lever;
    private String marginMode;
    private long holdingMinutes;
    private int addCount;
    private String addStatus;
    private String marketTrend;
    private String alignment;
    private String divergence;
    private double liquidationDistancePct;
}
