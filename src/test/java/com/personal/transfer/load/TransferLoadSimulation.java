package com.personal.transfer.load;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/**
 * Simulação de carga conforme SPEC-LT-01.
 * <p>
 * Metas:
 * - p99 latência < 100ms
 * - p95 latência < 80ms
 * - Taxa de erro < 0,1%
 * - Nenhum timeout no Circuit Breaker durante carga sustentada
 * <p>
 * Distribuição de requisições:
 * - 70% POST /v1/transfers
 * - 30% GET /v1/accounts/{id}/balance
 * <p>
 * Execução:
 * ./mvnw gatling:test -Dgatling.simulationClass=com.personal.transfer.load.TransferLoadSimulation
 */
public class TransferLoadSimulation extends Simulation {

    private static final String BASE_URL =
            System.getProperty("gatling.baseUrl", "http://localhost:8080");

    private static final int RAMP_USERS_TRANSFER =
            Integer.parseInt(System.getProperty("gatling.rampUsers.transfer", "4200"));

    private static final int RAMP_USERS_BALANCE =
            Integer.parseInt(System.getProperty("gatling.rampUsers.balance", "1800"));

    private static final int RAMP_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("gatling.rampDuration", "60"));

    private static final int SUSTAIN_DURATION_SECONDS =
            Integer.parseInt(System.getProperty("gatling.sustainDuration", "300"));

    private static final int RAMP_DOWN_SECONDS =
            Integer.parseInt(System.getProperty("gatling.rampDown", "30"));

    private static final String ORIGIN_ACCOUNT = "acc-origin-001";
    private static final String DESTINATION_ACCOUNT = "acc-dest-001";

    private static final Iterator<Map<String, Object>> idempotencyFeeder =
            Stream.generate(() -> Map.<String, Object>of("idempotencyKey", UUID.randomUUID().toString()))
                    .iterator();

    private final HttpProtocolBuilder httpProtocol = http
            .baseUrl(BASE_URL)
            .contentTypeHeader("application/json")
            .acceptHeader("application/json")
            .shareConnections();

    private final ScenarioBuilder transferScenario = scenario("POST /v1/transfers")
            .feed(idempotencyFeeder)
            .exec(http("Criar Transferência")
                    .post("/v1/transfers")
                    .header("Idempotency-Key", "#{idempotencyKey}")
                    .body(StringBody("""
                            {
                                "originAccountId": "%s",
                                "destinationAccountId": "%s",
                                "amount": 1.00,
                                "description": "Gatling load test"
                            }
                            """.formatted(ORIGIN_ACCOUNT, DESTINATION_ACCOUNT)
                    )).asJson()
                    .check(status().in(202, 422))
            );

    private final ScenarioBuilder balanceScenario = scenario("GET /v1/accounts/balance")
            .exec(http("Consultar Saldo")
                    .get("/v1/accounts/" + ORIGIN_ACCOUNT + "/balance")
                    .check(status().is(200))
            );

    {
        setUp(
                transferScenario.injectOpen(
                        rampUsers(RAMP_USERS_TRANSFER).during(Duration.ofSeconds(RAMP_DURATION_SECONDS)),
                        constantUsersPerSec(RAMP_USERS_TRANSFER).during(Duration.ofSeconds(SUSTAIN_DURATION_SECONDS)),
                        rampUsersPerSec(RAMP_USERS_TRANSFER).to(0).during(Duration.ofSeconds(RAMP_DOWN_SECONDS))
                ),
                balanceScenario.injectOpen(
                        rampUsers(RAMP_USERS_BALANCE).during(Duration.ofSeconds(RAMP_DURATION_SECONDS)),
                        constantUsersPerSec(RAMP_USERS_BALANCE).during(Duration.ofSeconds(SUSTAIN_DURATION_SECONDS)),
                        rampUsersPerSec(RAMP_USERS_BALANCE).to(0).during(Duration.ofSeconds(RAMP_DOWN_SECONDS))
                )
        )
                .protocols(httpProtocol)
                .assertions(
                        global().responseTime().percentile(99).lt(100),
                        global().responseTime().percentile(95).lt(80),
                        global().failedRequests().percent().lt(0.1)
                );
    }
}

