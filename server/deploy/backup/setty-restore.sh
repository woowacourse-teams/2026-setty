#!/usr/bin/env bash
# SETTY DB 복구: S3 객체 → 복호화 → gunzip → 지정한 DB로 복원.
# 사용: sudo /opt/setty/backup/setty-restore.sh <s3 key 또는 로컬 파일> <대상 DB>
set -euo pipefail
SRC="$1"; TARGET_DB="$2"
BUCKET="${BUCKET:-techcourse-project-2026}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-backup}"
mkdir -p "$WORK_DIR"; chmod 700 "$WORK_DIR"
if [ -f "$SRC" ]; then LOCAL="$SRC"; else LOCAL="$WORK_DIR/$(basename "$SRC")"; aws s3 cp --only-show-errors "s3://$BUCKET/$SRC" "$LOCAL"; fi
start=$(date +%s)
openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$KEY_FILE" -in "$LOCAL" \
  | gunzip \
  | mysql --defaults-file=/root/.my.cnf "$TARGET_DB"
end=$(date +%s)
printf '복원 완료: %s → %s (%s초)\n' "$SRC" "$TARGET_DB" "$((end-start))"
