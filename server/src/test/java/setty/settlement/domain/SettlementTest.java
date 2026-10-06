package setty.settlement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import setty.global.exception.BusinessException;

class SettlementTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-06T01:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-10-09T01:00:00Z");

    @Test
    void 판매자_정산은_물품대금으로_대기_상태로_생성된다() {
        final Settlement settlement = Settlement.seller(1L, 10L, 100L, 150_000, CREATED_AT);

        assertThat(settlement.getPayeeType()).isEqualTo(PayeeType.SELLER);
        assertThat(settlement.getPayeeId()).isEqualTo(10L);
        assertThat(settlement.getListingId()).isEqualTo(100L);
        assertThat(settlement.getAmount()).isEqualTo(150_000);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    void 기사_정산은_배송비로_대기_상태로_생성된다() {
        final Settlement settlement = Settlement.driver(1L, 20L, 10_000, CREATED_AT);

        assertThat(settlement.getPayeeType()).isEqualTo(PayeeType.DRIVER);
        assertThat(settlement.getPayeeId()).isEqualTo(20L);
        assertThat(settlement.getListingId()).isNull();
        assertThat(settlement.getAmount()).isEqualTo(10_000);
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.PENDING);
    }

    @Test
    void 받는_사람이나_금액이_올바르지_않으면_생성할_수_없다() {
        assertThatThrownBy(() -> Settlement.seller(1L, null, 100L, 150_000, CREATED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Settlement.seller(1L, 10L, null, 150_000, CREATED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Settlement.driver(1L, 20L, -1, CREATED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Settlement.driver(0L, 20L, 10_000, CREATED_AT))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 판매_완료되면_정산이_확정된다() {
        final Settlement settlement = Settlement.driver(1L, 20L, 10_000, CREATED_AT);

        settlement.apply(SettlementDecision.completed(DECIDED_AT));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.getConfirmedAt()).isEqualTo(DECIDED_AT);
        assertThat(settlement.getCancelledAt()).isNull();
    }

    @Test
    void 주문이_취소되면_정산이_취소된다() {
        final Settlement settlement = Settlement.seller(1L, 10L, 100L, 150_000, CREATED_AT);

        settlement.apply(SettlementDecision.cancelled(DECIDED_AT));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CANCELLED);
        assertThat(settlement.getCancelledAt()).isEqualTo(DECIDED_AT);
        assertThat(settlement.getConfirmedAt()).isNull();
    }

    @Test
    void 결론이_난_정산은_바뀌지_않는다() {
        final Settlement settlement = Settlement.seller(1L, 10L, 100L, 150_000, CREATED_AT);
        settlement.apply(SettlementDecision.completed(DECIDED_AT));

        settlement.apply(SettlementDecision.cancelled(DECIDED_AT.plusSeconds(60)));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.CONFIRMED);
        assertThat(settlement.getCancelledAt()).isNull();
    }

    @Test
    void 결론은_확정이나_취소만_가능하다() {
        assertThatThrownBy(() -> new SettlementDecision(SettlementStatus.PENDING, DECIDED_AT))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> SettlementDecision.completed(null))
                .isInstanceOf(BusinessException.class);
    }
}
