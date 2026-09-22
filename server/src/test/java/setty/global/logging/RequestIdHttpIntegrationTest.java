package setty.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import setty.delivery.auth.application.DeliveryMemberRepository;
import setty.global.auth.AuthInterceptor;
import setty.global.auth.LoginDeliveryMemberArgumentResolver;
import setty.global.auth.LoginMemberArgumentResolver;
import setty.global.auth.WebConfig;
import setty.global.exception.BusinessException;
import setty.global.exception.ErrorCode;
import setty.global.exception.GlobalExceptionHandler;
import setty.payment.application.PaymentService;
import setty.payment.presentation.CheckoutController;
import setty.platform.member.repository.MemberRepository;

@ExtendWith(OutputCaptureExtension.class)
class RequestIdHttpIntegrationTest {

    private static AnnotationConfigServletWebServerApplicationContext context;
    private static HttpClient client;
    private static Logger rootLogger;
    private static ListAppender<ILoggingEvent> logs;
    private static String baseUrl;

    @BeforeAll
    static void startServer() {
        context = (AnnotationConfigServletWebServerApplicationContext) new SpringApplicationBuilder(TestConfig.class)
                .web(WebApplicationType.SERVLET)
                .registerShutdownHook(false)
                .run("--spring.main.banner-mode=off");
        baseUrl = "http://127.0.0.1:" + context.getWebServer().getPort();
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logs = new ListAppender<>() {
            @Override
            protected void append(final ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        logs.list = new CopyOnWriteArrayList<>();
        logs.start();
        rootLogger.addAppender(logs);
    }

    @BeforeEach
    void clearLogsAndPaymentMock() {
        logs.list.clear();
        reset(context.getBean(PaymentService.class));
    }

    @AfterAll
    static void stopServer() {
        if (rootLogger != null) {
            rootLogger.detachAppender(logs);
            logs.stop();
        }
        if (client != null) {
            client.close();
        }
        if (context != null) {
            context.close();
        }
    }

    @Test
    void connectsResponseToTextAndJsonLogs(final CapturedOutput output) throws Exception {
        final HttpResponse<String> response = get("/api/listings/request-id");
        final String requestId = requestId(response);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(requestId);
        assertThat(output.getAll().lines()).anyMatch(line ->
                line.contains("request-id-probe") && line.contains("[requestId=" + requestId + "]"));

        final ILoggingEvent event = logEvent("request-id-probe");
        final StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.setFormat("logstash");
        encoder.start();
        try {
            final String json = new String(encoder.encode(event), StandardCharsets.UTF_8);
            assertThat(JsonParserFactory.getJsonParser().parseMap(json))
                    .containsEntry("requestId", requestId)
                    .containsEntry("message", "request-id-probe");
        } finally {
            encoder.stop();
        }
    }

    @Test
    void addsHeaderBeforeAuthenticationRejectsRequest() throws Exception {
        final HttpResponse<String> response = get("/api/protected");

        assertThat(response.statusCode()).isEqualTo(401);
        requestId(response);
        assertThat(JsonParserFactory.getJsonParser().parseMap(response.body()))
                .containsOnlyKeys("code", "message")
                .containsEntry("code", "INVALID_TOKEN");
    }

    @Test
    void preservesBusinessAndInvalidInputErrorResponses() throws Exception {
        final HttpResponse<String> businessError = get("/api/listings/business-error");
        final HttpResponse<String> invalidInput = get("/api/listings/invalid-input?amount=invalid");

        for (final HttpResponse<String> response : List.of(businessError, invalidInput)) {
            assertThat(response.statusCode()).isEqualTo(400);
            requestId(response);
            assertThat(JsonParserFactory.getJsonParser().parseMap(response.body()))
                    .containsOnlyKeys("code", "message");
        }
        assertThat(requestId(businessError)).isNotEqualTo(requestId(invalidInput));
    }

    @Test
    void connectsUnexpectedErrorResponseToExistingErrorLog() throws Exception {
        final HttpResponse<String> response = get("/api/listings/unexpected-error");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(JsonParserFactory.getJsonParser().parseMap(response.body()))
                .containsOnlyKeys("code", "message")
                .containsEntry("code", "INTERNAL_ERROR");
        assertThat(logEvent("예상치 못한 오류").getMDCPropertyMap())
                .containsEntry(RequestIdFilter.MDC_KEY, requestId(response));
    }

    @Test
    void coversPaymentRedirectOutsideApiWithoutLoggingPaymentKey() throws Exception {
        doThrow(new BusinessException(ErrorCode.PAYMENT_CONFIRM_FAILED))
                .when(context.getBean(PaymentService.class)).confirm("17_synthetic", "synthetic-key", 1000);

        final HttpResponse<String> response = get(
                "/payments/success?paymentKey=synthetic-key&orderId=17_synthetic&amount=1000");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location")).hasValue(
                "http://localhost/result?payment=fail&orderId=17_synthetic&reason=PAYMENT_CONFIRM_FAILED");
        assertThat(logEvent("결제 승인 처리에 실패했습니다. orderId={}, code={}").getMDCPropertyMap())
                .containsEntry(RequestIdFilter.MDC_KEY, requestId(response));
        assertThat(logs.list).noneMatch(event -> event.getFormattedMessage().contains("synthetic-key"));
    }

    @Test
    void reusesIdOnContainerAsyncDispatchWithoutPropagatingToBackgroundTask() throws Exception {
        final HttpResponse<String> response = get("/api/listings/async");
        final String requestId = requestId(response);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(requestId + ":" + requestId + ":ASYNC:none");
    }

    @Test
    void reusesIdWhenContainerDispatchesToErrorPage() throws Exception {
        final HttpResponse<String> response = get("/api/listings/container-error");
        final String requestId = requestId(response);

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).isEqualTo(requestId + ":" + requestId + ":ERROR");
    }

    @Test
    void supportsSseConnection() throws Exception {
        final HttpResponse<String> response = get("/api/listings/sse");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("event:connected", "data:" + requestId(response));
    }

    @Test
    void includesRequestIdInSystemLoggerOutput() throws Exception {
        final HttpResponse<String> response = get("/api/listings/system-log");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(logEvent("system-request-id-probe").getMDCPropertyMap())
                .containsEntry(RequestIdFilter.MDC_KEY, requestId(response));
    }

    private HttpResponse<String> get(final String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String requestId(final HttpResponse<String> response) {
        final String requestId = response.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow();
        assertThat(UUID.fromString(requestId).version()).isEqualTo(4);
        return requestId;
    }

    private ILoggingEvent logEvent(final String message) {
        return logs.list.stream().filter(event -> event.getMessage().equals(message)).findFirst().orElseThrow();
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({RequestIdConfiguration.class, AuthInterceptor.class, WebConfig.class,
            LoginMemberArgumentResolver.class, LoginDeliveryMemberArgumentResolver.class,
            GlobalExceptionHandler.class, ProbeController.class})
    static class TestConfig {

        @Bean
        TomcatServletWebServerFactory webServerFactory() {
            final TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(0);
            factory.addErrorPages(new ErrorPage(HttpStatus.BAD_GATEWAY, "/request-id-test/error"));
            return factory;
        }

        @Bean
        DispatcherServlet dispatcherServlet() {
            return new DispatcherServlet();
        }

        @Bean
        DispatcherServletRegistrationBean dispatcherServletRegistration(final DispatcherServlet dispatcherServlet) {
            final DispatcherServletRegistrationBean registration =
                    new DispatcherServletRegistrationBean(dispatcherServlet, "/");
            registration.setAsyncSupported(true);
            return registration;
        }

        @Bean
        MemberRepository memberRepository() {
            return mock(MemberRepository.class);
        }

        @Bean
        DeliveryMemberRepository deliveryMemberRepository() {
            return mock(DeliveryMemberRepository.class);
        }

        @Bean
        PaymentService paymentService() {
            return mock(PaymentService.class);
        }

        @Bean
        CheckoutController checkoutController(final PaymentService paymentService) {
            return new CheckoutController(paymentService, "http://localhost/result");
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/protected")
        void protectedEndpoint() {
        }

        @GetMapping("/api/listings/request-id")
        String requestId() {
            LoggerFactory.getLogger(ProbeController.class).info("request-id-probe");
            return MDC.get(RequestIdFilter.MDC_KEY);
        }

        @GetMapping("/api/listings/business-error")
        void businessError() {
            throw new BusinessException(ErrorCode.ALREADY_ORDERED);
        }

        @GetMapping("/api/listings/invalid-input")
        void invalidInput(@RequestParam final int amount) {
        }

        @GetMapping("/api/listings/unexpected-error")
        void unexpectedError() {
            throw new IllegalStateException("synthetic failure");
        }

        @GetMapping("/api/listings/async")
        void async(final HttpServletRequest request, final HttpServletResponse response) {
            request.setAttribute("initialId", MDC.get(RequestIdFilter.MDC_KEY));
            final AsyncContext async = request.startAsync();
            async.setTimeout(3000);
            async.start(() -> {
                request.setAttribute("backgroundId", MDC.get(RequestIdFilter.MDC_KEY) == null
                        ? "none" : MDC.get(RequestIdFilter.MDC_KEY));
                async.dispatch("/api/listings/async-result");
            });
        }

        @GetMapping("/api/listings/async-result")
        String asyncResult(final HttpServletRequest request) {
            return request.getAttribute("initialId") + ":" + MDC.get(RequestIdFilter.MDC_KEY)
                    + ":" + request.getDispatcherType() + ":" + request.getAttribute("backgroundId");
        }

        @GetMapping("/api/listings/container-error")
        void containerError(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
            request.setAttribute("initialId", MDC.get(RequestIdFilter.MDC_KEY));
            response.sendError(502);
        }

        @GetMapping("/request-id-test/error")
        String error(final HttpServletRequest request) {
            return request.getAttribute("initialId") + ":" + MDC.get(RequestIdFilter.MDC_KEY)
                    + ":" + request.getDispatcherType();
        }

        @GetMapping("/api/listings/sse")
        SseEmitter sse() throws IOException {
            final SseEmitter emitter = new SseEmitter(3000L);
            emitter.send(SseEmitter.event().name("connected").data(MDC.get(RequestIdFilter.MDC_KEY)));
            emitter.complete();
            return emitter;
        }

        @GetMapping("/api/listings/system-log")
        void systemLog() {
            System.getLogger(ProbeController.class.getName()).log(System.Logger.Level.WARNING, "system-request-id-probe");
        }
    }
}
