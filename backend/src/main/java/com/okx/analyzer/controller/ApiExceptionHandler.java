package com.okx.analyzer.controller;

import com.okx.analyzer.service.OkxApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OkxApiException.class)
    public ResponseEntity<Map<String, Object>> handleOkxApiException(OkxApiException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("message", e.getMessage());
        if (e.getRequestPath() != null) {
            body.put("path", e.getRequestPath());
        }
        if (e.getOkxCode() != null) {
            body.put("okxCode", e.getOkxCode());
        }
        if (e.getStatusCode() != null) {
            body.put("status", e.getStatusCode());
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
        ));
    }
}

