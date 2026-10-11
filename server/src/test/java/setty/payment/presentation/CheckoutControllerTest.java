package setty.payment.presentation;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import setty.payment.application.PaymentService;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    private static final String CLIENT_REDIRECT_URL = "https://www.setty.cloud";
    private static final String TOSS_ORDER_ID = "101_test-token";
    private static final String FALLBACK_ORDER_ID = "102_test-token";

    @Mock
    private PaymentService paymentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CheckoutController(paymentService, CLIENT_REDIRECT_URL)
        ).build();
    }

    @Test
    void 토스가_orderId를_생략한_취소_복귀는_fallback을_사용해_실패_처리하고_프런트로_돌아간다() throws Exception {
        mockMvc.perform(get("/payments/fail")
                        .queryParam("code", "PAY_PROCESS_CANCELED")
                        .queryParam("fallbackOrderId", FALLBACK_ORDER_ID))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        CLIENT_REDIRECT_URL + "?payment=fail&orderId=" + FALLBACK_ORDER_ID
                                + "&reason=PAY_PROCESS_CANCELED"
                ));

        verify(paymentService).fail(FALLBACK_ORDER_ID);
    }

    @Test
    void 토스가_전달한_orderId가_있으면_fallback보다_우선한다() throws Exception {
        mockMvc.perform(get("/payments/fail")
                        .queryParam("code", "PAY_PROCESS_ABORTED")
                        .queryParam("orderId", TOSS_ORDER_ID)
                        .queryParam("fallbackOrderId", FALLBACK_ORDER_ID))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        CLIENT_REDIRECT_URL + "?payment=fail&orderId=" + TOSS_ORDER_ID
                                + "&reason=PAY_PROCESS_ABORTED"
                ));

        verify(paymentService).fail(TOSS_ORDER_ID);
    }

    @Test
    void orderId가_모두_없어도_실패_화면으로_돌아가고_실패_이벤트는_발행하지_않는다() throws Exception {
        mockMvc.perform(get("/payments/fail").queryParam("code", "PAY_PROCESS_CANCELED"))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        CLIENT_REDIRECT_URL + "?payment=fail&reason=PAY_PROCESS_CANCELED"
                ));

        verifyNoInteractions(paymentService);
    }
}
