package setty.estimate.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import setty.estimate.application.OperatorEstimateRequestService;
import setty.estimate.application.command.RecordManualNotificationCommand;
import setty.estimate.application.exception.EstimateRequestNotFoundException;
import setty.estimate.application.query.EstimateRequestDetail;
import setty.estimate.application.query.EstimateRequestSummary;
import setty.estimate.application.query.ManualNotificationResult;
import setty.estimate.domain.EstimateRequestStatus;
import setty.estimate.domain.InvalidEstimateRequestStatusException;

@ExtendWith(MockitoExtension.class)
class OperatorEstimateRequestControllerTest {
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.of(
            2026,
            8,
            6,
            10,
            0,
            0,
            0,
            ZoneOffset.ofHours(9)
    );

    @Mock
    private OperatorEstimateRequestService operatorEstimateRequestService;

    @Captor
    private ArgumentCaptor<RecordManualNotificationCommand> commandCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OperatorEstimateRequestController(operatorEstimateRequestService)
                )
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void returnsLatestEstimateRequestSummariesWithoutPersonalInformation() throws Exception {
        when(operatorEstimateRequestService.findAll()).thenReturn(List.of(new EstimateRequestSummary(
                1L,
                "서울성북구",
                "원목의자",
                false,
                EstimateRequestStatus.PENDING_REVIEW,
                CREATED_AT
        )));

        mockMvc.perform(get("/api/operator/estimate-requests"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estimateRequestId").value(1))
                .andExpect(jsonPath("$[0].tradeArea").value("서울성북구"))
                .andExpect(jsonPath("$[0].name").doesNotExist())
                .andExpect(jsonPath("$[0].phoneNumber").doesNotExist());
    }

    @Test
    void returnsEstimateRequestDetailWithoutEstimatedAmountAndNotifiedAt() throws Exception {
        when(operatorEstimateRequestService.findById(1L)).thenReturn(new EstimateRequestDetail(
                1L,
                "테스트사용자",
                "01000000000",
                "서울성북구",
                "원목의자",
                false,
                "https://www.daangn.com/articles/test-1",
                EstimateRequestStatus.ESTIMATE_NOTIFIED,
                CREATED_AT,
                CREATED_AT,
                "2026-08-06",
                new ManualNotificationResult("예상 운송비는 30000원입니다.", true)
        ));

        mockMvc.perform(get("/api/operator/estimate-requests/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("테스트사용자"))
                .andExpect(jsonPath("$.phoneNumber").value("010-0000-0000"))
                .andExpect(jsonPath("$.productLink").value("https://www.daangn.com/articles/test-1"))
                .andExpect(jsonPath("$.privacyConsentedAt").isNotEmpty())
                .andExpect(jsonPath("$.privacyPolicyVersion").value("2026-08-06"))
                .andExpect(jsonPath("$.manualNotification.messageContent").value("예상 운송비는 30000원입니다."))
                .andExpect(jsonPath("$.manualNotification.transportFeasible").value(true))
                .andExpect(jsonPath("$.manualNotification.estimatedAmount").doesNotExist())
                .andExpect(jsonPath("$.manualNotification.notifiedAt").doesNotExist());
    }

    @Test
    void returnsNotFoundWhenTheEstimateRequestDoesNotExist() throws Exception {
        when(operatorEstimateRequestService.findById(99L)).thenThrow(new EstimateRequestNotFoundException());

        mockMvc.perform(get("/api/operator/estimate-requests/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ESTIMATE_REQUEST_NOT_FOUND"));
    }

    @Test
    void recordsManualNotificationAfterTheOperatorSendsTheMessage() throws Exception {
        mockMvc.perform(put("/api/operator/estimate-requests/1/manual-notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageContent": "예상 운송비는 30000원입니다.",
                                  "transportFeasible": true
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(operatorEstimateRequestService).recordManualNotification(eq(1L), commandCaptor.capture());
        final RecordManualNotificationCommand command = commandCaptor.getValue();
        org.assertj.core.api.Assertions.assertThat(command.messageContent()).isEqualTo("예상 운송비는 30000원입니다.");
        org.assertj.core.api.Assertions.assertThat(command.transportFeasible()).isTrue();
    }

    @Test
    void rejectsManualNotificationWithoutMessageContent() throws Exception {
        mockMvc.perform(put("/api/operator/estimate-requests/1/manual-notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageContent": "",
                                  "transportFeasible": false
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.messageContent").exists());
    }

    @Test
    void rejectsManualNotificationWhenTheStatusDoesNotAllowIt() throws Exception {
        doThrow(new InvalidEstimateRequestStatusException())
                .when(operatorEstimateRequestService)
                .recordManualNotification(eq(1L), any(RecordManualNotificationCommand.class));

        mockMvc.perform(put("/api/operator/estimate-requests/1/manual-notification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageContent": "이미 안내한 내용입니다.",
                                  "transportFeasible": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ESTIMATE_REQUEST_STATUS"));
    }
}
