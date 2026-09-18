package com.capstone.backend.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates the thing a real backend actually depends on to serve a request —
 * e.g. a DB connection pool. This is intentionally the "hidden" resource:
 * a process can be alive and listening (shallow check = healthy) while this
 * pool is exhausted, so every real request still fails.
 *
 * O2 reference behaviour: shallow check only asks "is the process listening?"
 * O3 contribution: deep check asks "is there spare capacity in this pool right now?"
 *
 * The deep check uses a non-blocking tryAcquire with an IMMEDIATE timeout and
 * releases instantly if it succeeds — it never queues behind real traffic and
 * never does real work itself, which is what keeps it from cascading failures
 * across backends (O4 / NT3).
 */
@Service
public class DependencyPoolService {

    private static final int POOL_SIZE = 5;
    // How much of the pool a fault injection permanently pins down.
    private static final int FAULT_PERMITS_HELD = 4;

    private final Semaphore pool = new Semaphore(POOL_SIZE, true);
    private final AtomicBoolean faultInjected = new AtomicBoolean(false);
    private final AtomicInteger faultPermitsAcquired = new AtomicInteger(0);

    /** A real unit of work: acquire a connection, hold it briefly, release it. */
    public boolean tryDoRealWork(long acquireTimeoutMillis) {
        boolean acquired = false;
        try {
            acquired = pool.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                return false;
            }
            // Simulate the query/work that actually needs the dependency.
            Thread.sleep(15);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (acquired) {
                pool.release();
            }
        }
    }

    /** Shallow check: process is up and can answer HTTP. Always true if we got this far. */
    public boolean shallowHealthy() {
        return true;
    }

    /** Deep check: is there at least one spare permit right now, without blocking or doing work? */
    public boolean deepHealthy() {
        boolean gotOne = pool.tryAcquire();
        if (gotOne) {
            pool.release();
        }
        return gotOne;
    }

    public int availablePermits() {
        return pool.availablePermits();
    }

    public int poolSize() {
        return POOL_SIZE;
    }

    public boolean isFaultInjected() {
        return faultInjected.get();
    }

    /** Pins most of the pool down to simulate an exhausted dependency (e.g. DB under load). */
    public synchronized void injectFault() {
        if (faultInjected.compareAndSet(false, true)) {
            int acquired = 0;
            for (int i = 0; i < FAULT_PERMITS_HELD; i++) {
                if (pool.tryAcquire()) {
                    acquired++;
                }
            }
            faultPermitsAcquired.set(acquired);
        }
    }

    /** Recovery path (NT1–NT4): release the pinned permits and reconcile state. */
    public synchronized void resetFault() {
        if (faultInjected.compareAndSet(true, false)) {
            int held = faultPermitsAcquired.getAndSet(0);
            pool.release(held);
        }
    }
}
