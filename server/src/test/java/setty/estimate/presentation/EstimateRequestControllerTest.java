package setty.estimate.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
import setty.estimate.application.EstimateRequestCreateService;
import setty.estimate.application.command.CreateEstimateRequestCommand;
import setty.estimate.application.result.CreatedEstimateRequest;
import setty.estimate.domain.EstimateRequestStatus;

@ExtendWith(MockitoExtension.class)
class EstimateRequestControllerTest {
    @Mock
    private EstimateRequestCreateService estimateRequestCreateService;

    @Captor
    private ArgumentCaptor<CreateEstimateRequestCommand> commandCaptor;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new EstimateRequestController(estimateRequestCreateService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void createsAnEstimateRequestWithNormalizedPhoneNumber() throws Exception {
        when(estimateRequestCreateService.create(any(CreateEstimateRequestCommand.class)))
                .thenReturn(new CreatedEstimateRequest(
                        1L,
                        EstimateRequestStatus.PENDING_REVIEW,
                        OffsetDateTime.of(2026, 8, 6, 10, 0, 0, 0, ZoneOffset.ofHours(9))
                ));

        mockMvc.perform(post("/api/estimate-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트사용자",
                                  "phoneNumber": "010-0000-0000",
                                  "tradeArea": "서울성북구",
                                  "itemType": "원목의자",
                                  "highValueItem": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estimateRequestId").value(1))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        verify(estimateRequestCreateService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().phoneNumber()).isEqualTo("01000000000");
        assertThat(commandCaptor.getValue().productLink()).isNull();
    }

    @Test
    void createsAnEstimateRequestWithOptionalProductLink() throws Exception {
        when(estimateRequestCreateService.create(any(CreateEstimateRequestCommand.class)))
                .thenReturn(new CreatedEstimateRequest(
                        1L,
                        EstimateRequestStatus.PENDING_REVIEW,
                        OffsetDateTime.of(2026, 8, 6, 10, 0, 0, 0, ZoneOffset.ofHours(9))
                ));

        mockMvc.perform(post("/api/estimate-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트사용자",
                                  "phoneNumber": "010-0000-0000",
                                  "tradeArea": "서울성북구",
                                  "itemType": "원목의자",
                                  "highValueItem": false,
                                  "productLink": "https://www.daangn.com/articles/test-1"
                                }
                                """))
                .andExpect(status().isCreated());

        verify(estimateRequestCreateService).create(commandCaptor.capture());
        assertThat(commandCaptor.getValue().productLink())
                .isEqualTo("https://www.daangn.com/articles/test-1");
    }

    @Test
    void rejectsProductLinkLongerThanFiveHundredCharacters() throws Exception {
        final String tooLongLink = "https://www.daangn.com/articles/" + "a".repeat(501);

        mockMvc.perform(post("/api/estimate-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트사용자",
                                  "phoneNumber": "010-0000-0000",
                                  "tradeArea": "서울성북구",
                                  "itemType": "원목의자",
                                  "highValueItem": false,
                                  "productLink": "%s"
                                }
                                """.formatted(tooLongLink)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.productLink").exists());
    }

    @Test
    void rejectsInvalidInputWithoutCallingTheService() throws Exception {
        mockMvc.perform(post("/api/estimate-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "테스트 사용자",
                                  "phoneNumber": "010-0000-000",
                                  "tradeArea": "",
                                  "itemType": "",
                                  "highValueItem": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phoneNumber").exists())
                .andExpect(jsonPath("$.fieldErrors.tradeArea").exists())
                .andExpect(jsonPath("$.fieldErrors.itemType").exists())
                .andExpect(jsonPath("$.fieldErrors.highValueItem").exists());
    }
}
