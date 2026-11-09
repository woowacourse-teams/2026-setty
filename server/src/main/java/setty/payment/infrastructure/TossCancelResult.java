package setty.payment.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/** 토스 결제 취소({@code POST /v1/payments/{paymentKey}/cancel}) 결과. */
public sealed interface TossCancelResult {

    record Canceled(String status, List<Cancel> cancels) implements TossCancelResult {

        public int canceledAmount() {
            return doneCancels().stream().mapToInt(Cancel::cancelAmount).sum();
        }

        public boolean isFullyCanceled(final int amount) {
            return "CANCELED".equals(status) && canceledAmount() == amount;
        }

        public String lastTransactionKey() {
            final List<Cancel> done = doneCancels();
            return done.isEmpty() ? null : done.getLast().transactionKey();
        }

        public Instant lastCanceledAt() {
            final List<Cancel> done = doneCancels();
            if (done.isEmpty() || done.getLast().canceledAt() == null) {
                return null;
            }
            return OffsetDateTime.parse(done.getLast().canceledAt()).toInstant();
        }

        private List<Cancel> doneCancels() {
            if (cancels == null) {
                return List.of();
            }
            return cancels.stream()
                    .filter(cancel -> "DONE".equals(cancel.cancelStatus()) && cancel.cancelAmount() != null)
                    .toList();
        }
    }

    record Cancel(Integer cancelAmount, String canceledAt, String transactionKey, String cancelStatus) {
    }

    // 토스 응답 기준으로 이미 전액 취소된 결제(ALREADY_CANCELED_PAYMENT).
    record AlreadyCanceled() implements TossCancelResult {
    }

    // 응답을 못 받았거나 토스가 처리 중·일시 오류라고 답했다. 같은 멱등키로 다시 보낸다.
    record Retryable(String errorCode) implements TossCancelResult {
    }

    // 다시 보내도 결과가 같은 거절. 사람이 확인한다.
    record Rejected(String errorCode) implements TossCancelResult {
    }
}
