package com.capstone.backend.controller;

import com.capstone.backend.service.DependencyPoolService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Fault-injection surface used for CP1's O2 reproduction and CP2's NT1-NT4
 * negative-test / recovery campaign. Not part of the production interface —
 * only used by the demo/load-test harness.
 */
@RestController
public class FaultController {

    private final DependencyPoolService pool;

    public FaultController(DependencyPoolService pool) {
        this.pool = pool;
    }

    @PostMapping("/fault/exhaust-pool")
    public ResponseEntity<Map<String, Object>> exhaustPool() {
        pool.injectFault();
        return ResponseEntity.ok(Map.of(
                "faultInjected", pool.isFaultInjected(),
                "availablePermits", pool.availablePermits()
        ));
    }

    @PostMapping("/fault/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        pool.resetFault();
        return ResponseEntity.ok(Map.of(
                "faultInjected", pool.isFaultInjected(),
                "availablePermits", pool.availablePermits()
        ));
    }
}
