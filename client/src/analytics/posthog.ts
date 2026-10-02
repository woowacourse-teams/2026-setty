import posthog from 'posthog-js';

/**
 * 사용자 행동 수집(PostHog).
 *
 * 빌드 환경변수 SETTY_POSTHOG_KEY가 있을 때만 초기화한다. 값이 없는 로컬·테스트 빌드에서는
 * 아래 함수들이 모두 아무 일도 하지 않는다. 세션 리플레이는 PostHog 프로젝트 설정에서 켠다.
 *
 * 수집 범위: 경로 변경마다 $pageview, 자동 클릭 수집, 커스텀 이벤트 4종, 로그인 아이디 identify.
 * 비밀번호·연락처·주소 같은 입력값은 리플레이에서 마스킹하고 이벤트 속성에는 넣지 않는다.
 */

const KEY_PATTERN = /^phc_[A-Za-z0-9]+$/;

let initialized = false;

export type AnalyticsEventName =
    | 'checkout_opened'
    | 'checkout_closed'
    | 'payment_succeeded'
    | 'payment_failed';

type EventProperties = Record<string, string | number | boolean>;

function getKey(): string | null {
    const key = __POSTHOG_KEY__.trim();
    return KEY_PATTERN.test(key) ? key : null;
}

export function initializeAnalytics() {
    if (initialized) return true;
    const key = getKey();
    if (!key) return false;

    posthog.init(key, {
        api_host: __POSTHOG_HOST__,
        // React Router의 history 변경마다 $pageview를 보낸다(SPA).
        capture_pageview: 'history_change',
        capture_pageleave: true,
        // 익명 방문자는 사람 프로필을 만들지 않고, 로그인(identify) 후부터 사람으로 묶는다.
        person_profiles: 'identified_only',
        // 리플레이에서 모든 입력값을 마스킹한다.
        session_recording: {
            maskAllInputs: true
        }
    });
    initialized = true;
    return true;
}

export function trackEvent(name: AnalyticsEventName, properties: EventProperties = {}) {
    if (!initializeAnalytics()) return;
    posthog.capture(name, properties);
}

/** 로그인 성공 시 아이디로 사람을 식별한다. 같은 기기를 여러 명이 쓸 때 세션을 가르기 위해서다. */
export function identifyUser(loginId: string) {
    if (!initializeAnalytics()) return;
    posthog.identify(loginId);
}

/** 로그아웃 시 식별을 끊어 다음 사용자가 이전 사람에 묶이지 않게 한다. */
export function resetUser() {
    if (!initialized) return;
    posthog.reset();
}
