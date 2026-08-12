package setty.dispatch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import setty.dispatch.domain.DispatchRequest;
import setty.dispatch.domain.SellerInputSession;
import setty.dispatch.dto.seller.SellerInputSubmitRequest;
import setty.dispatch.event.SellerInputCompletedEvent;
import setty.dispatch.exception.SellerInputAlreadySubmittedException;
import setty.dispatch.repository.SellerInputSessionRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("판매자 입력 서비스")
class SellerInputServiceTest {
    private static final String TOKEN = "test-seller-token";

    @Mock
    private SellerInputSessionRepository sellerInputSessionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<SellerInputCompletedEvent> eventCaptor;

    @Test
    @DisplayName("판매자 입력이 완료되면 운영자 알림 이벤트를 발행한다")
    void publishesEventWhenSellerInputCompleted() {
        when(sellerInputSessionRepository.findByToken(TOKEN)).thenReturn(Optional.of(session()));
        final SellerInputService service = new SellerInputService(sellerInputSessionRepository, eventPublisher);

        service.submit(TOKEN, submitRequest());

        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().itemType()).isEqualTo("책상");
        assertThat(eventCaptor.getValue().itemImageCount()).isEqualTo(1);
        assertThat(eventCaptor.getValue().sellerInputCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 제출된 링크로 다시 제출하면 알림 이벤트를 다시 발행하지 않는다")
    void doesNotPublishEventOnDuplicateSubmit() {
        final SellerInputSession session = session();
        when(sellerInputSessionRepository.findByToken(TOKEN)).thenReturn(Optional.of(session));
        final SellerInputService service = new SellerInputService(sellerInputSessionRepository, eventPublisher);
        service.submit(TOKEN, submitRequest());

        assertThatThrownBy(() -> service.submit(TOKEN, submitRequest()))
                .isInstanceOf(SellerInputAlreadySubmittedException.class);

        verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
    }

    private SellerInputSession session() {
        return new SellerInputSession(TOKEN, dispatchRequest());
    }

    private DispatchRequest dispatchRequest() {
        return new DispatchRequest(
                "test-buyer-token",
                "테스트구매자",
                "01000000001",
                "서울특별시 테스트구 테스트로 1",
                "책상",
                false,
                "https://www.daangn.com/articles/test-1",
                List.of("https://techcourse-project-2026.s3.ap-northeast-2.amazonaws.com/setty/images/items/test-1.jpg"),
                null
        );
    }

    private SellerInputSubmitRequest submitRequest() {
        return new SellerInputSubmitRequest(
                "테스트판매자",
                "010-0000-0002",
                "서울특별시 테스트구 테스트로 2",
                "평일 오후"
        );
    }
}
