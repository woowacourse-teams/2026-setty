#!/usr/bin/env bash
# 배포에 필요한 것들이 EC2에 갖춰져 있는지만 확인한다. 상태를 바꾸지 않는다.
set -euo pipefail

ENV_FILE=/opt/setty/.env

if ! command -v docker >/dev/null 2>&1; then
    echo "docker가 설치되어 있지 않습니다."
    exit 1
fi

if ! docker info >/dev/null 2>&1; then
    echo "docker 데몬이 떠 있지 않습니다. systemctl start docker 후 재배포하세요."
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    echo "docker compose 플러그인이 없습니다. docker-compose-plugin을 설치하세요."
    exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
    echo "환경 변수 파일이 없습니다: $ENV_FILE"
    echo "docs/deployment.md의 항목을 채워 EC2에 직접 만들어 두세요. 레포에 커밋하지 않습니다."
    exit 1
fi

echo "사전 점검 통과"
