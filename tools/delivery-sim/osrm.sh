#!/usr/bin/env bash
# 서울 car 경로 데이터(~/osrm/seoul.osrm.*)로 로컬 OSRM을 띄운다. 이미 떠 있으면 그대로 둔다.
set -euo pipefail

DATA_DIR="${OSRM_DATA_DIR:-$HOME/osrm}"
PORT="${OSRM_PORT:-5008}"
NAME=delivery-sim-osrm
IMAGE=ghcr.io/project-osrm/osrm-backend:v26.10.0-debian  # ~/osrm/seoul.osrm.* 를 만든 버전과 같아야 한다

if [ ! -f "$DATA_DIR/seoul.osrm.mldgr" ]; then
  echo "경로 데이터가 없습니다: $DATA_DIR/seoul.osrm.* (OSRM_DATA_DIR로 위치를 바꿀 수 있습니다)" >&2
  exit 1
fi

if [ -z "$(docker ps -q -f name="^${NAME}$")" ]; then
  docker rm -f "$NAME" >/dev/null 2>&1 || true
  docker run -d --name "$NAME" --memory=400m -p "127.0.0.1:$PORT:5000" -v "$DATA_DIR:/data" \
    "$IMAGE" osrm-routed --algorithm mld /data/seoul.osrm >/dev/null
fi

for _ in $(seq 1 30); do
  if curl -fs "http://localhost:$PORT/route/v1/driving/126.933,37.496;126.939,37.498?overview=false" >/dev/null; then
    echo "OSRM 준비 완료: http://localhost:$PORT (멈출 때: docker rm -f $NAME)"
    exit 0
  fi
  sleep 1
done
echo "OSRM이 30초 안에 응답하지 않았습니다. docker logs $NAME 으로 확인하세요." >&2
exit 1
