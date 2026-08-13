#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME=setty-backend.service

if systemctl cat "$SERVICE_NAME" >/dev/null 2>&1; then
    systemctl stop "$SERVICE_NAME"
fi

echo "서비스 중지 완료: $SERVICE_NAME"
