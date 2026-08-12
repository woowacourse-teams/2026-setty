#!/usr/bin/env bash
set -euo pipefail

APP_USER=ubuntu
LEGACY_USERS=(ubuntu ssm-user)
APP_DIR=/opt/setty/app
SOURCE_DIR=/opt/setty/source
SERVICE_NAME=setty-backend.service
UNIT_SOURCE="$SOURCE_DIR/deploy/setty-backend.service"
UNIT_DEST="/etc/systemd/system/$SERVICE_NAME"
CURRENT_JAR="$APP_DIR/setty.jar"
NEXT_JAR="$APP_DIR/setty.jar.next"
PREVIOUS_JAR="$APP_DIR/setty.jar.previous"

if [[ ! -s "$NEXT_JAR" ]]; then
    echo "새 JAR이 없습니다: $NEXT_JAR"
    exit 1
fi

if [[ ! -f "$UNIT_SOURCE" ]]; then
    echo "systemd 서비스 파일이 없습니다: $UNIT_SOURCE"
    exit 1
fi

preserve_legacy_jar() {
    local pid="$1"
    local -a args=()
    local jar_path=""
    local index

    mapfile -d '' -t args < "/proc/$pid/cmdline" || true
    for ((index = 0; index < ${#args[@]} - 1; index++)); do
        if [[ "${args[index]}" == "-jar" ]]; then
            jar_path="${args[index + 1]}"
            break
        fi
    done

    if [[ -z "$jar_path" ]]; then
        return
    fi

    if [[ "$jar_path" != /* ]]; then
        jar_path="$(readlink -f "/proc/$pid/cwd")/$jar_path"
    fi

    if [[ -s "$jar_path" ]]; then
        install -o "$APP_USER" -g "$APP_USER" -m 0644 "$jar_path" "$PREVIOUS_JAR"
        echo "기존 nohup JAR 보존: $jar_path -> $PREVIOUS_JAR"
    fi
}

stop_legacy_processes() {
    local -a pids=()
    local legacy_user
    local deadline
    local pid
    local running

    for legacy_user in "${LEGACY_USERS[@]}"; do
        if id "$legacy_user" >/dev/null 2>&1; then
            while IFS= read -r pid; do
                [[ -n "$pid" ]] && pids+=("$pid")
            done < <(
                pgrep -u "$legacy_user" \
                    -f 'java .*setty[.]jar|java .*server-[^ ]*[.]jar' || true
            )
        fi
    done

    if [[ "${#pids[@]}" -eq 0 ]]; then
        return
    fi

    if [[ ! -s "$CURRENT_JAR" && ! -s "$PREVIOUS_JAR" ]]; then
        preserve_legacy_jar "${pids[0]}"
        if [[ ! -s "$PREVIOUS_JAR" ]]; then
            echo "기존 프로세스의 JAR을 보존하지 못했습니다. 기존 앱을 유지하고 배포를 중단합니다."
            return 1
        fi
    fi

    echo "기존 nohup Spring Boot 종료: ${pids[*]}"
    kill -TERM "${pids[@]}" 2>/dev/null || true

    deadline=$((SECONDS + 30))
    while [[ "$SECONDS" -lt "$deadline" ]]; do
        running=0
        for pid in "${pids[@]}"; do
            if kill -0 "$pid" 2>/dev/null; then
                running=1
                break
            fi
        done
        if [[ "$running" -eq 0 ]]; then
            return
        fi
        sleep 1
    done

    for pid in "${pids[@]}"; do
        if kill -0 "$pid" 2>/dev/null; then
            echo "정상 종료 제한 시간을 초과해 강제 종료: $pid"
            kill -KILL "$pid"
        fi
    done
}

rollback_start_failure() {
    if [[ ! -s "$PREVIOUS_JAR" ]]; then
        echo "복구할 이전 JAR이 없습니다."
        return
    fi

    echo "시작 실패. 이전 JAR로 복구"
    install -o "$APP_USER" -g "$APP_USER" -m 0644 "$PREVIOUS_JAR" "$CURRENT_JAR"
    systemctl start "$SERVICE_NAME" || true
}

install -o root -g root -m 0644 "$UNIT_SOURCE" "$UNIT_DEST"
systemctl daemon-reload

if [[ -s "$CURRENT_JAR" ]]; then
    install -o "$APP_USER" -g "$APP_USER" -m 0644 "$CURRENT_JAR" "$PREVIOUS_JAR"
fi

if systemctl cat "$SERVICE_NAME" >/dev/null 2>&1; then
    systemctl stop "$SERVICE_NAME" || true
fi

stop_legacy_processes

chown "$APP_USER:$APP_USER" "$NEXT_JAR"
chmod 0644 "$NEXT_JAR"
mv -f "$NEXT_JAR" "$CURRENT_JAR"

systemctl enable "$SERVICE_NAME"
if ! systemctl start "$SERVICE_NAME"; then
    systemctl status "$SERVICE_NAME" --no-pager || true
    journalctl -u "$SERVICE_NAME" -n 100 --no-pager || true
    rollback_start_failure
    exit 1
fi

echo "systemd 시작 명령 완료: $SERVICE_NAME"
