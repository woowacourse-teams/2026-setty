package setty.payment.presentation;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import setty.global.auth.LoginMember;
import setty.payment.application.PaymentService;
import setty.payment.domain.Payment;
import setty.platform.member.domain.Member;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final long ORDER_ID = 101L;
    private static final long BUYER_ID = 301L;
    private static final String TOSS_ORDER_ID = ORDER_ID + "_test-token";
    private static final String PAYMENT_KEY = "test-payment-key";
    private static final int AMOUNT = 160_000;

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        final Member buyer = mock(Member.class);
        lenient().when(buyer.getId()).thenReturn(BUYER_ID);
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
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
    void 구매자의_결제를_승인하고_결과를_응답한다() throws Exception {
        when(paymentService.confirm(BUYER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT))
                .thenReturn(Payment.done(ORDER_ID, TOSS_ORDER_ID, PAYMENT_KEY, AMOUNT, LocalDateTime.now()));

        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentKey": "%s", "orderId": "%s", "amount": %d}
                                """.formatted(PAYMENT_KEY, TOSS_ORDER_ID, AMOUNT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(ORDER_ID))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.amount").value(AMOUNT));
    }

    @Test
    void 구매자의_결제_실패를_처리한다() throws Exception {
        mockMvc.perform(post("/api/payments/fail")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "%s"}
                                """.formatted(TOSS_ORDER_ID)))
                .andExpect(status().isNoContent());

        verify(paymentService).fail(BUYER_ID, TOSS_ORDER_ID);
    }

    @Test
    void 결제_키가_없으면_승인하지_않는다() throws Exception {
        mockMvc.perform(post("/api/payments/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId": "%s", "amount": %d}
                                """.formatted(TOSS_ORDER_ID, AMOUNT)))
                .andExpect(status().isBadRequest());
    }
}
