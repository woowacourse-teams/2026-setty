package setty.dispatch.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import setty.dispatch.exception.DispatchStatusTransitionException;

@DisplayName("배차 요청")
class DispatchRequestTest {
    private static DispatchRequest newDispatchRequest() {
        return new DispatchRequest(
                "buyer-token",
                "테스트구매자",
                "010-0000-0001",
                "서울특별시 테스트구 테스트로 1",
                "책상",
                false,
                "https://www.daangn.com/articles/test-1",
                java.util.List.of(),
                null
        );
    }

    private static SellerInput newSellerInput() {
        return new SellerInput(
                "테스트판매자",
                "010-0000-0002",
                "서울특별시 테스트구 테스트로 2",
                "평일 오후"
        );
    }

    @Test
    @DisplayName("생성되면 판매자 입력 대기 상태로 시작한다")
    void startsInSellerInputPending() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.SELLER_INPUT_PENDING);
        assertThat(dispatchRequest.isSellerInputCompleted()).isFalse();
    }

    @Test
    @DisplayName("판매자 입력 완료 여부는 상태가 아니라 실제 입력값으로 판단한다")
    void reportsSellerInputCompletionFromInputNotStatus() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        assertThat(dispatchRequest.isSellerInputCompleted()).isFalse();
        assertThat(dispatchRequest.getSellerInputCompletedAt()).isNull();

        dispatchRequest.completeSellerInput(newSellerInput());

        assertThat(dispatchRequest.isSellerInputCompleted()).isTrue();
        assertThat(dispatchRequest.getSellerInputCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("판매자 입력이 채워지면 최종 검토 대기로 넘어간다")
    void movesToFinalReviewPendingWhenSellerInputIsCompleted() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        dispatchRequest.completeSellerInput(newSellerInput());

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.FINAL_REVIEW_PENDING);
        assertThat(dispatchRequest.isSellerInputCompleted()).isTrue();
        assertThat(dispatchRequest.getSellerInput().getSellerName()).isEqualTo("테스트판매자");
    }

    @Test
    @DisplayName("판매자 입력 대기가 아닌 요청에는 판매자 입력을 다시 채울 수 없다")
    void rejectsSellerInputWhenRequestIsNoLongerWaitingForSeller() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());

        assertThatThrownBy(() -> dispatchRequest.completeSellerInput(newSellerInput()))
                .isInstanceOf(DispatchStatusTransitionException.class);
    }

    @Test
    @DisplayName("DEC-022에 합의된 배차 상태 11개를 모두 선언한다")
    void declaresEveryAgreedDispatchStatus() {
        assertThat(DispatchStatus.values()).hasSize(11);
    }

    @Test
    @DisplayName("최종 검토 대기 요청에 최종 금액을 기록하면 최종 금액 확인 대기로 넘어간다")
    void movesToFinalAmountConfirmPendingWhenFinalAmountIsRecorded() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());

        dispatchRequest.recordFinalAmount(30000);

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING);
        assertThat(dispatchRequest.getFinalQuotedAmount()).isEqualTo(30000);
    }

    @Test
    @DisplayName("최종 금액 확인 대기 동안에는 최종 금액을 계속 수정할 수 있다")
    void allowsEditingFinalAmountWhileConfirmPending() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());
        dispatchRequest.recordFinalAmount(30000);

        dispatchRequest.recordFinalAmount(35000);

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.FINAL_AMOUNT_CONFIRM_PENDING);
        assertThat(dispatchRequest.getFinalQuotedAmount()).isEqualTo(35000);
    }

    @Test
    @DisplayName("판매자 입력이 끝나지 않은 요청에는 최종 금액을 기록할 수 없다")
    void rejectsFinalAmountWhenSellerInputIsNotCompleted() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        assertThatThrownBy(() -> dispatchRequest.recordFinalAmount(30000))
                .isInstanceOf(DispatchStatusTransitionException.class);
    }

    @Test
    @DisplayName("구매자가 최종 금액을 승인하면 배차 대기가 되고 확인 시각이 남는다")
    void movesToDispatchPendingWhenBuyerApprovesFinalAmount() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());
        dispatchRequest.recordFinalAmount(30000);

        dispatchRequest.approveFinalAmount();

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.DISPATCH_PENDING);
        assertThat(dispatchRequest.getAmountCheckedAt()).isNotNull();
    }

    @Test
    @DisplayName("최종 금액 확인 대기가 아닌 요청은 승인할 수 없다")
    void rejectsApprovalWhenAmountIsNotWaitingForConfirmation() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        assertThatThrownBy(dispatchRequest::approveFinalAmount)
                .isInstanceOf(DispatchStatusTransitionException.class);
    }

    @Test
    @DisplayName("구매자가 승인한 뒤에는 최종 금액을 다시 수정할 수 없다")
    void rejectsFinalAmountEditAfterBuyerApproval() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());
        dispatchRequest.recordFinalAmount(30000);
        dispatchRequest.approveFinalAmount();

        assertThatThrownBy(() -> dispatchRequest.recordFinalAmount(35000))
                .isInstanceOf(DispatchStatusTransitionException.class);
    }

    @Test
    @DisplayName("배차 대기 요청을 운영자가 배차 완료로 바꾼다")
    void movesToDispatchCompletedWhenOperatorFinishesDispatch() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());
        dispatchRequest.recordFinalAmount(30000);
        dispatchRequest.approveFinalAmount();

        dispatchRequest.completeDispatch();

        assertThat(dispatchRequest.getStatus()).isEqualTo(DispatchStatus.DISPATCH_COMPLETED);
    }

    @Test
    @DisplayName("배차 대기가 아닌 요청은 배차 완료로 바꿀 수 없다")
    void rejectsCompletionWhenRequestIsNotWaitingForDispatch() {
        final DispatchRequest dispatchRequest = newDispatchRequest();
        dispatchRequest.completeSellerInput(newSellerInput());

        assertThatThrownBy(dispatchRequest::completeDispatch)
                .isInstanceOf(DispatchStatusTransitionException.class);
    }

    @Test
    @DisplayName("안내 문자 기록은 상태와 관계없이 언제든 수정할 수 있다")
    void updatesMessageContentRegardlessOfStatus() {
        final DispatchRequest dispatchRequest = newDispatchRequest();

        dispatchRequest.updateMessageContent("발송 전 초안입니다.");
        assertThat(dispatchRequest.getMessageContent()).isEqualTo("발송 전 초안입니다.");

        dispatchRequest.completeSellerInput(newSellerInput());
        dispatchRequest.recordFinalAmount(30000);
        dispatchRequest.approveFinalAmount();
        dispatchRequest.completeDispatch();

        dispatchRequest.updateMessageContent("배차 완료 후 정정한 문자입니다.");
        assertThat(dispatchRequest.getMessageContent()).isEqualTo("배차 완료 후 정정한 문자입니다.");
    }
}
