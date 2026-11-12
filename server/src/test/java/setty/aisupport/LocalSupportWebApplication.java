package setty.aisupport;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;

/** Explicit local-only entry point. Never included in production artifacts or ordinary tests. */
public final class LocalSupportWebApplication {
    public static final String LOGIN_ID = "eval-buyer";
    public static final String PASSWORD = "LocalSupport123!"; // Public synthetic data, not a credential for any real service.

    public static void main(String[] args) throws Exception {
        String runDirectory = System.getProperty("setty.local.run-directory");
        if (runDirectory == null) throw new IllegalArgumentException("Use the local support launcher");
        Path directory = Path.of(runDirectory).toAbsolutePath();
        Files.createDirectories(directory);
        int port = Integer.parseInt(System.getProperty("setty.local.port", "18080"));
        if(port < 1024 || port > 65535) throw new IllegalArgumentException("Choose a local port between 1024 and 65535");
        String evalRoot = System.getProperty("setty.local.eval-root", "");
        if(!evalRoot.isBlank() && (port == 18082 || !System.getProperty("setty.local.restore-dump", "").isBlank()))
            throw new IllegalArgumentException("Evaluation cannot use the user port or restore user data");
        String provider = System.getProperty("setty.local.provider", "openai");
        if(!List.of("bedrock","openai").contains(provider)) throw new IllegalArgumentException("Unsupported local provider");
        boolean enabled = "true".equals(System.getProperty("setty.local.ai-enabled", "false"));
        String model = System.getProperty("setty.local.model", "");
        String restoreFile = System.getProperty("setty.local.restore-dump", "");
        if(enabled && model.isBlank()) throw new IllegalArgumentException("An approved model must be explicitly selected");
        if(enabled && !"openai".equals(provider)) throw new IllegalArgumentException("This launcher enables only the approved local provider");
        AtomicReference<ConfigurableApplicationContext> context = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean();
        MySQLContainer database = new MySQLContainer("mysql:8.4.11").withDatabaseName("setty_ai_eval")
                .withUsername("setty_local_eval").withPassword("synthetic-local-database-only");
        Runnable close = () -> {
            if(!closed.compareAndSet(false,true)) return;
            var active=context.get();
            if(active!=null) active.close();
            database.stop();
        };
        Runtime.getRuntime().addShutdownHook(new Thread(close,"local-support-cleanup"));
        try {
            database.start();
            if(!restoreFile.isBlank()) {
                // Only the newly created, owned container receives this copy. Original DB remains untouched.
                database.copyFileToContainer(MountableFile.forHostPath(restoreFile,0600),"/tmp/local-support-restore.sql");
                var restored=database.execInContainer("sh","-c",
                        "mysql --default-character-set=utf8mb4 --user=setty_local_eval --password=synthetic-local-database-only setty_ai_eval < /tmp/local-support-restore.sql");
                database.execInContainer("rm","--","/tmp/local-support-restore.sql");
                if(restored.getExitCode()!=0) throw new IllegalStateException("Local snapshot restore failed; original database was not changed");
            }
            List<String> settings = new ArrayList<>(List.of(
                    "--spring.config.location=classpath:/application.yml", "--spring.config.import=",
                    "--spring.profiles.active=default", "--server.address=127.0.0.1", "--server.port="+port,
                    "--spring.datasource.url="+database.getJdbcUrl(),
                    "--spring.datasource.username="+database.getUsername(), "--spring.datasource.password="+database.getPassword(),
                    "--spring.flyway.baseline-on-migrate=false", "--spring.flyway.clean-disabled=true",
                    "--setty.ai-support.provider="+provider, "--setty.ai-support.enabled="+enabled,
                    "--setty.ai-support.max-concurrent=1", "--setty.notification.sender=noop",
                    "--setty.delivery-fee.zone-mode=dong", "--management.otlp.metrics.export.enabled=false",
                    "--setty.address.backfill.enabled=false", "--setty.listing.zip-zone-backfill.enabled=false",
                    "--setty.order.pending-expiration.scan-interval=PT24H", "--setty.order.completion.scan-interval=PT24H",
                    "--setty.events.resubmission-interval=PT24H"));
            if(!model.isBlank()) settings.add("--setty.ai-support.model-id="+model);
            var app=new SpringApplication(LocalConfiguration.class);
            app.setRegisterShutdownHook(false);
            var active=app.run(settings.toArray(String[]::new));
            context.set(active);
            var jdbc=active.getBean(JdbcTemplate.class);
            try(var connection=jdbc.getDataSource().getConnection()) {
                if(!connection.getMetaData().getURL().equals(database.getJdbcUrl()))
                    throw new IllegalStateException("Refusing to seed a different database");
            }
            if(!"setty_ai_eval".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
                throw new IllegalStateException("Refusing to seed a non-evaluation database");
            if(restoreFile.isBlank()) {
                new ResourceDatabasePopulator(new ClassPathResource("aisupport/eval-fixture.sql")).execute(jdbc.getDataSource());
                if(jdbc.update("UPDATE members SET password=? WHERE id=920001 AND login_id=?",
                        new BCryptPasswordEncoder().encode(PASSWORD),LOGIN_ID)!=1)
                    throw new IllegalStateException("Synthetic owner was not found");
            }
            var json=JsonMapper.builder().findAndAddModules().build();
            Files.writeString(directory.resolve("business-baseline.json"),json.writeValueAsString(
                    jdbc.queryForList("SELECT o.id,o.buyer_id,o.order_status,o.delivery_status,o.created_at,"
                            + "d.status AS actual_delivery_status,l.sale_status FROM orders o "
                            + "LEFT JOIN delivery d ON d.order_id=o.id LEFT JOIN listings l ON l.id=o.listing_id ORDER BY o.id")));
            Files.writeString(directory.resolve("ready.json"),json.writeValueAsString(Map.of(
                    "baseUrl","http://127.0.0.1:"+port,"provider",provider,"aiEnabled",enabled,
                    "loginId",LOGIN_ID,"syntheticPassword",PASSWORD,"database","disposable-testcontainer",
                    "databaseContainerId",database.getContainerId())));
            System.out.println("LOCAL_SUPPORT_READY http://127.0.0.1:"+port+" AI_ENABLED="+enabled);
            while(!Files.exists(directory.resolve("stop.request"))) Thread.sleep(500);
        } finally {
            close.run();
            Files.deleteIfExists(directory.resolve("ready.json"));
        }
    }

    @TestConfiguration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @AutoConfigurationPackage(basePackages="setty")
    @ComponentScan(basePackages="setty",excludeFilters={
            @ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=setty.SettyApplication.class),
            @ComponentScan.Filter(type=FilterType.REGEX,pattern=".*Test(?:\\$.*)?"),
            @ComponentScan.Filter(type=FilterType.ANNOTATION,classes=org.springframework.boot.test.context.TestComponent.class)
    })
    static class LocalConfiguration {
        @Bean
        static BeanPostProcessor localModelBudget(ApplicationContext context) {
            var budget=new LocalSupportModelBudget(Path.of(System.getProperty("setty.local.run-directory"),"model-usage.json"),
                    ()->context.getBean(SupportConfiguration.Settings.class).modelId());
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean,String name) {
                    String evalRoot=System.getProperty("setty.local.eval-root", "");
                    if(!evalRoot.isBlank() && bean instanceof java.time.Clock)
                        return java.time.Clock.fixed(java.time.Instant.parse(System.getProperty("setty.local.eval-clock")),java.time.ZoneOffset.UTC);
                    if(bean instanceof SupportModel model) {
                        SupportModel wrapped=budget.wrap(model);
                        return evalRoot.isBlank() ? wrapped : new SupportEvalTape(wrapped,Path.of(evalRoot));
                    }
                    return bean;
                }
            };
        }
    }
}
