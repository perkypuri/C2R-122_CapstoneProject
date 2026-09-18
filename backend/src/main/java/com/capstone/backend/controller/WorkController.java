package com.capstone.backend.controller;

import com.capstone.backend.service.DependencyPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Represents real client traffic. This is what AC-1 ("check accuracy against
 * actual request success") is measured against — a check is only useful if
 * it predicts whether THIS endpoint will succeed.
 */
@RestController
public class WorkController {

    private final DependencyPoolService pool;

    @Value("${server.port:8080}")
    private String port;

    public WorkController(DependencyPoolService pool) {
        this.pool = pool;
    }

    @GetMapping("/api/work")
    public ResponseEntity<Map<String, Object>> doWork() {
        boolean success = pool.tryDoRealWork(50); // short timeout — a real request won't wait forever
        return ResponseEntity.status(success ? 200 : 503).body(Map.of(
                "success", success,
                "port", port
        ));
    }
}
