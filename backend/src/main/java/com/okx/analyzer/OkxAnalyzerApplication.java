package com.okx.analyzer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OkxAnalyzerApplication {
    public static void main(String[] args) {
        SpringApplication.run(OkxAnalyzerApplication.class, args);
    }
}
