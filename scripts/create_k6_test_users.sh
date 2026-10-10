#!/usr/bin/env bash
set -euo pipefail

# Create or verify disposable buyer accounts for the k6/browser scenario.
# Usage: scripts/create_k6_test_users.sh
# For non-interactive use, provide PASSWORD in the environment.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${BASE_URL:-https://www.setty.cloud/dev}"
CSV_PATH="${CSV_PATH:-${REPO_ROOT}/.local/k6-test-users.csv}"
PASSWORD="${PASSWORD:-}"
COUNT="${COUNT:-100}"
ACCOUNT_PREFIX="${ACCOUNT_PREFIX:-k6user}"

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

if [[ -z "${PASSWORD}" ]]; then
    if [[ -t 0 ]]; then
        read -r -s -p '테스트 계정 비밀번호(8~64자): ' PASSWORD
        printf '\n'
    else
        fail "비대화형 실행에는 PASSWORD 환경변수가 필요합니다."
    fi
fi
if (( ${#PASSWORD} < 8 || ${#PASSWORD} > 64 )); then
    fail "PASSWORD는 서버 회원가입 규칙에 따라 8~64자여야 합니다."
fi
if [[ ! "${PASSWORD}" =~ ^[A-Za-z0-9._!@#%+=-]+$ ]]; then
    fail "PASSWORD는 CSV/JSON에 안전한 영문·숫자·일부 기호만 사용할 수 있습니다."
fi
if [[ ! "${COUNT}" =~ ^[1-9][0-9]*$ ]] || (( ${#COUNT} > 4 )) || (( COUNT > 9999 )); then
    fail "COUNT는 1~9999 사이의 정수여야 합니다."
fi
if [[ ! "${ACCOUNT_PREFIX}" =~ ^[a-z0-9]+$ ]]; then
    fail "ACCOUNT_PREFIX는 영문 소문자와 숫자만 사용할 수 있습니다."
fi
if (( ${#ACCOUNT_PREFIX} + ${#COUNT} > 20 )); then
    fail "ACCOUNT_PREFIX와 계정 번호의 합은 20자를 넘을 수 없습니다."
fi
if [[ "${BASE_URL}" != https://* && "${BASE_URL}" != http://* ]]; then
    fail "BASE_URL은 http:// 또는 https://로 시작해야 합니다."
fi

while [[ "${BASE_URL}" == */ ]]; do
    BASE_URL="${BASE_URL%/}"
done

umask 077
if [[ "${CSV_PATH}" != /* ]]; then
    CSV_PATH="${PWD}/${CSV_PATH}"
fi
csv_directory="$(dirname "${CSV_PATH}")"
mkdir -p "${csv_directory}"
temporary_csv="$(mktemp "${CSV_PATH}.tmp.XXXXXX")"
trap 'rm -f "${temporary_csv}"' EXIT
chmod 600 "${temporary_csv}"
printf 'loginId,password\n' > "${temporary_csv}"

http_status() {
    curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
        --connect-timeout 10 --max-time 30 "$@"
}

width="${#COUNT}"
for ((i = 1; i <= COUNT; i++)); do
    printf -v suffix "%0${width}d" "${i}"
    login_id="${ACCOUNT_PREFIX}${suffix}"
    phone_number="$(printf '010-0000-%04d' "${i}")"
    signup_payload="{\"loginId\":\"${login_id}\",\"password\":\"${PASSWORD}\",\"phoneNumber\":\"${phone_number}\",\"address\":\"부하 테스트 주소 ${i}\"}"

    signup_status="$(http_status \
        "${BASE_URL}/api/auth/signup" \
        -H 'Content-Type: application/json' \
        --data "${signup_payload}")"

    if [[ "${signup_status}" != 201 ]]; then
        login_payload="{\"loginId\":\"${login_id}\",\"password\":\"${PASSWORD}\"}"
        login_status="$(http_status \
            "${BASE_URL}/api/auth/login" \
            -H 'Content-Type: application/json' \
            --data "${login_payload}")"
        if [[ "${login_status}" != 200 ]]; then
            fail "계정 생성·확인 실패: ${login_id} (signup=${signup_status}, login=${login_status})"
        fi
    fi

    printf '%s,%s\n' "${login_id}" "${PASSWORD}" >> "${temporary_csv}"
done

mv -f "${temporary_csv}" "${CSV_PATH}"
trap - EXIT
chmod 600 "${CSV_PATH}"
printf '계정 %s개를 생성·확인해 %s 에 저장했습니다.\n' "${COUNT}" "${CSV_PATH}"
