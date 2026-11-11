package setty.payment.infrastructure;

import java.util.Set;
import setty.global.exception.ErrorCode;

/**
 * 토스가 승인을 명확히 거절한 에러 코드를 구매자에게 안내할 거절 종류로 나눈다.
 * 코드 목록은 토스 결제 승인 API 에러 코드표(docs.tosspayments.com/reference/error-codes)를 따른다.
 * 표에 없는 코드는 일반 승인 실패로 본다.
 */
enum TossConfirmRejection {

    // 카드·계좌 쪽에서 거절했다. 다른 결제 수단으로 다시 결제하면 된다.
    PAYMENT_METHOD(ErrorCode.PAYMENT_METHOD_REJECTED, Set.of(
            "REJECT_CARD_PAYMENT", "REJECT_CARD_COMPANY", "REJECT_ACCOUNT_PAYMENT", "INVALID_REJECT_CARD",
            "INVALID_STOPPED_CARD", "INVALID_CARD_LOST_OR_STOLEN", "INVALID_CARD_EXPIRATION", "INVALID_CARD_NUMBER",
            "INVALID_PASSWORD", "FDS_ERROR", "NOT_ALLOWED_POINT_USE", "EXCEED_MAX_AUTH_COUNT",
            "EXCEED_MAX_DAILY_PAYMENT_COUNT", "EXCEED_MAX_PAYMENT_AMOUNT", "EXCEED_MAX_ONE_DAY_AMOUNT",
            "EXCEED_MAX_MONTHLY_PAYMENT_AMOUNT", "EXCEED_MAX_AMOUNT", "EXCEED_MAX_ONE_DAY_WITHDRAW_AMOUNT",
            "EXCEED_MAX_ONE_TIME_WITHDRAW_AMOUNT", "EXCEED_MAX_CARD_INSTALLMENT_PLAN", "INVALID_CARD_INSTALLMENT_PLAN",
            "NOT_SUPPORTED_INSTALLMENT_PLAN_CARD_OR_MERCHANT", "NOT_SUPPORTED_MONTHLY_INSTALLMENT_PLAN",
            "REJECT_TOSSPAY_INVALID_ACCOUNT", "RESTRICTED_TRANSFER_ACCOUNT", "INVALID_ACCOUNT_INFO_RE_REGISTER")),

    // 결제창 인증 후 승인 가능 시간이 지났다. 결제를 처음부터 다시 해야 한다.
    SESSION_EXPIRED(ErrorCode.PAYMENT_SESSION_EXPIRED, Set.of("NOT_FOUND_PAYMENT_SESSION")),

    // 토스·카드사·은행이 잠시 처리하지 못했다. 잠시 뒤 다시 결제하면 된다. (요청 한도 초과 429도 여기에 든다.)
    TEMPORARY(ErrorCode.PAYMENT_TEMPORARILY_UNAVAILABLE, Set.of(
            "PROVIDER_ERROR", "CARD_PROCESSING_ERROR", "NOT_AVAILABLE_PAYMENT", "NOT_AVAILABLE_BANK")),

    // 우리 가맹점 설정(키·인증 형식·계약 정보) 문제다. 구매자가 해결할 수 없으므로 운영자가 바로 봐야 한다.
    MERCHANT_CONFIG(ErrorCode.PAYMENT_CONFIRM_FAILED, Set.of(
            "UNAUTHORIZED_KEY", "INVALID_API_KEY", "INCORRECT_BASIC_AUTH_FORMAT", "FORBIDDEN_REQUEST",
            "NOT_FOUND_TERMINAL_ID", "INVALID_UNREGISTERED_SUBMALL", "NOT_REGISTERED_BUSINESS", "INVALID_AUTHORIZE_AUTH")),

    OTHER(ErrorCode.PAYMENT_CONFIRM_FAILED, Set.of());

    private final ErrorCode errorCode;
    private final Set<String> tossCodes;

    TossConfirmRejection(final ErrorCode errorCode, final Set<String> tossCodes) {
        this.errorCode = errorCode;
        this.tossCodes = tossCodes;
    }

    static TossConfirmRejection of(final int httpStatus, final String tossCode) {
        if (httpStatus == 429) {
            return TEMPORARY;
        }
        for (final TossConfirmRejection rejection : values()) {
            if (rejection.tossCodes.contains(tossCode)) {
                return rejection;
            }
        }
        return OTHER;
    }

    ErrorCode errorCode() {
        return errorCode;
    }

    boolean needsOperator() {
        return this == MERCHANT_CONFIG;
    }
}
