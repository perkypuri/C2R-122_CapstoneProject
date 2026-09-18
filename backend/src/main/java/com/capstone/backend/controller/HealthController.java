package com.capstone.backend.controller;

import com.capstone.backend.service.DependencyPoolService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final DependencyPoolService pool;

    @Value("${server.port:8080}")
    private String port;

    public HealthController(DependencyPoolService pool) {
        this.pool = pool;
    }

    /**
     * O2 reference check — this is the naive check every basic load balancer setup uses.
     * It only proves the process is listening. HAProxy's "shallow" config points here.
     */
    @GetMapping("/health/shallow")
    public ResponseEntity<Map<String, Object>> shallow() {
        boolean healthy = pool.shallowHealthy();
        return ResponseEntity.status(healthy ? 200 : 503).body(Map.of(
                "check", "shallow",
                "healthy", healthy,
                "port", port
        ));
    }

    /**
     * O3 contribution — reflects actual serving capability by checking spare
     * capacity in the dependency pool, without blocking or doing real work.
     * HAProxy's "deep" config points here.
     */
    @GetMapping("/health/deep")
    public ResponseEntity<Map<String, Object>> deep() {
        boolean healthy = pool.deepHealthy();
        return ResponseEntity.status(healthy ? 200 : 503).body(Map.of(
                "check", "deep",
                "healthy", healthy,
                "availablePermits", pool.availablePermits(),
                "poolSize", pool.poolSize(),
                "port", port
        ));
    }
}
