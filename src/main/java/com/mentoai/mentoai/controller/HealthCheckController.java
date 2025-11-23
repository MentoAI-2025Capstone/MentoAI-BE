package com.mentoai.mentoai.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Render health checks ping this endpoint periodically.
 */
@RestController
public class HealthCheckController {

    @GetMapping("/healthz")
    public Map<String, Object> healthCheck() {
        return Map.of(
                "status", "UP",
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}

