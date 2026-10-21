#!/usr/bin/env bash
set -Eeuo pipefail

log() {
    printf '[setty-deploy] %s\n' "$*" >&2
}

die() {
    log "$*"
    return 1
}

load_config() {
    local config_file=${SETTY_DEPLOY_CONFIG:-/opt/setty/deploy.env}
    [[ -f "$config_file" ]] || die "Missing deployment config: $config_file"
    # root가 관리하는 별도 배포 설정이다. 애플리케이션의 systemd EnvironmentFile은 source하지 않는다.
    # shellcheck source=/dev/null
    source "$config_file"
    [[ ${SETTY_DEPLOY_ENVIRONMENT:-} == dev || ${SETTY_DEPLOY_ENVIRONMENT:-} == prod ]] \
        || die 'SETTY_DEPLOY_ENVIRONMENT must be dev or prod.'

    SETTY_DEPLOY_ROOT=${SETTY_DEPLOY_ROOT:-/opt/setty}
    SETTY_NGINX_UPSTREAM=${SETTY_NGINX_UPSTREAM:-/etc/nginx/setty/active-upstream.conf}
    SETTY_NGINX_ACCESS_LOG=${SETTY_NGINX_ACCESS_LOG:-/var/log/nginx/setty-access.log}
    SETTY_PROXY_HEALTH_URL=${SETTY_PROXY_HEALTH_URL:-http://127.0.0.1/actuator/health}
    [[ -n ${SETTY_SMOKE_URL:-} ]] || die 'Set the smoke URL for this environment.'
    SETTY_HEALTH_TIMEOUT_SECONDS=${SETTY_HEALTH_TIMEOUT_SECONDS:-180}
    SETTY_ROUTE_TIMEOUT_SECONDS=${SETTY_ROUTE_TIMEOUT_SECONDS:-30}
    SETTY_OBSERVE_SECONDS=${SETTY_OBSERVE_SECONDS:-60}
    SETTY_DRAIN_TIMEOUT_SECONDS=${SETTY_DRAIN_TIMEOUT_SECONDS:-180}
    SETTY_POLL_SECONDS=${SETTY_POLL_SECONDS:-2}

    [[ ${DEPLOYMENT_ID:-} =~ ^[A-Za-z0-9_-]+$ ]] || die 'DEPLOYMENT_ID is required.'
    [[ -n ${DEPLOYMENT_GROUP_NAME:-} ]] || die 'DEPLOYMENT_GROUP_NAME is required.'
    [[ -n ${APPLICATION_NAME:-} ]] || die 'APPLICATION_NAME is required.'
    local name value
    for name in SETTY_HEALTH_TIMEOUT_SECONDS SETTY_ROUTE_TIMEOUT_SECONDS \
        SETTY_OBSERVE_SECONDS SETTY_DRAIN_TIMEOUT_SECONDS SETTY_POLL_SECONDS; do
        value=${!name}
        [[ $value =~ ^(0|[1-9][0-9]{0,3})$ ]] || die "Invalid duration: $name"
    done
    (( SETTY_HEALTH_TIMEOUT_SECONDS > 0 && SETTY_ROUTE_TIMEOUT_SECONDS > 0 \
        && SETTY_DRAIN_TIMEOUT_SECONDS > 0 && SETTY_POLL_SECONDS > 0 )) \
        || die 'Timeouts and polling interval must be positive.'
    (( SETTY_POLL_SECONDS <= 10 && SETTY_POLL_SECONDS <= SETTY_HEALTH_TIMEOUT_SECONDS \
        && SETTY_POLL_SECONDS <= SETTY_ROUTE_TIMEOUT_SECONDS \
        && SETTY_POLL_SECONDS <= SETTY_DRAIN_TIMEOUT_SECONDS )) \
        || die 'Polling interval must fit the configured timeouts and be at most 10 seconds.'
    # 기동, 복구 시 재기동, 전환·복구, 양쪽의 연결 정리와 명령 실행 여유를 포함한다.
    (( 2 * SETTY_HEALTH_TIMEOUT_SECONDS + 3 * SETTY_ROUTE_TIMEOUT_SECONDS \
        + SETTY_OBSERVE_SECONDS + 2 * SETTY_DRAIN_TIMEOUT_SECONDS \
        + 180 <= 1200 )) || die 'Configured waits exceed the 1200-second hook budget.'
    [[ "$SETTY_DEPLOY_ROOT" == /* && "$SETTY_NGINX_UPSTREAM" == /* \
        && "$SETTY_NGINX_ACCESS_LOG" == /* ]] || die 'Deployment paths must be absolute.'
    [[ "$SETTY_PROXY_HEALTH_URL" == http://127.0.0.1/* \
        && "$SETTY_PROXY_HEALTH_URL" != *\?* ]] || die 'Proxy health URL must use localhost without a query.'
    [[ ( "$SETTY_SMOKE_URL" == https://* || "$SETTY_SMOKE_URL" == http://* ) \
        && "$SETTY_SMOKE_URL" != *\#* ]] \
        || die 'An HTTP smoke URL is required.'

    local script_dir
    script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
    BUNDLE_ROOT=$(cd -- "$script_dir/../../.." && pwd)
    APP_JAR="$BUNDLE_ROOT/app.jar"
    STATE_ROOT="$SETTY_DEPLOY_ROOT/deployments"
    STATE_DIR="$STATE_ROOT/$DEPLOYMENT_ID"
    PENDING_FILE="$STATE_ROOT/pending"
}

service_for() {
    printf 'setty-backend@%s.service\n' "$1"
}

port_for() {
    case "$1" in
        blue) printf '8080\n' ;;
        green) printf '8081\n' ;;
        *) die "Unknown slot: $1" ;;
    esac
}

upstream_slot() {
    local address
    # 주석을 제외한 server 지시자가 정확히 하나인 기존 upstream만 관리한다.
    address=$(awk '
        { sub(/#.*/, ""); line=$0
          while (match(line, /server[[:space:]]+[^;]+;/)) {
            directive=substr(line, RSTART, RLENGTH)
            split(directive, fields, /[[:space:]]+/)
            sub(/;$/, "", fields[2]); print fields[2]
            line=substr(line, RSTART+RLENGTH)
          }
        }' "$SETTY_NGINX_UPSTREAM") || return 1
    case "$address" in
        127.0.0.1:8080) printf 'blue\n' ;;
        127.0.0.1:8081) printf 'green\n' ;;
        *) die 'Upstream must contain exactly one localhost server on 8080 or 8081.' ;;
    esac
}

slot_healthy() {
    local slot=$1 port
    port=$(port_for "$slot")
    systemctl is-active --quiet "$(service_for "$slot")" \
        && curl --noproxy '*' -fsS --connect-timeout 2 --max-time 5 \
            "http://127.0.0.1:$port/actuator/health" 2>/dev/null \
            | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
}

wait_for_slot() {
    local slot=$1 deadline=$((SECONDS + SETTY_HEALTH_TIMEOUT_SECONDS))
    until slot_healthy "$slot"; do
        (( SECONDS < deadline )) || return 1
        sleep "$SETTY_POLL_SECONDS"
    done
}

probe_route() {
    local slot=$1 probe
    probe="${DEPLOYMENT_ID}-${slot}-${BASHPID}-${RANDOM}"
    curl --noproxy '*' -fsS --connect-timeout 2 --max-time 5 \
        -H 'Connection: close' "${SETTY_PROXY_HEALTH_URL}?setty_deploy_probe=$probe" \
        2>/dev/null | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' || return 1
    # reload는 비동기다. 설정 파일만 확인하지 않고 이번 요청의 실제 upstream을 확인한다.
    logged_route "$slot" "$probe"
}

logged_route() {
    local slot=$1 probe=$2 port
    port=$(port_for "$slot")
    tail -n 500 "$SETTY_NGINX_ACCESS_LOG" \
        | grep -F "deploy_probe=$probe " \
        | grep -F "upstream=127.0.0.1:$port " >/dev/null
}

wait_for_route() {
    local slot=$1 deadline=$((SECONDS + SETTY_ROUTE_TIMEOUT_SECONDS))
    until [[ $(upstream_slot) == "$slot" ]] && slot_healthy "$slot" && probe_route "$slot"; do
        (( SECONDS < deadline )) || return 1
        sleep "$SETTY_POLL_SECONDS"
    done
}

smoke_check() {
    local slot=${1:-$(upstream_slot)} separator='?' probe
    [[ "$SETTY_SMOKE_URL" == *\?* ]] && separator='&'
    probe="${DEPLOYMENT_ID}-external-${slot}-${BASHPID}-${RANDOM}"
    curl -fsS --connect-timeout 3 --max-time 10 --output /dev/null \
        "${SETTY_SMOKE_URL}${separator}setty_deploy_probe=$probe" || return 1
    logged_route "$slot" "$probe"
}

wait_for_connections() {
    local slot=$1 port connections empty=0
    local deadline=$((SECONDS + SETTY_DRAIN_TIMEOUT_SECONDS))
    port=$(port_for "$slot")
    while (( SECONDS <= deadline )); do
        connections=$(ss -Hnt state established "( sport = :$port )") || return 1
        if [[ -z "$connections" ]]; then
            empty=$((empty + 1))
            (( empty >= 2 )) && return 0
        else
            empty=0
        fi
        sleep "$SETTY_POLL_SECONDS"
    done
    log "Connections did not drain: $slot ($port)."
    return 1
}

preflight() {
    local command_name slot profile current inactive working_directory executable slot_port
    for command_name in nginx systemctl curl ss flock awk grep sed tail \
        install mktemp cp mv chmod sha256sum date sleep rm touch; do
        command -v "$command_name" >/dev/null || die "Missing command: $command_name"
    done
    [[ -s "$APP_JAR" ]] || die "Missing revision JAR: $APP_JAR"
    [[ -s "$SETTY_DEPLOY_ROOT/setty.env" ]] || die 'Missing shared application environment.'
    [[ -f "$SETTY_NGINX_UPSTREAM" && ! -L "$SETTY_NGINX_UPSTREAM" ]] \
        || die 'A regular upstream configuration file is required.'
    [[ -f "$SETTY_NGINX_ACCESS_LOG" ]] || die 'Nginx upstream access log is required.'
    ! systemctl is-active --quiet setty-backend.service || die 'Migrate the legacy service first.'
    ! systemctl is-enabled --quiet setty-backend.service || die 'Disable legacy autostart first.'
    systemctl is-active --quiet nginx.service || die 'Nginx must already be running.'
    systemctl is-enabled --quiet nginx.service || die 'Enable Nginx autostart first.'
    nginx -t || die 'Existing Nginx configuration is invalid.'
    for slot in blue green; do
        [[ $(systemctl show "$(service_for "$slot")" --property=LoadState --value) == loaded ]] \
            || die "Missing slot service: $slot"
        working_directory=$(systemctl show "$(service_for "$slot")" --property=WorkingDirectory --value)
        executable=$(systemctl show "$(service_for "$slot")" --property=ExecStart --value)
        [[ "$working_directory" == "$SETTY_DEPLOY_ROOT/slots/$slot" \
            && "$executable" == *" -jar $SETTY_DEPLOY_ROOT/slots/$slot/app.jar "* ]] \
            || die "Slot service must execute its own app.jar: $slot"
        [[ -d "$SETTY_DEPLOY_ROOT/slots/$slot" \
            && -s "$SETTY_DEPLOY_ROOT/slots/$slot/slot.env" ]] || die "Missing slot directory/environment: $slot"
        profile=$(awk -F= '/^[[:space:]]*SPRING_PROFILES_ACTIVE[[:space:]]*=/ {
            value=substr($0,index($0,"=")+1); gsub(/[[:space:]"\047]/,"",value); print value
        }' "$SETTY_DEPLOY_ROOT/slots/$slot/slot.env" | tail -n 1)
        [[ "$profile" == "$SETTY_DEPLOY_ENVIRONMENT" ]] \
            || die "Expected $SETTY_DEPLOY_ENVIRONMENT profile in $slot/slot.env."
        slot_port=$(awk -F= '/^[[:space:]]*SERVER_PORT[[:space:]]*=/ {
            value=substr($0,index($0,"=")+1); gsub(/[[:space:]"\047]/,"",value); print value
        }' "$SETTY_DEPLOY_ROOT/slots/$slot/slot.env" | tail -n 1)
        [[ "$slot_port" == "$(port_for "$slot")" ]] || die "Unexpected SERVER_PORT for $slot."
    done
    current=$(upstream_slot)
    inactive=green
    [[ "$current" == green ]] && inactive=blue
    slot_healthy "$current" || die 'Current slot must be healthy before deployment.'
    systemctl is-enabled --quiet "$(service_for "$current")" || die 'Current slot must have autostart enabled.'
    case $(systemctl show "$(service_for "$inactive")" --property=ActiveState --value) in
        inactive|failed) ;;
        *) die 'Inactive slot is still running; finish its observation/draining first.' ;;
    esac
    ! systemctl is-enabled --quiet "$(service_for "$inactive")" || die 'Inactive slot must have autostart disabled.'
    [[ -z $(ss -Hltn "( sport = :$(port_for "$inactive") )") ]] \
        || die 'Inactive port is occupied by another process.'
    wait_for_route "$current" || die 'Configured and live Nginx routes do not agree.'
}
