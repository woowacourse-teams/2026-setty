package setty.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

class TossPaymentStatusTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @ParameterizedTest
    @CsvSource({"DONE, DONE", "WAITING_FOR_DEPOSIT, WAITING_FOR_DEPOSIT", "ABORTED, ABORTED",
            "NEW_STATUS, UNKNOWN", "done, UNKNOWN", "UNKNOWN, UNKNOWN"})
    void 토스_상태_문자열을_번역하고_모르는_값은_UNKNOWN으로_둔다(final String raw, final TossPaymentStatus expected) {
        assertThat(TossPaymentStatus.from(raw)).isEqualTo(expected);
    }

    @Test
    void 응답의_모르는_상태는_역직렬화에_실패하지_않고_승인도_미승인도_아니다() {
        final TossConfirmResult result = jsonMapper.readValue("""
                {"paymentKey":"pk","orderId":"1_a","status":"NEW_STATUS","totalAmount":1000}
                """, TossConfirmResult.class);

        assertThat(result.status()).isEqualTo(TossPaymentStatus.UNKNOWN);
        assertThat(result.isDoneFor("1_a", 1000)).isFalse();
        assertThat(result.isNotApproved()).isFalse();
    }

    @Test
    void 응답에_상태가_없으면_UNKNOWN이다() {
        final TossConfirmResult result = jsonMapper.readValue("""
                {"paymentKey":"pk","orderId":"1_a","totalAmount":1000}
                """, TossConfirmResult.class);

        assertThat(result.status()).isEqualTo(TossPaymentStatus.UNKNOWN);
    }
}
