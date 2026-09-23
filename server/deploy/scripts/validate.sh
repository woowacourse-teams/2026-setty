#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME=setty-backend.service
HEALTH_URL=http://127.0.0.1:8080/actuator/health
TIMEOUT_SECONDS=180

wait_for_health() {
    local timeout_seconds="$1"
    local deadline=$((SECONDS + timeout_seconds))

    while [[ "$SECONDS" -lt "$deadline" ]]; do
        if systemctl is-active --quiet "$SERVICE_NAME" \
            && curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null \
            | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
            return 0
        fi
        sleep 5
    done

    return 1
}

if wait_for_health "$TIMEOUT_SECONDS"; then
    echo "헬스체크 통과: $HEALTH_URL"
    exit 0
fi

echo "${TIMEOUT_SECONDS}초 안에 헬스체크를 통과하지 못했습니다."
systemctl status "$SERVICE_NAME" --no-pager || true
journalctl -u "$SERVICE_NAME" -n 200 --no-pager || true

exit 1
