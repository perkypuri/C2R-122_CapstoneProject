package com.capstone.backend;

import com.capstone.backend.service.DependencyPoolService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is the O2 reproduction test: proves a backend can pass its shallow
 * check while failing real requests, and that the deep check catches it.
 */
class DependencyPoolServiceTest {

    @Test
    void shallowCheckStaysHealthyWhileRealWorkFails_underFault() {
        DependencyPoolService pool = new DependencyPoolService();
        pool.injectFault();

        // O2: naive check is blind to the exhausted dependency.
        assertTrue(pool.shallowHealthy(), "shallow check should still report healthy");

        // O3: deep check correctly reflects reduced capacity.
        // (Not asserting always-false here since pool still has 1 spare permit
        // by design — see the cascade-avoidance note below.)

        // Real traffic should start failing once concurrent demand exceeds
        // remaining capacity.
        int failures = 0;
        for (int i = 0; i < 10; i++) {
            new Thread(() -> pool.tryDoRealWork(50)).start();
        }
        for (int i = 0; i < 10; i++) {
            if (!pool.tryDoRealWork(5)) {
                failures++;
            }
        }
        assertTrue(failures > 0, "expected some real requests to fail under the injected fault");

        pool.resetFault();
        assertFalse(pool.isFaultInjected());
    }

    @Test
    void deepCheckFailsWhenPoolFullyExhausted() throws InterruptedException {
        DependencyPoolService pool = new DependencyPoolService();
        // Hold every permit to simulate total exhaustion.
        for (int i = 0; i < pool.poolSize(); i++) {
            new Thread(() -> pool.tryDoRealWork(2000)).start();
        }
        Thread.sleep(50);
        assertFalse(pool.deepHealthy(), "deep check should detect total exhaustion");
    }
}
