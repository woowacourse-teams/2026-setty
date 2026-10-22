#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=server/deploy/blue-green/common.sh
source "$SCRIPT_DIR/common.sh"
load_config

install -d -m 0700 "$STATE_ROOT"
exec 9>"$STATE_ROOT/deploy.lock"
flock -n 9 || die 'Another slot deployment is running.'
[[ ! -e "$PENDING_FILE" ]] || die "Unfinished deployment: inspect $PENDING_FILE before retrying."

# 동일 배포 ID의 훅 재실행은 이미 적용한 리비전을 다시 덮어쓰지 않는다.
if [[ -f "$STATE_DIR/completed" ]]; then
    previous_new=$(awk -F= '$1=="new_slot" {print $2}' "$STATE_DIR/state")
    [[ "$previous_new" == blue || "$previous_new" == green ]] || die 'Invalid completed deployment record.'
    [[ $(upstream_slot) == "$previous_new" ]] || die 'Completed deployment is no longer active; create a new deployment.'
    recorded_sha=$(awk -F= '$1=="jar_sha256" {print $2}' "$STATE_DIR/state")
    [[ $(sha256sum "$SETTY_DEPLOY_ROOT/slots/$previous_new/app.jar" | awk '{print $1}') == "$recorded_sha" ]] \
        || die 'Completed deployment JAR has changed; create a new deployment.'
    wait_for_route "$previous_new" && smoke_check || die 'Completed deployment is no longer healthy.'
    log "Deployment already completed: $DEPLOYMENT_ID"
    exit 0
fi
[[ ! -e "$STATE_DIR" ]] || die 'This deployment ID has an unfinished record; create a new deployment after recovery.'
preflight
smoke_check

OLD_SLOT=$(upstream_slot)
NEW_SLOT=green
[[ "$OLD_SLOT" == green ]] && NEW_SLOT=blue
OLD_SERVICE=$(service_for "$OLD_SLOT")
NEW_SERVICE=$(service_for "$NEW_SLOT")
NEW_DIR="$SETTY_DEPLOY_ROOT/slots/$NEW_SLOT"
JAR_SHA256=$(sha256sum "$APP_JAR" | awk '{print $1}')
PHASE=prepared
SWITCH_ATTEMPTED=0

install -d -m 0700 "$STATE_DIR"
cp -p "$SETTY_NGINX_UPSTREAM" "$STATE_DIR/upstream.before.conf"

write_state() {
    local temporary="$STATE_DIR/state.tmp"
    {
        printf 'deployment_id=%s\nold_slot=%s\nnew_slot=%s\n' "$DEPLOYMENT_ID" "$OLD_SLOT" "$NEW_SLOT"
        printf 'jar_sha256=%s\nphase=%s\nswitch_attempted=%s\n' "$JAR_SHA256" "$PHASE" "$SWITCH_ATTEMPTED"
        printf 'updated_at_utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$temporary"
    chmod 0600 "$temporary"
    mv -f "$temporary" "$STATE_DIR/state"
}

restore_upstream() {
    local temporary
    temporary=$(mktemp "${SETTY_NGINX_UPSTREAM}.XXXXXX") || return 1
    cp -p "$STATE_DIR/upstream.before.conf" "$temporary" \
        && mv -f "$temporary" "$SETTY_NGINX_UPSTREAM" || return 1
    nginx -t && systemctl reload nginx.service && wait_for_route "$OLD_SLOT"
}

rollback() {
    log "Restoring $OLD_SLOT; failed phase: $PHASE"
    # 구버전 JVM 종료 이후 실패했다면 보관한 JAR로 재기동한 뒤 복구한다.
    if ! systemctl is-active --quiet "$OLD_SERVICE"; then
        systemctl start "$OLD_SERVICE" || return 1
    fi
    wait_for_slot "$OLD_SLOT" || return 1
    systemctl enable "$OLD_SERVICE" || return 1
    if (( SWITCH_ATTEMPTED )); then
        restore_upstream || return 1
    else
        [[ $(upstream_slot) == "$OLD_SLOT" ]] && wait_for_route "$OLD_SLOT" || return 1
    fi
    smoke_check "$OLD_SLOT" || return 1
    systemctl disable "$NEW_SERVICE" || return 1
    # 복구 후에도 처리 중인 요청이 남아 있으면 새 JVM을 강제로 종료하지 않는다.
    wait_for_connections "$NEW_SLOT" || return 1
    systemctl stop "$NEW_SERVICE" || return 1
    PHASE=rolled_back
    write_state || return 1
    rm -f "$PENDING_FILE"
}

on_failure() {
    local status=$1 line=$2
    trap - ERR INT TERM
    set +e
    log "Deployment failed at line $line (status=$status)."
    if rollback; then
        log "Previous slot restored. Deployment remains failed: $DEPLOYMENT_ID"
    else
        PHASE=recovery_required
        write_state
        log "Recovery incomplete: keep both JARs/processes and inspect $STATE_DIR and $PENDING_FILE."
    fi
    exit 1
}

write_state
printf '%s\n' "$DEPLOYMENT_ID" > "$PENDING_FILE"
trap 'on_failure "$?" "$LINENO"' ERR
trap 'on_failure 130 "$LINENO"' INT
trap 'on_failure 143 "$LINENO"' TERM

# ApplicationStart부터 ValidateService까지 나누지 않고 전체 변경 동안 같은 잠금을 유지한다.
log "Deploying $DEPLOYMENT_ID: $OLD_SLOT -> $NEW_SLOT ($JAR_SHA256)"
install -m 0644 "$APP_JAR" "$NEW_DIR/app.jar.$DEPLOYMENT_ID"
mv -f "$NEW_DIR/app.jar.$DEPLOYMENT_ID" "$NEW_DIR/app.jar"
PHASE=installed
write_state
systemctl reset-failed "$NEW_SERVICE" || true
systemctl start "$NEW_SERVICE"
wait_for_slot "$NEW_SLOT"
PHASE=healthy
write_state

# 파일 교체·reload 사이에서 실패해도 백업을 복구할 수 있도록 변경 전에 기록한다.
systemctl enable "$NEW_SERVICE"
SWITCH_ATTEMPTED=1
PHASE=switching
write_state
temporary=$(mktemp "${SETTY_NGINX_UPSTREAM}.XXXXXX")
cp -p "$STATE_DIR/upstream.before.conf" "$temporary"
sed "s/127\.0\.0\.1:$(port_for "$OLD_SLOT")/127.0.0.1:$(port_for "$NEW_SLOT")/g" \
    "$STATE_DIR/upstream.before.conf" > "$temporary"
mv -f "$temporary" "$SETTY_NGINX_UPSTREAM"
nginx -t
systemctl reload nginx.service
wait_for_route "$NEW_SLOT"
smoke_check
PHASE=observing
write_state

deadline=$((SECONDS + SETTY_OBSERVE_SECONDS))
while (( SECONDS < deadline )); do
    slot_healthy "$OLD_SLOT"
    slot_healthy "$NEW_SLOT"
    probe_route "$NEW_SLOT"
    smoke_check
    sleep "$SETTY_POLL_SECONDS"
done

PHASE=draining
write_state
wait_for_connections "$OLD_SLOT"
# 실제 종료 직전에도 활성 경로와 외부 요청을 다시 확인한다.
wait_for_route "$NEW_SLOT"
smoke_check
systemctl disable "$OLD_SERVICE"
systemctl stop "$OLD_SERVICE"
slot_healthy "$NEW_SLOT"
wait_for_route "$NEW_SLOT"
smoke_check
PHASE=completed
write_state
touch "$STATE_DIR/completed"
rm -f "$PENDING_FILE"
trap - ERR INT TERM
log "Deployment completed: $NEW_SLOT active; $OLD_SLOT stopped."
