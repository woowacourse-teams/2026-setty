#!/usr/bin/env bash
# SETTY binlog 백업: 지금 binlog를 닫고, 닫힌 파일 중 아직 올리지 않은 것을 gzip → 암호화 → S3에 올린 뒤 성공 지표를 보낸다.
# systemd timer가 5분마다 부른다. 보관 기간 지난 binlog 정리는 하루 한 번만 한다.
set -euo pipefail

BUCKET="${BUCKET:-techcourse-project-2026}"
PREFIX="${PREFIX:-setty/backup/db/binlog}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
STATE_DIR="${STATE_DIR:-/var/lib/setty-backup}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-binlog}"
KEEP_DAYS="${KEEP_DAYS:-31}"
METRIC_NAMESPACE="${METRIC_NAMESPACE-Setty/Prod}"
METRIC_REGION="${METRIC_REGION:-ap-northeast-2}"

export TZ=Asia/Seoul
LAST_FILE="$STATE_DIR/binlog-last-uploaded"
PRUNED_FILE="$STATE_DIR/binlog-last-pruned"
mkdir -p "$STATE_DIR" "$WORK_DIR"
chmod 700 "$STATE_DIR" "$WORK_DIR"
trap 'rm -f "$WORK_DIR"/*.gz.enc' EXIT

log() { printf '%s %s\n' "$(date +%FT%T%z)" "$*"; }
sql() { mysql --defaults-file=/root/.my.cnf -N -e "$1"; }

[ -r "$KEY_FILE" ] || { log "key file not readable: $KEY_FILE"; exit 1; }
binlog_dir="$(dirname "$(sql "SELECT @@log_bin_basename")")"

sql "FLUSH BINARY LOGS"
binlogs="$(sql "SHOW BINARY LOGS")"
mapfile -t logs < <(awk '{print $1}' <<<"$binlogs")
[ "${#logs[@]}" -ge 2 ] || { log "expected at least two binlog files after flush"; exit 1; }
closed=("${logs[@]:0:${#logs[@]}-1}")

last="$(cat "$LAST_FILE" 2>/dev/null || true)"
if [ -n "$last" ] && [[ "$last" > "${logs[-1]}" ]]; then
  log "last uploaded $last is newer than server binlogs; binlog numbering was reset, uploading all closed files"
  last=""
fi

uploaded=0
for name in "${closed[@]}"; do
  [ -n "$last" ] && [[ ! "$name" > "$last" ]] && continue
  object="$(date -r "$binlog_dir/$name" +%Y%m%d-%H%M%S)-$name.gz.enc"
  gzip -c "$binlog_dir/$name" \
    | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$KEY_FILE" -out "$WORK_DIR/$object"
  aws s3 cp --only-show-errors "$WORK_DIR/$object" "s3://$BUCKET/$PREFIX/$object"
  rm -f "$WORK_DIR/$object"
  printf '%s\n' "$name" > "$LAST_FILE"
  uploaded=$((uploaded + 1))
  log "uploaded $PREFIX/$object"
done
log "binlog upload done uploaded=$uploaded"

today="$(date +%F)"
if [ "$(cat "$PRUNED_FILE" 2>/dev/null || true)" != "$today" ]; then
  # 보관 기간이 지난 객체는 이전 버전과 삭제 마커까지 지운다(버킷 버전 관리 켜짐).
  cutoff="$(date -u -d "-${KEEP_DAYS} days" +%Y-%m-%dT%H:%M:%S)"
  aws s3api list-object-versions --bucket "$BUCKET" --prefix "$PREFIX/" \
    --query "[Versions,DeleteMarkers][] | [?LastModified<'${cutoff}'].[Key,VersionId]" --output text \
  | while read -r key vid; do
      [ -z "${key:-}" ] && continue
      aws s3api delete-object --bucket "$BUCKET" --key "$key" --version-id "$vid" >/dev/null
      log "pruned $key ($vid)"
    done
  printf '%s\n' "$today" > "$PRUNED_FILE"
fi

if [ -n "$METRIC_NAMESPACE" ]; then
  aws cloudwatch put-metric-data --region "$METRIC_REGION" --namespace "$METRIC_NAMESPACE" \
    --metric-name BinlogBackupSucceeded --value 1 --unit Count
fi
