#!/usr/bin/env bash
# 앱 컨테이너만 교체한다. MySQL 컨테이너와 db-data 볼륨은 건드리지 않는다.
set -euo pipefail

APP_DIR=/opt/setty/app
COMPOSE_FILE="$APP_DIR/docker-compose.prod.yml"
DB_CONTAINER=setty-mysql
APP_CONTAINER=setty-app
DB_WAIT_SECONDS=120

cd "$APP_DIR"

echo "이미지 빌드"
docker build -t setty-app:latest -f Dockerfile .

echo "MySQL 확인"
# 이미 떠 있으면 compose가 그대로 두고, 없을 때만 새로 만든다.
docker compose -f "$COMPOSE_FILE" up -d db

echo "MySQL 헬스 대기 (최대 ${DB_WAIT_SECONDS}초)"
deadline=$((SECONDS + DB_WAIT_SECONDS))
until [ "$(docker inspect -f '{{.State.Health.Status}}' "$DB_CONTAINER" 2>/dev/null || echo starting)" = "healthy" ]; do
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "MySQL이 준비되지 않았습니다."
        docker logs --tail 50 "$DB_CONTAINER" || true
        exit 1
    fi
    sleep 3
done

# 첫 배포에서 ApplicationStop이 돌지 않으므로, compose 밖에서 뜬 동명 컨테이너를 여기서 정리한다.
if docker ps -aq --filter "name=^/${APP_CONTAINER}$" | grep -q .; then
    echo "기존 앱 컨테이너 정리"
    docker rm -f "$APP_CONTAINER"
fi

echo "앱 컨테이너 기동"
# 이미지 태그가 latest로 같아 compose가 변경을 감지하지 못하므로 강제로 재생성한다.
docker compose -f "$COMPOSE_FILE" up -d --no-deps --force-recreate app

echo "미사용 이미지 정리"
docker image prune -f >/dev/null 2>&1 || true

echo "기동 명령 완료"
