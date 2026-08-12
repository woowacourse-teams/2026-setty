#!/usr/bin/env bash
set -euo pipefail

APP_USER=ubuntu
APP_DIR=/opt/setty/app
SERVICE_NAME=setty-backend.service
HEALTH_URL=http://127.0.0.1:8080/actuator/health
TIMEOUT_SECONDS=180
ROLLBACK_TIMEOUT_SECONDS=120
CURRENT_JAR="$APP_DIR/setty.jar"
PREVIOUS_JAR="$APP_DIR/setty.jar.previous"

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

if [[ ! -s "$PREVIOUS_JAR" ]]; then
    echo "복구할 이전 JAR이 없습니다: $PREVIOUS_JAR"
    exit 1
fi

echo "이전 JAR로 롤백 시작"
systemctl stop "$SERVICE_NAME" || true
install -o "$APP_USER" -g "$APP_USER" -m 0644 "$PREVIOUS_JAR" "$CURRENT_JAR"

if ! systemctl start "$SERVICE_NAME"; then
    echo "이전 JAR 시작도 실패했습니다."
    systemctl status "$SERVICE_NAME" --no-pager || true
    journalctl -u "$SERVICE_NAME" -n 200 --no-pager || true
    exit 1
fi

if wait_for_health "$ROLLBACK_TIMEOUT_SECONDS"; then
    echo "이전 JAR 복구 성공. 이번 배포는 실패로 기록합니다."
else
    echo "이전 JAR 복구 후에도 헬스체크 실패"
    systemctl status "$SERVICE_NAME" --no-pager || true
    journalctl -u "$SERVICE_NAME" -n 200 --no-pager || true
fi

exit 1
