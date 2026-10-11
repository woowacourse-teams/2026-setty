package setty.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.boot.webmvc.autoconfigure.DispatcherServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import setty.global.exception.GlobalExceptionHandler;
import setty.platform.listing.application.ListingService;
import setty.platform.listing.domain.ConditionGrade;
import setty.platform.listing.domain.Dimensions;
import setty.platform.listing.domain.Listing;
import setty.platform.listing.domain.ListingCategory;
import setty.platform.listing.domain.ListingImage;
import setty.platform.listing.domain.SaleStatus;
import setty.platform.listing.presentation.ListingController;
import setty.platform.listing.repository.ListingImageRepository;
import setty.platform.listing.repository.ListingRepository;
import setty.platform.listing.storage.ListingImageStorage;

class ListingTimingHttpIntegrationTest {

    private static AnnotationConfigServletWebServerApplicationContext context;
    private static HttpClient client;
    private static Logger logger;
    private static ListAppender<ILoggingEvent> logs;

    @BeforeAll
    static void startServer() {
        context = (AnnotationConfigServletWebServerApplicationContext) new SpringApplicationBuilder(Config.class)
                .web(WebApplicationType.SERVLET).profiles("dev").registerShutdownHook(false)
                .run("--spring.main.banner-mode=off", "--logging.structured.format.console=");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        logger = (Logger) LoggerFactory.getLogger(ListingRequestTiming.class);
        logs = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        logs.list = new CopyOnWriteArrayList<>();
        logs.start();
        logger.addAppender(logs);
    }

    @BeforeEach
    void resetState() {
        logs.list.clear();
        reset(context.getBean(ListingRepository.class), context.getBean(ListingImageRepository.class),
                context.getBean(ListingImageStorage.class));
    }

    @AfterAll
    static void stopServer() {
        if (logger != null) { logger.detachAppender(logs); logs.stop(); }
        if (client != null) { client.close(); }
        if (context != null) { context.close(); }
    }

    @Test
    void actualControllerAndServiceProduceJsonAndOneCorrelatedStructuredLog() throws Exception {
        Listing listing = Listing.create(2L, "test desk", "description", 50000, ListingCategory.DESK,
                ConditionGrade.A, Dimensions.of(80, 50, 70));
        ReflectionTestUtils.setField(listing, "id", 7L);
        when(context.getBean(ListingRepository.class)
                .findAllBySaleStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(SaleStatus.AVAILABLE))
                .thenReturn(List.of(listing));
        when(context.getBean(ListingImageRepository.class).findAllByListingIdInOrderByListingIdAscDisplayOrderAsc(anyCollection()))
                .thenReturn(List.of(ListingImage.create(7L, "first.jpg", 1), ListingImage.create(7L, "second.jpg", 2)));
        when(context.getBean(ListingImageStorage.class).publicUrl("first.jpg")).thenReturn("https://example.test/first.jpg");

        HttpResponse<String> response = get();
        assertThat(response.statusCode()).isEqualTo(200);
        Map<String, Object> body = JsonParserFactory.getJsonParser().parseMap(response.body());
        assertThat(body).containsOnlyKeys("items");
        assertThat((List<?>) body.get("items")).hasSize(1);
        assertThat(response.body()).contains("test desk", "https://example.test/first.jpg").doesNotContain("second.jpg");
        Map<String, Object> event = jsonLog();
        assertThat(event).containsEntry("requestId", response.headers().firstValue("X-Request-Id").orElseThrow())
                .containsEntry("event", "listing_request_timing").containsEntry("status", 200)
                .containsEntry("listing_count", 1).containsEntry("image_count", 2).containsEntry("summary_count", 1)
                .containsEntry("listings_completed", true).containsEntry("images_completed", true)
                .containsEntry("mapping_completed", true).containsEntry("response_complete", true);
        assertThat(((Number) event.get("filter_ms")).doubleValue()).isGreaterThanOrEqualTo(
                ((Number) event.get("listings_ms")).doubleValue() + ((Number) event.get("images_ms")).doubleValue()
                        + ((Number) event.get("mapping_ms")).doubleValue());
    }

    @Test
    void emptyResultHasZeroCountsAndNoImageQuery() throws Exception {
        assertThat(get().body()).isEqualTo("{\"items\":[]}");
        assertThat(jsonLog()).containsEntry("listing_count", 0).containsEntry("image_count", 0)
                .containsEntry("summary_count", 0).containsEntry("response_complete", true);
        org.mockito.Mockito.verifyNoInteractions(context.getBean(ListingImageRepository.class));
    }

    @Test
    void handledRepositoryFailureRetains500AndMarksOnlyAttemptedStage() throws Exception {
        when(context.getBean(ListingRepository.class)
                .findAllBySaleStatusAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(SaleStatus.AVAILABLE))
                .thenThrow(new IllegalStateException("synthetic failure"));
        HttpResponse<String> response = get();
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).contains("INTERNAL_ERROR");
        assertThat(jsonLog()).containsEntry("status", 500).containsEntry("listings_completed", false)
                .containsEntry("response_complete", true).doesNotContainKeys("images_ms", "mapping_ms");
    }

    private HttpResponse<String> get() throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + context.getWebServer().getPort() + "/api/listings"))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> jsonLog() {
        // 필터 로그는 응답을 쓴 직후 출력하므로 클라이언트 수신과 짧게 경합할 수 있다.
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(logs.list).hasSize(1));
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        encoder.setFormat("logstash");
        encoder.start();
        try {
            return JsonParserFactory.getJsonParser().parseMap(new String(encoder.encode(logs.list.getFirst()), StandardCharsets.UTF_8));
        } finally {
            encoder.stop();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({RequestIdConfiguration.class, ListingController.class, ListingService.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean TomcatServletWebServerFactory webServerFactory() { return new TomcatServletWebServerFactory(0); }
        @Bean DispatcherServlet dispatcherServlet() { return new DispatcherServlet(); }
        @Bean DispatcherServletRegistrationBean dispatcherRegistration(DispatcherServlet servlet) {
            return new DispatcherServletRegistrationBean(servlet, "/");
        }
        @Bean ListingRepository listingRepository() { return mock(ListingRepository.class); }
        @Bean ListingImageRepository imageRepository() { return mock(ListingImageRepository.class); }
        @Bean ListingImageStorage imageStorage() { return mock(ListingImageStorage.class); }
    }
}
