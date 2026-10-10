#!/usr/bin/env bash
set -euo pipefail

# Create or resume a batch of disposable listings for the k6/browser scenario.
# First create a dedicated seller CSV with create_k6_test_users.sh.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${BASE_URL:-https://www.setty.cloud/dev}"
SELLER_CSV_PATH="${SELLER_CSV_PATH:-${REPO_ROOT}/.local/k6-test-seller.csv}"
BUYER_CSV_PATH="${BUYER_CSV_PATH:-${REPO_ROOT}/.local/k6-test-users.csv}"
IMAGE_PATH="${IMAGE_PATH:-${REPO_ROOT}/client/public/images/listings/floor-lamp.png}"
COUNT="${COUNT:-20}"
RUN_TAG="${RUN_TAG:-$(date '+%Y%m%d%H%M%S')}"
TITLE_PREFIX="${TITLE_PREFIX:-k6-load-${RUN_TAG}}"
LISTINGS_CSV_PATH="${LISTINGS_CSV_PATH:-${REPO_ROOT}/.local/k6-test-listings-${RUN_TAG}.csv}"

fail() {
    printf '%s\n' "$1" >&2
    exit 1
}

if ! command -v curl >/dev/null 2>&1; then
    fail "curl이 필요합니다."
fi
if ! command -v python3 >/dev/null 2>&1; then
    fail "로그인 응답을 읽으려면 python3이 필요합니다."
fi
if [[ ! -f "${SELLER_CSV_PATH}" ]]; then
    fail "판매자 계정 CSV가 없습니다. 먼저 COUNT=1 ACCOUNT_PREFIX=k6seller CSV_PATH='${SELLER_CSV_PATH}' scripts/create_k6_test_users.sh 를 실행하세요."
fi
if [[ ! -f "${IMAGE_PATH}" ]]; then
    fail "매물 이미지 파일을 찾을 수 없습니다: ${IMAGE_PATH}"
fi
if [[ "${BASE_URL}" != https://* && "${BASE_URL}" != http://* ]]; then
    fail "BASE_URL은 http:// 또는 https://로 시작해야 합니다."
fi
if [[ ! "${COUNT}" =~ ^[1-9][0-9]*$ ]] || (( ${#COUNT} > 4 )) || (( COUNT > 9999 )); then
    fail "COUNT는 1~9999 사이의 정수여야 합니다."
fi
if [[ ! "${RUN_TAG}" =~ ^[A-Za-z0-9_-]{1,32}$ ]]; then
    fail "RUN_TAG는 영문·숫자·밑줄·하이픈 1~32자로 지정하세요."
fi
if [[ ! "${TITLE_PREFIX}" =~ ^[A-Za-z0-9\ _-]+$ ]]; then
    fail "TITLE_PREFIX에는 영문·숫자·공백·밑줄·하이픈만 사용할 수 있습니다."
fi
if (( ${#TITLE_PREFIX} + 1 + ${#COUNT} > 100 )); then
    fail "TITLE_PREFIX와 번호의 합은 매물명 제한 100자를 넘을 수 없습니다."
fi

case "${IMAGE_PATH}" in
    *.png|*.PNG) IMAGE_CONTENT_TYPE='image/png' ;;
    *.jpg|*.JPG|*.jpeg|*.JPEG) IMAGE_CONTENT_TYPE='image/jpeg' ;;
    *.webp|*.WEBP) IMAGE_CONTENT_TYPE='image/webp' ;;
    *) fail "이미지는 PNG, JPEG, WEBP 파일이어야 합니다." ;;
esac

while [[ "${BASE_URL}" == */ ]]; do
    BASE_URL="${BASE_URL%/}"
done

seller_header=''
seller_login_id=''
seller_password=''
{
    IFS=, read -r seller_header
    IFS=, read -r seller_login_id seller_password
} < "${SELLER_CSV_PATH}"
if [[ "${seller_header}" != 'loginId,password' || -z "${seller_login_id}" || -z "${seller_password}" ]]; then
    fail "판매자 CSV는 loginId,password 헤더와 계정 한 행이 필요합니다."
fi
seller_login_id="${seller_login_id%$'\r'}"
seller_password="${seller_password%$'\r'}"
if [[ ! "${seller_login_id}" =~ ^[a-z0-9]{4,20}$ ]]; then
    fail "판매자 CSV의 loginId 형식이 올바르지 않습니다."
fi
if [[ ! "${seller_password}" =~ ^[A-Za-z0-9._!@#%+=-]{8,64}$ ]]; then
    fail "판매자 CSV 비밀번호 형식이 올바르지 않습니다."
fi

if [[ -f "${BUYER_CSV_PATH}" ]]; then
    while IFS=, read -r buyer_login_id _; do
        buyer_login_id="${buyer_login_id%$'\r'}"
        if [[ "${buyer_login_id}" == "${seller_login_id}" ]]; then
            fail "판매자 계정은 구매자 CSV의 계정과 분리해야 합니다: ${seller_login_id}"
        fi
    done < "${BUYER_CSV_PATH}"
fi

umask 077
if [[ "${LISTINGS_CSV_PATH}" != /* ]]; then
    LISTINGS_CSV_PATH="${PWD}/${LISTINGS_CSV_PATH}"
fi
csv_directory="$(dirname "${LISTINGS_CSV_PATH}")"
mkdir -p "${csv_directory}"
temporary_csv="$(mktemp "${LISTINGS_CSV_PATH}.tmp.XXXXXX")"
response_file="$(mktemp "${LISTINGS_CSV_PATH}.response.XXXXXX")"
existing_listings_file=''
completed_count=0

cleanup() {
    if [[ -n "${temporary_csv}" && -f "${temporary_csv}" ]]; then
        if (( completed_count > 0 )); then
            partial_path="${LISTINGS_CSV_PATH}.partial-${RUN_TAG}"
            mv -f "${temporary_csv}" "${partial_path}"
            chmod 600 "${partial_path}"
            printf '일부 매물이 등록됐습니다(%s개). 결과: %s\n' "${completed_count}" "${partial_path}" >&2
        else
            rm -f "${temporary_csv}"
        fi
    fi
    if [[ -n "${response_file}" && -f "${response_file}" ]]; then
        rm -f "${response_file}"
    fi
    if [[ -n "${existing_listings_file}" && -f "${existing_listings_file}" ]]; then
        rm -f "${existing_listings_file}"
    fi
}
trap cleanup EXIT

chmod 600 "${temporary_csv}" "${response_file}"
printf 'listingId,title,category,price\n' > "${temporary_csv}"

http_status() {
    curl --silent --show-error --output "${response_file}" --write-out '%{http_code}' \
        --connect-timeout 10 --max-time 60 "$@"
}

login_payload="{\"loginId\":\"${seller_login_id}\",\"password\":\"${seller_password}\"}"
login_status="$(http_status \
    "${BASE_URL}/api/auth/login" \
    -H 'Content-Type: application/json' \
    --data "${login_payload}")"
if [[ "${login_status}" != 200 ]]; then
    fail "판매자 로그인에 실패했습니다 (HTTP ${login_status})."
fi
seller_token="$(python3 - "${response_file}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as response:
    print(json.load(response)['token'])
PY
)"
if [[ -z "${seller_token}" ]]; then
    fail "로그인 응답에 토큰이 없습니다."
fi

listings_status="$(http_status \
    "${BASE_URL}/api/me/listings" \
    -H "Authorization: Bearer ${seller_token}")"
if [[ "${listings_status}" != 200 ]]; then
    fail "판매자 매물 목록을 불러오지 못했습니다 (HTTP ${listings_status})."
fi
existing_listings_file="$(mktemp "${LISTINGS_CSV_PATH}.existing.XXXXXX")"
cp "${response_file}" "${existing_listings_file}"
chmod 600 "${existing_listings_file}"

categories=(SOFA TABLE DESK CHAIR STORAGE BED)
condition_grades=(S A B C)
width="${#COUNT}"
for ((i = 1; i <= COUNT; i++)); do
    printf -v suffix "%0${width}d" "${i}"
    title="${TITLE_PREFIX} ${suffix}"
    category="${categories[$(((i - 1) % ${#categories[@]}))]}"
    condition_grade="${condition_grades[$(((i - 1) % ${#condition_grades[@]}))]}"
    price=$((50000 + i * 1000))

    if existing_id="$(python3 - "${existing_listings_file}" "${title}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as response:
    items = json.load(response)['items']
matches = [item for item in items if item['title'] == sys.argv[2]]
if not matches:
    print('')
elif len(matches) != 1 or matches[0]['saleStatus'] != 'AVAILABLE':
    print(f"매물명이 중복되었거나 판매 가능 상태가 아닙니다: {sys.argv[2]}", file=sys.stderr)
    sys.exit(2)
else:
    print(matches[0]['id'])
PY
)"; then
        :
    else
        fail "기존 매물 상태를 확인하지 못했습니다. 같은 RUN_TAG의 매물을 점검하세요."
    fi

    if [[ -z "${existing_id}" ]]; then
        request_payload="{\"title\":\"${title}\",\"description\":\"k6 시나리오용 테스트 매물 ${RUN_TAG}\",\"price\":${price},\"category\":\"${category}\",\"conditionGrade\":\"${condition_grade}\",\"dimensions\":{\"widthCm\":100,\"depthCm\":45,\"heightCm\":75}}"
        create_status="$(http_status \
            "${BASE_URL}/api/listings" \
            -H "Authorization: Bearer ${seller_token}" \
            --form "request=${request_payload};type=application/json" \
            --form "images=@${IMAGE_PATH};type=${IMAGE_CONTENT_TYPE}")"
        if [[ "${create_status}" != 201 ]]; then
            response_message="$(cat "${response_file}")"
            fail "매물 등록 실패: ${title} (HTTP ${create_status}) ${response_message}"
        fi
        listing_id="$(python3 - "${response_file}" <<'PY'
import json
import sys

with open(sys.argv[1], encoding='utf-8') as response:
    print(json.load(response)['listingId'])
PY
)"
        if [[ ! "${listing_id}" =~ ^[1-9][0-9]*$ ]]; then
            fail "매물 등록 응답에 유효한 listingId가 없습니다: ${title}"
        fi
    else
        listing_id="${existing_id}"
    fi

    printf '%s,%s,%s,%s\n' "${listing_id}" "${title}" "${category}" "${price}" >> "${temporary_csv}"
    completed_count=$((completed_count + 1))
done

mv -f "${temporary_csv}" "${LISTINGS_CSV_PATH}"
temporary_csv=''
chmod 600 "${LISTINGS_CSV_PATH}"
cleanup
trap - EXIT
printf '판매 가능 매물 %s개를 확인해 %s 에 저장했습니다.\n' "${COUNT}" "${LISTINGS_CSV_PATH}"
