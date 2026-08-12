#!/usr/bin/env bash
set -euo pipefail

APP_USER=ubuntu
BASE_DIR=/opt/setty
APP_DIR="$BASE_DIR/app"
SOURCE_DIR="$BASE_DIR/source"
ENV_FILE="$BASE_DIR/setty.env"
MIN_FREE_BYTES=2147483648

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "필수 명령이 없습니다: $command_name"
        exit 1
    fi
}

if ! id "$APP_USER" >/dev/null 2>&1; then
    echo "서비스 실행 사용자가 없습니다: $APP_USER"
    exit 1
fi

for command_name in awk chmod chown curl cut df env find getent grep id install java javac journalctl mv pgrep readlink rm runuser sleep systemctl; do
    require_command "$command_name"
done

if ! getent group "$APP_USER" >/dev/null; then
    echo "서비스 실행 그룹이 없습니다: $APP_USER"
    exit 1
fi

JAVA_VERSION="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ { print $2; exit }')"
if [[ "$JAVA_VERSION" != "21" ]]; then
    echo "Java 21이 필요합니다. 현재 java.specification.version=$JAVA_VERSION"
    exit 1
fi

if [[ ! -s "$ENV_FILE" ]]; then
    echo "환경 변수 파일이 없거나 비어 있습니다: $ENV_FILE"
    echo "deploy/setty.env.example을 참고해 EC2에 직접 생성하세요."
    exit 1
fi

AVAILABLE_BYTES="$(df -B1 --output=avail /opt | awk 'NR == 2 {print $1}')"
if [[ ! "$AVAILABLE_BYTES" =~ ^[0-9]+$ ]] || (( AVAILABLE_BYTES < MIN_FREE_BYTES )); then
    echo "디스크 여유 공간이 부족합니다. /opt에 최소 2 GiB가 필요합니다."
    df -h /opt
    exit 1
fi

if [[ "$SOURCE_DIR" != "/opt/setty/source" ]]; then
    echo "소스 정리 경로가 허용된 값이 아닙니다: $SOURCE_DIR"
    exit 1
fi

if [[ -L "$SOURCE_DIR" ]]; then
    echo "소스 경로가 심볼릭 링크입니다. 정리를 중단합니다: $SOURCE_DIR"
    exit 1
fi

install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$APP_DIR"
install -d -o "$APP_USER" -g "$APP_USER" -m 0755 "$SOURCE_DIR"
chown root:root "$ENV_FILE"
chmod 0600 "$ENV_FILE"

find "$SOURCE_DIR" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
echo "이전 배포 소스 정리 완료: $SOURCE_DIR"
echo "사전 점검 통과: Java $JAVA_VERSION, 사용자 $APP_USER, 환경 파일 $ENV_FILE"
