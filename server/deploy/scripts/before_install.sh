#!/usr/bin/env bash
set -euo pipefail

APP_USER=setty
BASE_DIR=/opt/setty
APP_DIR="$BASE_DIR/app"
ENV_FILE="$BASE_DIR/setty.env"
MIN_FREE_BYTES=1073741824

require_command() {
    local command_name="$1"

    if ! command -v "$command_name" >/dev/null 2>&1; then
        echo "필수 명령이 없습니다: $command_name"
        exit 1
    fi
}

for command_name in awk chmod chown df getent grep groupadd id install nologin useradd yum; do
    require_command "$command_name"
done

if ! getent group "$APP_USER" >/dev/null; then
    groupadd --system "$APP_USER"
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
    useradd \
        --system \
        --gid "$APP_USER" \
        --no-create-home \
        --home-dir /nonexistent \
        --shell "$(command -v nologin)" \
        "$APP_USER"
fi

if [[ "$(id -gn "$APP_USER")" != "$APP_USER" ]]; then
    echo "서비스 사용자의 기본 그룹이 올바르지 않습니다: $APP_USER"
    exit 1
fi

APP_SHELL="$(getent passwd "$APP_USER" | awk -F: '{ print $7 }')"
if [[ "$APP_SHELL" != */nologin ]]; then
    echo "서비스 사용자는 비로그인 셸을 사용해야 합니다: $APP_USER"
    exit 1
fi

if [[ " $(id -nG "$APP_USER") " == *" wheel "* ]]; then
    echo "서비스 사용자는 관리자 그룹에 속할 수 없습니다: $APP_USER"
    exit 1
fi

if ! command -v java >/dev/null 2>&1; then
    yum install -y java-21-amazon-corretto-headless
fi

if ! command -v curl >/dev/null 2>&1; then
    yum install -y curl
fi

JAVA_VERSION="$(java -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ { print $2; exit }')"
if [[ "$JAVA_VERSION" != "21" ]]; then
    echo "Java 21이 필요합니다. 현재 java.specification.version=$JAVA_VERSION"
    exit 1
fi

if [[ ! -s "$ENV_FILE" ]]; then
    echo "환경 변수 파일이 없거나 비어 있습니다: $ENV_FILE"
    echo "server/deploy/setty.env.example을 참고해 EC2에 직접 생성하세요."
    exit 1
fi

has_non_blank_value() {
    local variable_name="$1"
    local line
    local value

    line="$(grep -E "^${variable_name}=" "$ENV_FILE" | tail -n 1 || true)"
    if [[ -z "$line" ]]; then
        return 1
    fi

    value="${line#*=}"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"

    if [[ "${#value}" -ge 2 ]] \
        && { [[ "$value" == \"*\" ]] || [[ "$value" == \'*\' ]]; }; then
        value="${value:1:${#value}-2}"
        value="${value#"${value%%[![:space:]]*}"}"
        value="${value%"${value##*[![:space:]]}"}"
    fi

    [[ -n "$value" ]]
}

for variable_name in \
    SETTY_OPERATOR_SECRET \
    SETTY_FRONT_BASE_URL \
    SPRING_DATASOURCE_URL \
    SPRING_DATASOURCE_USERNAME \
    SPRING_DATASOURCE_PASSWORD; do
    if ! has_non_blank_value "$variable_name"; then
        echo "필수 환경 변수가 없거나 비어 있습니다: $variable_name"
        exit 1
    fi
done

AVAILABLE_BYTES="$(df -B1 --output=avail /opt | awk 'NR == 2 {print $1}')"
if [[ ! "$AVAILABLE_BYTES" =~ ^[0-9]+$ ]] || (( AVAILABLE_BYTES < MIN_FREE_BYTES )); then
    echo "디스크 여유 공간이 부족합니다. /opt에 최소 1 GiB가 필요합니다."
    df -h /opt
    exit 1
fi

install -d -o root -g root -m 0755 "$APP_DIR"
chown root:root "$ENV_FILE"
chmod 0600 "$ENV_FILE"

echo "사전 점검 통과: Java $JAVA_VERSION, 사용자 $APP_USER, 환경 파일 $ENV_FILE"
