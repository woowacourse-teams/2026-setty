#!/usr/bin/env bash
# SETTY DB 백업: mysqldump → gzip → openssl 암호화 → S3 업로드 → 보관 기간 지난 백업 정리 → 성공 지표 전송.
# systemd timer가 매일 04:00 KST에 부른다. 수동 실행: sudo /opt/setty/backup/setty-backup.sh
set -euo pipefail

DB="${DB:-setty}"
BUCKET="${BUCKET:-techcourse-project-2026}"
PREFIX="${PREFIX:-setty/backup/db/daily}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-backup}"
KEEP_DAYS="${KEEP_DAYS:-30}"
METRIC_NAMESPACE="${METRIC_NAMESPACE-Setty/Prod}"
METRIC_REGION="${METRIC_REGION:-ap-northeast-2}"

export TZ=Asia/Seoul
FILE="$DB-$(date +%Y%m%d-%H%M%S).sql.gz.enc"
mkdir -p "$WORK_DIR"
chmod 700 "$WORK_DIR"
trap 'rm -f "$WORK_DIR/$FILE"' EXIT

log() { printf '%s %s\n' "$(date +%FT%T%z)" "$*"; }

start=$(date +%s)
mysqldump --defaults-file=/root/.my.cnf \
  --single-transaction --no-tablespaces --routines --triggers --events --set-gtid-purged=OFF "$DB" \
  | gzip -9 \
  | openssl enc -aes-256-cbc -pbkdf2 -salt -pass "file:$KEY_FILE" -out "$WORK_DIR/$FILE"
end=$(date +%s)
log "dump+encrypt done file=$FILE size=$(stat -c%s "$WORK_DIR/$FILE")B took=$((end-start))s"

aws s3 cp --only-show-errors "$WORK_DIR/$FILE" "s3://$BUCKET/$PREFIX/$FILE"
log "uploaded $PREFIX/$FILE"

# 보관 기간이 지난 객체는 이전 버전과 삭제 마커까지 지운다(버킷 버전 관리 켜짐).
cutoff="$(date -u -d "-${KEEP_DAYS} days" +%Y-%m-%dT%H:%M:%S)"
aws s3api list-object-versions --bucket "$BUCKET" --prefix "$PREFIX/" \
  --query "[Versions,DeleteMarkers][] | [?LastModified<'${cutoff}'].[Key,VersionId]" --output text \
| while read -r key vid; do
    [ -z "${key:-}" ] && continue
    aws s3api delete-object --bucket "$BUCKET" --key "$key" --version-id "$vid" >/dev/null
    log "pruned $key ($vid)"
  done
if [ -n "$METRIC_NAMESPACE" ]; then
  aws cloudwatch put-metric-data --region "$METRIC_REGION" --namespace "$METRIC_NAMESPACE" \
    --metric-name BackupSucceeded --value 1 --unit Count
  log "reported BackupSucceeded to $METRIC_NAMESPACE"
fi
log "backup finished"
