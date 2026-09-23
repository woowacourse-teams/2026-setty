#!/usr/bin/env bash
set -euo pipefail

APP_JAR=/opt/setty/app/app.jar
ENV_FILE=/opt/setty/setty.env
SERVICE_NAME=setty-backend.service

if [[ ! -s "$APP_JAR" ]]; then
    echo "배포된 JAR이 없습니다: $APP_JAR"
    exit 1
fi

chown root:root "$APP_JAR"
chmod 0644 "$APP_JAR"
chown root:root "$ENV_FILE"
chmod 0600 "$ENV_FILE"

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"

echo "설치 후 설정 완료: $SERVICE_NAME"
