#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME=setty-backend.service

systemctl reset-failed "$SERVICE_NAME" || true
if ! systemctl restart "$SERVICE_NAME"; then
    systemctl status "$SERVICE_NAME" --no-pager || true
    journalctl -u "$SERVICE_NAME" -n 100 --no-pager || true
    exit 1
fi

echo "systemd 시작 명령 완료: $SERVICE_NAME"
