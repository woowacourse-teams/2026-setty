#!/usr/bin/env bash
set -euo pipefail

script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
cd "$repo_root"

if ! command -v k6 >/dev/null 2>&1; then
    printf 'k6 명령을 찾을 수 없습니다. 먼저 k6를 설치하세요.\n' >&2
    exit 127
fi

mkdir -p .local
umask 077
log_dir=$(mktemp -d "$repo_root/.local/k6-run.XXXXXX")
cleanup() {
    status=$?
    if [[ "$status" -eq 0 ]]; then
        rm -rf "$log_dir"
    else
        printf '실패 로그를 보존했습니다: %s\n' "$log_dir" >&2
    fi
}
trap cleanup EXIT

k6_run() {
    local phase=$1
    local -a args=()
    local name
    for name in APP_URL API_BASE_URL USERS_CSV LISTINGS_CSV PAYMENT_TIMEOUT_MS POSTHOG_TIMEOUT_MS ALLOW_NON_DEV_API ANALYTICS_SMOKE PURCHASE_SMOKE; do
        if [[ -v $name ]]; then
            args+=(--env "$name=${!name}")
        fi
    done
    args+=(--env "K6_BROWSER_HEADLESS=${K6_BROWSER_HEADLESS:-false}")
    if [[ "$phase" != 'smoke' && "$phase" != 'purchase_smoke' ]]; then
        args+=(--env "K6_PHASE=$phase")
    fi
    K6_BROWSER_HEADLESS="${K6_BROWSER_HEADLESS:-false}" k6 run --quiet "${args[@]}" "$script_dir/scenario.js"
}

if [[ "${ANALYTICS_SMOKE:-false}" == 'true' ]]; then
    k6_run smoke
    exit
fi

if [[ "${PURCHASE_SMOKE:-false}" == 'true' ]]; then
    printf '\n=== 단건 결제 스모크 단계 ===\n'
    k6_run purchase_smoke 2>&1 | tee "$log_dir/purchase_smoke.log"
    exit
fi

run_phase() {
    phase=$1
    printf '\n=== %s 단계 ===\n' "$phase"
    k6_run "$phase" 2>&1 | tee "$log_dir/$phase.log"
}

summary_metric() {
    log_file=$1
    label=$2
    awk -F': ' -v expected="$label" '$1 == expected { value = $2; sub(/[^0-9].*$/, "", value) } END { if (value == "") exit 1; print value }' "$log_file"
}

run_phase pre_purchase
run_phase purchase

pre_log="$log_dir/pre_purchase.log"
purchase_log="$log_dir/purchase.log"
wau=$(( $(summary_metric "$pre_log" 'WAU (상품을 확인한 고유 계정)') + $(summary_metric "$purchase_log" 'WAU (상품을 확인한 고유 계정)') ))
product_views=$(( $(summary_metric "$pre_log" '상품 조회 이벤트') + $(summary_metric "$purchase_log" '상품 조회 이벤트') ))
checkout_entries=$(( $(summary_metric "$pre_log" '결제창 진입') + $(summary_metric "$purchase_log" '결제창 진입') ))
checkout_abandons=$(summary_metric "$pre_log" '결제창 이탈')
reconfirm_users=$(summary_metric "$pre_log" '재확인 사용자')
successful_payments=$(summary_metric "$purchase_log" '결제 완료')
verified_events=$(( $(summary_metric "$pre_log" 'PostHog HTTP 2xx로 확인한 이벤트') + $(summary_metric "$purchase_log" 'PostHog HTTP 2xx로 확인한 이벤트') ))
wau_conversion=$(awk -v numerator="$successful_payments" -v denominator="$wau" 'BEGIN { printf "%.1f%%", 100 * numerator / denominator }')
checkout_conversion=$(awk -v numerator="$successful_payments" -v denominator="$checkout_entries" 'BEGIN { printf "%.1f%%", 100 * numerator / denominator }')

printf '\n=== 전체 시나리오 결과 ===\n'
printf 'WAU (상품을 확인한 고유 계정): %s\n' "$wau"
printf '상품 조회 이벤트: %s\n' "$product_views"
printf '결제창 진입: %s\n' "$checkout_entries"
printf '결제창 이탈: %s\n' "$checkout_abandons"
printf '재확인 사용자: %s\n' "$reconfirm_users"
printf '결제 완료: %s\n' "$successful_payments"
printf 'PostHog HTTP 2xx로 확인한 이벤트: %s\n' "$verified_events"
printf 'WAU 대비 최종 결제 전환율: %s\n' "$wau_conversion"
printf '결제창 진입 대비 결제 전환율: %s\n' "$checkout_conversion"
printf 'PostHog 집계는 동일한 기간과 k6 테스트 계정(loginId k6user*)으로 필터링해 대조하세요.\n'
