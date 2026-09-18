# CLOUD-158 — Load Balancer Health Checking That Reflects Real Service Capability

A working, minimal implementation of the problem and contribution described in the
capstone spec, built so it's easy to extend for the full two-semester scope.

## What this actually demonstrates

- **The problem (O2 reference):** a backend can be "healthy" by a naive check
  (process is listening) while its real dependency — here, a simulated bounded
  connection pool — is exhausted, so real requests still fail.
- **The contribution (O3):** a *deep* health check that asks "do I have spare
  capacity right now?" without blocking or doing real work, so it can't itself
  cascade failures across backends (O4 / NT3).
- **The system (O4):** two backend instances + HAProxy, switchable between the
  naive check and the deep check via config, so you can literally watch HAProxy
  route around a capacity-exhausted backend once you flip the switch.
- **Acceptance (O5):** `scripts/demo.py` hammers the load balancer's real work
  endpoint and reports KPI-1 (check accuracy against request success) and KPI-5
  (error rate), which is exactly the comparison your KPI table asks for.

## Project layout

```
backend/            Spring Boot service (healthBacked) — the actual "load balanced" app
  src/main/.../controller/HealthController.java   /health/shallow and /health/deep
  src/main/.../controller/WorkController.java     /api/work — the "real request"
  src/main/.../controller/FaultController.java    /fault/exhaust-pool, /fault/reset
  src/main/.../service/DependencyPoolService.java the simulated dependency (bounded pool)
haproxy/
  haproxy-shallow.cfg   O2 baseline — checks /health/shallow
  haproxy-deep.cfg      O3 contribution — checks /health/deep
docker-compose.yml      2 backend instances + HAProxy
scripts/
  demo.py               load-test harness, prints KPI-1 / KPI-5
  fault_inject.sh        on/off toggle for the simulated dependency failure
.github/workflows/ci.yml  build + test on push
```

## Running it

Requires Docker and Docker Compose.

```bash
cd capstone-project

# 1. Start with the O2 baseline (naive check)
docker compose up -d --build

# 2. Baseline run — no fault yet, should be ~100% success
python3 scripts/demo.py

# 3. Inject a fault into backend2 (simulates its DB pool getting exhausted)
./scripts/fault_inject.sh backend2 on

# 4. Re-run — shallow check still says backend2 is healthy, so HAProxy keeps
#    sending it traffic, and you'll see real failures (this reproduces O2)
python3 scripts/demo.py

# 5. Reset, then switch HAProxy to the deep check (O3 contribution)
./scripts/fault_inject.sh backend2 off
HAPROXY_CONFIG=./haproxy/haproxy-deep.cfg docker compose up -d haproxy

# 6. Inject the same fault again
./scripts/fault_inject.sh backend2 on

# 7. Re-run — HAProxy's stats page (http://localhost:8404/stats) should show
#    backend2 marked DOWN, and demo.py should show a much higher success rate
python3 scripts/demo.py
```

Watch it live at **http://localhost:8404/stats** while you run steps 3–7 —
that's the clearest way to *see* the difference between the two checks.

## Running the backend alone (no Docker)

```bash
cd backend
mvn spring-boot:run
# in another terminal:
curl http://localhost:8081/health/shallow
curl http://localhost:8081/health/deep
curl http://localhost:8081/api/work
```

## Where the spec's other requirements map to right now

| Spec item | Status here | What's left for the full submission |
|---|---|---|
| O1 (basic health-checked LB) | Done — `haproxy-shallow.cfg` + 2 backends | Add real telemetry export |
| O2 (reproducible reference) | Done — `DependencyPoolServiceTest`, `demo.py` step 2-4 | Run with a real DB pool instead of a `Semaphore`, gather baseline over multiple trials |
| O3 (differentiating contribution) | Done — `/health/deep` | Add ablation: vary pool size, timeout, concurrency; report sensitivity |
| O4 (integrated, cascade-safe system) | Done — deep check is non-blocking, side-effect-free | Add degraded-mode telemetry, structured logs |
| O5 (independent acceptance, NT1-NT5) | Partially — `fault_inject.sh` covers NT1/NT2/NT3 | Add NT4 (control-plane outage) and NT5 (config drift) fixtures; get an independent reviewer to sign off |
| KPI-1, KPI-5 | Measured by `demo.py` | Run ≥30 trials per condition, report p50/p95/p99, uncertainty |
| D1-D7 deliverables | Code + README only | Architecture decision record, risk register, evidence manifest, final report/video |

## Swapping the simulated pool for a real dependency

Right now `DependencyPoolService` uses a `Semaphore` to stand in for "a limited
resource a real request needs." For a stronger CP2 submission, swap it for an
actual bounded resource — e.g. a HikariCP pool against a real (or testcontainer)
Postgres instance — and keep the same shallow-vs-deep contract:

- shallow: `SELECT 1` or just "am I running"
- deep: check `HikariPoolMXBean.getActiveConnections()` / idle count without
  taking a connection out of rotation
