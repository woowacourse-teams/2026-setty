#!/usr/bin/env bash
# 컨테이너가 떴는지가 아니라 스프링이 실제로 요청을 받을 수 있는지를 확인한다.
# actuator health는 DataSource 상태를 포함하므로 DB가 끊긴 배포는 여기서 실패한다.
set -euo pipefail

HEALTH_URL=http://localhost:8080/actuator/health
APP_CONTAINER=setty-app
TIMEOUT_SECONDS=180

deadline=$((SECONDS + TIMEOUT_SECONDS))
while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null | grep -q '"status":"UP"'; then
        echo "헬스체크 통과"
        exit 0
    fi

    if [ "$(docker inspect -f '{{.State.Running}}' "$APP_CONTAINER" 2>/dev/null || echo false)" != "true" ]; then
        echo "앱 컨테이너가 떠 있지 않습니다."
        docker logs --tail 100 "$APP_CONTAINER" || true
        exit 1
    fi

    sleep 5
done

echo "${TIMEOUT_SECONDS}초 안에 헬스체크를 통과하지 못했습니다."
docker logs --tail 100 "$APP_CONTAINER" || true
exit 1
