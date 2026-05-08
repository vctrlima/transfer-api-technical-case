package com.personal.transfer.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.personal.transfer.infrastructure.redis.BalanceCacheRepository;
import com.personal.transfer.infrastructure.redis.CustomerCacheRepository;
import com.personal.transfer.infrastructure.redis.DailyLimitRepository;
import com.personal.transfer.infrastructure.redis.IdempotencyRepository;
import com.personal.transfer.infrastructure.sqs.BacenEventConsumer;
import com.personal.transfer.infrastructure.sqs.BacenEventPublisher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("transferdb")
            .withUsername("transfer")
            .withPassword("transfer");

    protected static final WireMockServer CADASTRO_MOCK;
    protected static final WireMockServer BACEN_MOCK;

    static {
        CADASTRO_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        CADASTRO_MOCK.start();
        BACEN_MOCK = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        BACEN_MOCK.start();
    }

    @AfterAll
    static void stopMocks() {
        CADASTRO_MOCK.stop();
        BACEN_MOCK.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("cadastro.api.url", () -> "http://localhost:" + CADASTRO_MOCK.port());
        registry.add("bacen.api.url", () -> "http://localhost:" + BACEN_MOCK.port());
    }

    @MockBean
    RedisConnectionFactory redisConnectionFactory;

    @MockBean
    SqsClient sqsClient;

    @MockBean
    BacenEventPublisher bacenEventPublisher;

    @MockBean
    BacenEventConsumer bacenEventConsumer;

    @MockBean
    BalanceCacheRepository balanceCacheRepository;

    @MockBean
    CustomerCacheRepository customerCacheRepository;

    @MockBean
    DailyLimitRepository dailyLimitRepository;

    @MockBean
    IdempotencyRepository idempotencyRepository;

    @Autowired
    protected MockMvc mockMvc;

    @BeforeEach
    void setUpBaseMocks() {
        CADASTRO_MOCK.resetAll();
        BACEN_MOCK.resetAll();

        when(balanceCacheRepository.get(anyString())).thenReturn(Optional.empty());
        when(customerCacheRepository.get(anyString())).thenReturn(Optional.empty());
        when(dailyLimitRepository.getAccumulated(anyString())).thenReturn(BigDecimal.ZERO);
        when(dailyLimitRepository.incrementAndGet(anyString(), any(BigDecimal.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(idempotencyRepository.findByKey(anyString())).thenReturn(Optional.empty());
        doNothing().when(bacenEventPublisher).publish(any());
    }
}

