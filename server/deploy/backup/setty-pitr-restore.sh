#!/usr/bin/env bash
# SETTY 시점 복구: 매일 덤프를 새 DB에 풀고, binlog를 고른 위치 직전까지 재생한다.
# 사용: sudo /opt/setty/backup/setty-pitr-restore.sh events  <덤프 s3 key 또는 파일>
#       sudo /opt/setty/backup/setty-pitr-restore.sh restore <덤프 s3 key 또는 파일> <새 DB 이름> <binlog 파일> <위치>
# events로 덤프 이후 setty DB의 삭제·수정·DDL을 보고, 되돌릴 묶음의 파일과 위치를 restore에 넣는다.
# binlog는 서버 디스크에서 읽는다. 서버를 잃었을 때는 BINLOG_SOURCE=s3 로 S3에 올라간 binlog를 쓴다.
set -euo pipefail

usage() {
  echo "사용: $0 events <덤프>" >&2
  echo "      $0 restore <덤프> <새 DB 이름> <binlog 파일> <위치>" >&2
  exit 2
}
[ "$#" -ge 2 ] || usage
MODE="$1"; DUMP="$2"
case "$MODE" in
  events) [ "$#" -eq 2 ] || usage ;;
  restore) [ "$#" -eq 5 ] || usage; TARGET_DB="$3"; STOP_FILE="$4"; STOP_POS="$5" ;;
  *) usage ;;
esac

BUCKET="${BUCKET:-techcourse-project-2026}"
KEY_FILE="${KEY_FILE:-/root/setty-backup.key}"
WORK_DIR="${WORK_DIR:-/var/tmp/setty-backup}"
SOURCE_DB="${SOURCE_DB:-setty}"
BINLOG_SOURCE="${BINLOG_SOURCE:-local}"
BINLOG_PREFIX="${BINLOG_PREFIX:-setty/backup/db/binlog}"
RESTORE_SCRIPT="${RESTORE_SCRIPT:-$(dirname "$0")/setty-restore.sh}"
ADMIN_DEFAULTS_FILE="${ADMIN_DEFAULTS_FILE:-}"

export TZ=Asia/Seoul
if [ "$MODE" = "restore" ]; then
  if [ "$TARGET_DB" = "$SOURCE_DB" ]; then
    echo "운영 DB($SOURCE_DB)에는 복원하지 않습니다. 새 DB 이름을 지정하세요." >&2
    exit 1
  fi
  [[ "$TARGET_DB" =~ ^[A-Za-z0-9_]+$ ]] || { echo "DB 이름에는 영문, 숫자, _만 씁니다." >&2; exit 1; }
  [[ "$STOP_POS" =~ ^[0-9]+$ ]] || { echo "위치는 숫자입니다." >&2; exit 1; }
fi

mkdir -p "$WORK_DIR"; chmod 700 "$WORK_DIR"
RUN_DIR="$(mktemp -d "$WORK_DIR/pitr.XXXXXX")"
ADMIN_CNF=""
cleanup() {
  rm -rf "$RUN_DIR"
  if [ -n "$ADMIN_CNF" ]; then rm -f "$ADMIN_CNF"; fi
}
trap cleanup EXIT

decrypt() { openssl enc -d -aes-256-cbc -pbkdf2 -pass "file:$KEY_FILE" -in "$1" | gunzip; }

if [ -f "$DUMP" ]; then
  LOCAL_DUMP="$DUMP"
else
  LOCAL_DUMP="$RUN_DIR/$(basename "$DUMP")"
  aws s3 cp --only-show-errors "s3://$BUCKET/$DUMP" "$LOCAL_DUMP"
fi

header="$(decrypt "$LOCAL_DUMP" 2>/dev/null | head -n 60 || true)"
source_line="$(grep -m1 'CHANGE REPLICATION SOURCE' <<<"$header" || true)"
if [ -z "$source_line" ]; then
  echo "덤프에서 binlog 시작 위치를 찾지 못했습니다. 키가 틀렸거나 --source-data 적용 전 덤프입니다." >&2
  exit 1
fi
START_FILE="$(sed -E "s/.*SOURCE_LOG_FILE='([^']+)'.*/\1/" <<<"$source_line")"
START_POS="$(sed -E 's/.*SOURCE_LOG_POS=([0-9]+).*/\1/' <<<"$source_line")"
echo "덤프 시작 위치: $START_FILE $START_POS"

BINLOG_DIR="$RUN_DIR/binlogs"
mkdir -p "$BINLOG_DIR"
if [ "$BINLOG_SOURCE" = "local" ]; then
  base="$(mysql --defaults-file=/root/.my.cnf -N -e "SELECT @@log_bin_basename")"
  while read -r path; do
    name="$(basename "$path")"
    [[ "$name" < "$START_FILE" ]] && continue
    ln -s "$(dirname "$base")/$name" "$BINLOG_DIR/$name"
  done < "$base.index"
elif [ "$BINLOG_SOURCE" = "s3" ]; then
  dump_stamp="$(grep -oE '[0-9]{8}-[0-9]{6}' <<<"$(basename "$DUMP")" | head -n1 || true)"
  aws s3 ls "s3://$BUCKET/$BINLOG_PREFIX/" | awk '{print $4}' | while read -r object; do
    stamp="${object:0:15}"; name="${object:16}"; name="${name%.gz.enc}"
    [[ "$name" < "$START_FILE" ]] && continue
    [ -n "$dump_stamp" ] && [[ "$stamp" < "$dump_stamp" ]] && continue
    aws s3 cp --only-show-errors "s3://$BUCKET/$BINLOG_PREFIX/$object" "$RUN_DIR/$object"
    decrypt "$RUN_DIR/$object" > "$BINLOG_DIR/$name"
    rm -f "$RUN_DIR/$object"
  done
  echo "S3 binlog를 씁니다. 마지막 업로드 뒤 최대 5분의 변경은 없습니다."
else
  echo "BINLOG_SOURCE는 local 또는 s3입니다." >&2
  exit 1
fi
mapfile -t FILES < <(ls "$BINLOG_DIR" | sort)
if [ "${#FILES[@]}" -eq 0 ] || [ "${FILES[0]}" != "$START_FILE" ]; then
  echo "덤프 시작 위치의 binlog($START_FILE)가 없습니다. 이 덤프로는 시점 복구를 할 수 없습니다." >&2
  exit 1
fi

if [ "$MODE" = "events" ]; then
  printf '%-15s %-10s %-19s %s\n' "binlog 파일" "위치" "시각" "내용"
  first=1
  for name in "${FILES[@]}"; do
    start_opt=(); [ "$first" -eq 1 ] && start_opt=(--start-position="$START_POS"); first=0
    mysqlbinlog --base64-output=DECODE-ROWS -v "${start_opt[@]}" "$BINLOG_DIR/$name" \
    | awk -v file="$name" -v db="$SOURCE_DB" '
        function flush(  k) {
          for (k in cnt) printf "%-15s %-10s %-19s %s rows=%d\n", file, txpos, txtime, k, cnt[k]
          for (k in ddl) printf "%-15s %-10s %-19s %s\n", file, txpos, txtime, ddl[k]
          delete cnt; delete ddl; nddl = 0
        }
        /^# at [0-9]+/ { pos = $3 }
        /GTID/ && /last_committed/ {
          flush(); txpos = pos
          txtime = "20" substr($1, 2, 2) "-" substr($1, 4, 2) "-" substr($1, 6, 2) " " $2
        }
        /^use `/ { cur = $2; gsub(/`|\/\*!\*\/;/, "", cur) }
        /^### DELETE FROM `/ { split($4, t, "`"); if (t[2] == db) cnt["DELETE " t[2] "." t[4]]++ }
        /^### UPDATE `/ { split($3, t, "`"); if (t[2] == db) cnt["UPDATE " t[2] "." t[4]]++ }
        toupper($0) ~ /^(DROP|TRUNCATE|ALTER|RENAME) / { if (cur == db) ddl[++nddl] = $0 }
        END { flush() }'
  done
  echo
  echo "되돌릴 묶음의 파일과 위치를 restore에 넣으면 그 묶음 직전까지 복원합니다."
  exit 0
fi

replay=()
for name in "${FILES[@]}"; do
  replay+=("$BINLOG_DIR/$name")
  [ "$name" = "$STOP_FILE" ] && break
done
if [ "$(basename "${replay[-1]}")" != "$STOP_FILE" ]; then
  echo "멈출 binlog($STOP_FILE)가 덤프 이후 binlog 목록에 없습니다." >&2
  exit 1
fi

if [ -z "$ADMIN_DEFAULTS_FILE" ]; then
  read -r -s -p 'MySQL root 비밀번호: ' ROOT_PW; echo
  ADMIN_CNF="$(mktemp)"; chmod 600 "$ADMIN_CNF"
  ROOT_PW="${ROOT_PW//\\/\\\\}"; ROOT_PW="${ROOT_PW//\"/\\\"}"
  printf '[client]\nuser=root\npassword="%s"\n' "$ROOT_PW" > "$ADMIN_CNF"
  unset ROOT_PW
  ADMIN_DEFAULTS_FILE="$ADMIN_CNF"
fi

ADMIN_DEFAULTS_FILE="$ADMIN_DEFAULTS_FILE" KEY_FILE="$KEY_FILE" PROTECTED_DB="$SOURCE_DB" WORK_DIR="$WORK_DIR" \
  "$RESTORE_SCRIPT" "$LOCAL_DUMP" "$TARGET_DB"

start=$(date +%s)
mysqlbinlog --start-position="$START_POS" --stop-position="$STOP_POS" \
  --rewrite-db="$SOURCE_DB->$TARGET_DB" --database="$TARGET_DB" "${replay[@]}" \
| mysql --defaults-file="$ADMIN_DEFAULTS_FILE" --init-command="SET sql_log_bin=0"
end=$(date +%s)
printf 'binlog 재생 완료: %s %s → %s %s 직전 (파일 %s개, %s초)\n' \
  "$START_FILE" "$START_POS" "$STOP_FILE" "$STOP_POS" "${#replay[@]}" "$((end-start))"
echo "운영 DB($SOURCE_DB)는 바꾸지 않았습니다. $TARGET_DB 를 확인한 뒤 전환 방법은 팀과 정합니다."
