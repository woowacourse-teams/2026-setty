#!/usr/bin/env bash
# SETTY DB 백업: mysqldump → gzip → openssl 암호화 → S3 업로드 → 기한 지난 백업 정리.
# systemd timer가 매일 04:00 KST에 부른다. 수동 실행: sudo /opt/setty/backup/setty-backup.sh
set -euo pipefail

DB="${DB:-setty}"
BUCKET="${BUCKET:-techcourse-project-2026}"
PREFIX="${PREFIX:-setty/backup/db}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-backup}"
DAILY_KEEP_DAYS="${DAILY_KEEP_DAYS:-30}"
MONTHLY_KEEP_DAYS="${MONTHLY_KEEP_DAYS:-365}"
YEARLY_KEEP_DAYS="${YEARLY_KEEP_DAYS:-1825}"

export TZ=Asia/Seoul
STAMP="$(date +%Y%m%d-%H%M%S)"
TODAY_DAY="$(date +%d)"
TODAY_MONTH_DAY="$(date +%m%d)"
FILE="$DB-$STAMP.sql.gz.enc"
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
SIZE=$(stat -c%s "$WORK_DIR/$FILE")
log "dump+encrypt done file=$FILE size=${SIZE}B took=$((end-start))s"

upload() { aws s3 cp --only-show-errors "$WORK_DIR/$FILE" "s3://$BUCKET/$PREFIX/$1/$FILE"; log "uploaded $1/$FILE"; }
upload daily
[ "$TODAY_DAY" = "01" ] && upload monthly
[ "$TODAY_MONTH_DAY" = "0101" ] && upload yearly

# 기한 지난 객체는 버전과 삭제 마커까지 지운다(버킷 버전 관리 켜짐).
prune() {
  local tier="$1" keep_days="$2" cutoff
  cutoff="$(date -u -d "-${keep_days} days" +%Y-%m-%dT%H:%M:%S)"
  aws s3api list-object-versions --bucket "$BUCKET" --prefix "$PREFIX/$tier/" \
    --query "[Versions,DeleteMarkers][][?LastModified<'${cutoff}'].[Key,VersionId]" --output text 2>/dev/null \
  | while read -r key vid; do
      [ -z "${key:-}" ] && continue
      aws s3api delete-object --bucket "$BUCKET" --key "$key" --version-id "$vid" >/dev/null
      log "pruned $key ($vid)"
    done
}
prune daily "$DAILY_KEEP_DAYS"
prune monthly "$MONTHLY_KEEP_DAYS"
prune yearly "$YEARLY_KEEP_DAYS"
log "backup finished"
