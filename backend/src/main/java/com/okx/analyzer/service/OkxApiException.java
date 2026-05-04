package com.okx.analyzer.service;

import lombok.Getter;

@Getter
public class OkxApiException extends RuntimeException {

    private final String requestPath;
    private final Integer statusCode;
    private final String okxCode;

    public OkxApiException(String requestPath, Integer statusCode, String okxCode, String message) {
        super(message);
        this.requestPath = requestPath;
        this.statusCode = statusCode;
        this.okxCode = okxCode;
    }

    public OkxApiException(String requestPath, Integer statusCode, String okxCode, String message, Throwable cause) {
        super(message, cause);
        this.requestPath = requestPath;
        this.statusCode = statusCode;
        this.okxCode = okxCode;
    }
}

