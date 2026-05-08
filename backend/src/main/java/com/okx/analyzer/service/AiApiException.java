package com.okx.analyzer.service;

import lombok.Getter;

@Getter
public class AiApiException extends RuntimeException {

    private final String provider;
    private final String requestPath;
    private final Integer statusCode;

    public AiApiException(String provider, String requestPath, Integer statusCode, String message) {
        super(message);
        this.provider = provider;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
    }

    public AiApiException(String provider, String requestPath, Integer statusCode, String message, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.requestPath = requestPath;
        this.statusCode = statusCode;
    }
}
