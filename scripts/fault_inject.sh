#!/usr/bin/env bash
# Usage: ./fault_inject.sh backend1|backend2 on|off
set -euo pipefail

TARGET="${1:?usage: fault_inject.sh <backend1|backend2> <on|off>}"
ACTION="${2:?usage: fault_inject.sh <backend1|backend2> <on|off>}"

case "$TARGET" in
  backend1) PORT=8081 ;;
  backend2) PORT=8082 ;;
  *) echo "unknown target: $TARGET"; exit 1 ;;
esac

case "$ACTION" in
  on)  PATH_="/fault/exhaust-pool" ;;
  off) PATH_="/fault/reset" ;;
  *) echo "unknown action: $ACTION"; exit 1 ;;
esac

curl -s -X POST "http://localhost:${PORT}${PATH_}" | tee /dev/stderr
echo
