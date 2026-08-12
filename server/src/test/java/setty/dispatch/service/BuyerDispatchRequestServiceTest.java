package setty.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import setty.common.web.FrontProperties;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.DispatchStatus;
import setty.dispatch.domain.SellerInputSession;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateRequest;
import setty.dispatch.dto.buyer.BuyerDispatchRequestCreateResponse;
import setty.dispatch.repository.DispatchRequestRepository;
import setty.dispatch.repository.SellerInputSessionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("구매자 배차 요청 서비스")
class BuyerDispatchRequestServiceTest {
    @Mock
    private DispatchRequestRepository dispatchRequestRepository;

    @Mock
    private SellerInputSessionRepository sellerInputSessionRepository;

    @Captor
    private ArgumentCaptor<DispatchRequest> dispatchRequestCaptor;

    @Test
    @DisplayName("판매자 입력을 기다리는 요청을 저장하고 판매자 입력 URL을 돌려준다")
    void savesRequestPendingSellerInputAndReturnsSellerInputUrl() {
        when(dispatchRequestRepository.save(any(DispatchRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(sellerInputSessionRepository.save(any(SellerInputSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        final BuyerDispatchRequestService service = new BuyerDispatchRequestService(
                dispatchRequestRepository,
                sellerInputSessionRepository,
                new SellerInputUrlFactory(new FrontProperties("https://setty.test"))
        );

        final BuyerDispatchRequestCreateResponse response = service.create(new BuyerDispatchRequestCreateRequest(
                "테스트구매자",
                "010-0000-0001",
                "서울특별시 테스트구 테스트로 1",
                "책상",
                false,
                "https://www.daangn.com/articles/test-1",
                List.of("https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/setty/images/items/test-1.jpg"),
                null
        ));

        assertThat(response.sellerInputUrl()).startsWith("https://setty.test/seller-input/");
        assertThat(response.buyerToken()).isNotBlank();
        verify(dispatchRequestRepository).save(dispatchRequestCaptor.capture());
        assertThat(dispatchRequestCaptor.getValue().getStatus()).isEqualTo(DispatchStatus.SELLER_INPUT_PENDING);
    }
}
