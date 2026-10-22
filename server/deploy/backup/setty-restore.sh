#!/usr/bin/env bash
# SETTY DB 복구: 백업 파일(S3 key 또는 로컬 경로) → 복호화 → gunzip → 새 DB로 복원.
# 사용: sudo /opt/setty/backup/setty-restore.sh <s3 key 또는 로컬 파일> <새 DB 이름>
# 운영 DB와 이미 있는 DB에는 복원하지 않는다. 실행하면 MySQL root 비밀번호를 묻는다.
set -euo pipefail

if [ "$#" -ne 2 ]; then
  echo "사용: $0 <s3 key 또는 로컬 파일> <새 DB 이름>" >&2
  exit 2
fi
SRC="$1"; TARGET_DB="$2"
BUCKET="${BUCKET:-techcourse-project-2026}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-backup}"
PROTECTED_DB="${PROTECTED_DB:-setty}"
ADMIN_DEFAULTS_FILE="${ADMIN_DEFAULTS_FILE:-}"

if [ "$TARGET_DB" = "$PROTECTED_DB" ]; then
  echo "운영 DB($PROTECTED_DB)에는 복원하지 않습니다. 새 DB 이름을 지정하세요." >&2
  exit 1
fi
if [[ ! "$TARGET_DB" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "DB 이름에는 영문, 숫자, _만 씁니다." >&2
  exit 1
fi

DOWNLOADED=""; ADMIN_CNF=""
cleanup() {
  if [ -n "$DOWNLOADED" ]; then rm -f "$DOWNLOADED"; fi
  if [ -n "$ADMIN_CNF" ]; then rm -f "$ADMIN_CNF"; fi
}
trap cleanup EXIT

mkdir -p "$WORK_DIR"; chmod 700 "$WORK_DIR"
if [ -f "$SRC" ]; then
  LOCAL="$SRC"
else
  LOCAL="$WORK_DIR/$(basename "$SRC")"
  DOWNLOADED="$LOCAL"
  aws s3 cp --only-show-errors "s3://$BUCKET/$SRC" "$LOCAL"
fi

if [ -z "$ADMIN_DEFAULTS_FILE" ]; then
  read -r -s -p 'MySQL root 비밀번호: ' ROOT_PW; echo
  ADMIN_CNF="$(mktemp)"; chmod 600 "$ADMIN_CNF"
  ROOT_PW="${ROOT_PW//\\/\\\\}"; ROOT_PW="${ROOT_PW//\"/\\\"}"
  printf '[client]\nuser=root\npassword="%s"\n' "$ROOT_PW" > "$ADMIN_CNF"
  unset ROOT_PW
  ADMIN_DEFAULTS_FILE="$ADMIN_CNF"
fi

if ! openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$KEY_FILE" -in "$LOCAL" | gunzip -t; then
  echo "백업 파일을 풀 수 없습니다. 키나 파일을 확인하세요. DB는 만들지 않았습니다." >&2
  exit 1
fi

start=$(date +%s)
{
  printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\nUSE `%s`;\n' "$TARGET_DB" "$TARGET_DB"
  openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$KEY_FILE" -in "$LOCAL" | gunzip
} | mysql --defaults-file="$ADMIN_DEFAULTS_FILE"
end=$(date +%s)
printf '복원 완료: %s → %s (%s초)\n' "$SRC" "$TARGET_DB" "$((end-start))"
