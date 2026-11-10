package setty.payment.infrastructure;

/** 토스 API 에러 응답 본문({@code {code, message}}). 판단에는 code만 쓴다. */
record TossErrorResponse(String code, String message) {
}
