#!/usr/bin/env bash
# 배송비 모듈 리소스(src/main/resources/deliveryfee)를 다시 만든다. Docker와 python3가 필요하다.
# 사용: WORK=<작업 폴더> ./build_data.sh   (작업 폴더에 원본 약 400MB를 내려받는다)
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../../src/main/resources/deliveryfee"
WORK=${WORK:?작업 폴더를 WORK로 지정하세요}
OSM_URL=${OSM_URL:-https://download.geofabrik.de/asia/south-korea-latest.osm.pbf}
DONGS_URL=${DONGS_URL:-https://raw.githubusercontent.com/vuski/admdongkor/master/ver20260701/HangJeongDong_ver20260701.geojson}
OSRM_IMAGE=ghcr.io/project-osrm/osrm-backend
SEOUL_BBOX=126.76,37.41,127.19,37.72
RESIDENTIAL=apartments,residential,house,detached,semidetached_house,terrace,dormitory

mkdir -p "$WORK"
cd "$WORK"
# geofabrik의 latest 주소는 날짜 파일로 넘겨 주므로 -L이 필요하다
[ -f korea.osm.pbf ] || curl -fL -o korea.osm.pbf "$OSM_URL"
[ -f dongs.geojson ] || curl -fL -o dongs.geojson "$DONGS_URL"

docker run --rm -v "$WORK":/data -w /data debian:bookworm-slim sh -c "
  apt-get update -qq >/dev/null && apt-get install -y -qq osmium-tool >/dev/null &&
  osmium extract -b $SEOUL_BBOX korea.osm.pbf -o seoul.osm.pbf --overwrite &&
  osmium tags-filter seoul.osm.pbf w/building=$RESIDENTIAL -o residential.osm.pbf --overwrite &&
  osmium export residential.osm.pbf -f geojsonseq --geometry-types=polygon -o residential.geojsonseq --overwrite"

docker run --rm -v "$WORK":/data "$OSRM_IMAGE" osrm-extract -p /opt/car.lua /data/seoul.osm.pbf
docker run --rm -v "$WORK":/data "$OSRM_IMAGE" osrm-partition /data/seoul.osrm
docker run --rm -v "$WORK":/data "$OSRM_IMAGE" osrm-customize /data/seoul.osrm

docker rm -f deliveryfee-osrm >/dev/null 2>&1 || true
docker run -d --name deliveryfee-osrm -p 5000:5000 -v "$WORK":/data "$OSRM_IMAGE" \
  osrm-routed --algorithm mld --max-table-size 500 /data/seoul.osrm >/dev/null
trap 'docker rm -f deliveryfee-osrm >/dev/null' EXIT
until curl -fs "http://localhost:5000/nearest/v1/driving/126.97,37.56" >/dev/null; do sleep 1; done

python3 "$HERE/build_data.py" dongs.geojson residential.geojsonseq http://localhost:5000 "$OUT"
ls -l "$OUT"
