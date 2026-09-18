#!/usr/bin/env python3
"""
CLOUD-158 demo / acceptance harness.

Run this against HAProxy (whichever config is currently mounted) to measure:
  - KPI-1: check accuracy against request success
  - KPI-5: service error rate at declared load

Usage:
    python3 demo.py --lb http://localhost:80 --backend2 http://localhost:8082 --requests 200

Typical run:
  1. docker compose up -d                     (starts with haproxy-shallow.cfg)
  2. python3 scripts/demo.py                   -> baseline (O2): high error rate
  3. curl -X POST http://localhost:8082/fault/exhaust-pool
  4. python3 scripts/demo.py                   -> shows requests failing while LB still sends traffic there
  5. curl -X POST http://localhost:8082/fault/reset
  6. HAPROXY_CONFIG=./haproxy/haproxy-deep.cfg docker compose up -d haproxy
  7. curl -X POST http://localhost:8082/fault/exhaust-pool
  8. python3 scripts/demo.py                   -> shows LB routing around the unhealthy backend (O3)
"""
import argparse
import time
import urllib.request
import urllib.error


def hit(url, timeout=2.0):
    try:
        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status
    except urllib.error.HTTPError as e:
        return e.code
    except Exception:
        return None  # connection refused / timeout


def run(lb_url, requests, delay):
    success = 0
    failure = 0
    for i in range(requests):
        status = hit(f"{lb_url}/api/work")
        if status == 200:
            success += 1
        else:
            failure += 1
        time.sleep(delay)

    total = success + failure
    success_rate = 100.0 * success / total if total else 0.0
    error_rate = 100.0 * failure / total if total else 0.0

    print("=" * 50)
    print(f"Requests sent:        {total}")
    print(f"Successful (200):     {success}")
    print(f"Failed (503/timeout): {failure}")
    print(f"KPI-1 success rate:   {success_rate:.1f}%")
    print(f"KPI-5 error rate:     {error_rate:.1f}%")
    print("=" * 50)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--lb", default="http://localhost:80", help="HAProxy frontend URL")
    parser.add_argument("--requests", type=int, default=200)
    parser.add_argument("--delay", type=float, default=0.02)
    args = parser.parse_args()

    run(args.lb, args.requests, args.delay)
