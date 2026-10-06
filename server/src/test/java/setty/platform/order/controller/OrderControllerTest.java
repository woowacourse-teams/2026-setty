package setty.platform.order.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import setty.global.auth.LoginMember;
import setty.platform.member.domain.Member;
import setty.platform.order.controller.dto.OrderCancellationResponse;
import setty.platform.order.service.OrderCompletionService;
import setty.platform.order.service.OrderService;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    private static final long ORDER_ID = 101L;
    private static final long BUYER_ID = 301L;

    @Mock
    private OrderService orderService;

    @Mock
    private OrderCompletionService orderCompletionService;

    private OrderController orderController;

    private MockMvc mockMvc;

    private Member buyer;

    @BeforeEach
    void setUp() {
        buyer = mock(Member.class);
        when(buyer.getId()).thenReturn(BUYER_ID);
        orderController = new OrderController(orderService, orderCompletionService);
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(final MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(LoginMember.class);
                    }

                    @Override
                    public Object resolveArgument(
                            final MethodParameter parameter,
                            final ModelAndViewContainer container,
                            final NativeWebRequest request,
                            final WebDataBinderFactory binderFactory
                    ) {
                        return buyer;
                    }
                })
                .build();
    }

    @Test
    void 취소_API는_202와_취소_대기_응답을_반환한다() throws Exception {
        when(orderService.requestCancellation(ORDER_ID, BUYER_ID))
                .thenReturn(new OrderCancellationResponse(ORDER_ID, "CANCEL_PENDING", "취소 대기 중"));

        mockMvc.perform(post("/api/orders/{id}/cancellations", ORDER_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.orderStatus").value("CANCEL_PENDING"))
                .andExpect(jsonPath("$.message").value("취소 대기 중"));
        verify(orderService).requestCancellation(ORDER_ID, BUYER_ID);
    }

    @Test
    void 판매_완료_확인_API는_204를_반환한다() throws Exception {
        mockMvc.perform(post("/api/orders/{id}/completion", ORDER_ID))
                .andExpect(status().isNoContent());
        verify(orderCompletionService).confirmByBuyer(ORDER_ID, BUYER_ID);
    }
}
