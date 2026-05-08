# Transfer API — Desafio Técnico

API REST para **Consulta de Saldo** e **Transferência entre Contas**, desenvolvida em Java 21 + Spring Boot 3.3.2.

Arquitetura Hexagonal (Ports & Adapters) com padrão SAGA orquestrado, Redis para cache/estado efêmero e AWS SQS para
notificação assíncrona ao BACEN.

---

## Pré-requisitos

| Ferramenta              | Versão mínima |
|-------------------------|---------------|
| Docker + Docker Compose | 24+           |
| Java                    | 21            |
| Maven                   | 3.9+          |

---

## Como rodar

### 1. Subir a infraestrutura completa (recomendado)

```bash
docker-compose up --build
```

Isso sobe:

- **App** na porta `8080`
- **Redis** na porta `6379`
- **LocalStack (SQS)** na porta `4566`
- **WireMock — Cadastro API** na porta `8081`
- **WireMock — BACEN API** na porta `8082`

### 2. Rodar apenas a infraestrutura e subir a app localmente

```bash
# Apenas dependências
docker-compose up redis ministack ministack-setup cadastro-mock bacen-mock

# App com H2 em memória (sem Postgres/Redis externos)
mvn spring-boot:run
```

> Por padrão, a app usa H2 em memória, Redis em `localhost:6379` e LocalStack em `localhost:4566`.

### 3. Rodar os testes

```bash
# Todos os testes (unitários + integração via Testcontainers)
mvn verify

# Apenas testes unitários (rápidos, sem Docker)
mvn test -Dgroups="unitario"
```

---

## Endpoints

### `GET /v1/accounts/{accountId}/balance` — Consulta de Saldo

Retorna o saldo atual e limite diário da conta. Valida se a conta está ativa e busca o nome do cliente na API de
Cadastro.

**Request:**

```
GET /v1/accounts/acc-origin-001/balance
```

**Response 200 OK:**

```json
{
  "accountId": "acc-origin-001",
  "customerName": "Victor Lima",
  "balance": 1500.00,
  "availableLimit": 1000.00,
  "dailyLimitUsed": 0.00,
  "dailyLimitRemaining": 1000.00,
  "updatedAt": "2026-05-08T12:00:00"
}
```

**Erros:**
| Código HTTP | Código de erro | Descrição |
|-------------|---------------|-----------|
| 422 | `ACCOUNT_INACTIVE` | Conta inativa ou bloqueada |
| 422 | `ENTITY_NOT_FOUND` | Conta não encontrada |

---

### `POST /v1/transfers` — Transferência entre Contas

Executa uma transferência. Requer header `Idempotency-Key` único por operação para garantir idempotência.

**Headers:**

```
Idempotency-Key: <uuid>
Content-Type: application/json
```

**Request Body:**

```json
{
  "originAccountId": "acc-origin-001",
  "destinationAccountId": "acc-dest-001",
  "amount": 200.00,
  "description": "Pagamento de serviço"
}
```

**Response 202 Accepted:**

```json
{
  "transferId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "PROCESSING",
  "amount": 200.00,
  "originAccountId": "acc-origin-001",
  "destinationAccountId": "acc-dest-001",
  "createdAt": "2026-05-08T12:00:00"
}
```

**Erros:**
| Código HTTP | Código de erro | Descrição |
|-------------|---------------|-----------|
| 400 | `MISSING_IDEMPOTENCY_KEY` | Header obrigatório ausente |
| 400 | `VALIDATION_ERROR` | Campos inválidos (amount ≤ 0, campos nulos) |
| 422 | `ACCOUNT_INACTIVE` | Conta inativa ou bloqueada |
| 422 | `INSUFFICIENT_BALANCE` | Saldo insuficiente na conta de origem |
| 422 | `DAILY_LIMIT_EXCEEDED` | Limite diário de R$ 1.000,00 excedido |
| 422 | `ENTITY_NOT_FOUND` | Conta não encontrada |
| 502 | `EXTERNAL_SERVICE_UNAVAILABLE` | API de Cadastro ou BACEN indisponível |

---

### `GET /v1/transfers/{transferId}` — Consulta de Status da Transferência

Consulta o status atual de uma transferência. Útil para confirmar a transição de `PROCESSING` → `COMPLETED` após a
notificação assíncrona ao BACEN.

**Request:**

```
GET /v1/transfers/3fa85f64-5717-4562-b3fc-2c963f66afa6
```

**Response 200 OK:**

```json
{
  "transferId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "status": "COMPLETED",
  "amount": 200.00,
  "originAccountId": "acc-origin-001",
  "destinationAccountId": "acc-dest-001",
  "createdAt": "2026-05-08T12:00:00"
}
```

**Status possíveis:** `PROCESSING` → `COMPLETED` | `FAILED` | `ROLLED_BACK`

---

## Decisão arquitetural: por que a notificação ao BACEN é assíncrona

O `POST /v1/transfers` retorna **imediatamente com status `PROCESSING`** após debitar/creditar as contas. A notificação
ao BACEN é processada de forma **assíncrona via fila SQS**.

**Fluxo:**

```
POST /v1/transfers
  └─► SAGA: valida → executa débito/crédito → publica evento SQS
         └─► [202 PROCESSING]

BacenEventConsumer (@Scheduled, 1s)
  └─► Lê SQS → chama BACEN API → marca Transfer = COMPLETED
```

**Por que esse design?**

- **Resiliência:** rate limit 429 do BACEN não reverte a transferência — a mensagem fica na fila e é re-entregue
  automaticamente (SQS visibility timeout)
- **DLQ:** após 3 falhas, a mensagem vai para `bacen-events-dlq` para inspeção e reprocessamento manual
- **Idempotência:** o consumer verifica `TransferStatus.COMPLETED` antes de notificar novamente

Para confirmar o status final, use `GET /v1/transfers/{transferId}`.

---

## Arquitetura

```
interfaces/
  controllers/       ← REST endpoints (Spring MVC)
  dto/               ← Records de request/response

application/
  usecases/          ← Casos de uso (BalanceUseCase, TransferUseCase)
  saga/              ← Orquestração SAGA com compensação transacional
    steps/           ← Strategy Pattern: cada step é independente e compensável

domain/
  entities/          ← Account, Transfer (JPA)
  valueobjects/      ← Money (imutável, com invariantes)
  exceptions/        ← Exceções de negócio customizadas

infrastructure/
  adapters/          ← Feign clients + Circuit Breaker + Retry (Resilience4j)
  persistence/       ← JPA Repositories
  redis/             ← Cache de saldo, cliente, limite diário, idempotência
  sqs/               ← Publisher e Consumer de eventos BACEN
  config/            ← Configuração de Redis, SQS, ObjectMapper
```

---

## SAGA de Transferência

A transferência é executada em 5 steps com compensação automática em caso de falha:

| # | Step                    | O que faz                                                                               | Compensação                      |
|---|-------------------------|-----------------------------------------------------------------------------------------|----------------------------------|
| 1 | `ValidateAccountStep`   | Lê as duas contas no DB (sem lock). Valida status e saldo.                              | —                                |
| 2 | `ValidateLimitStep`     | `INCRBY` atômico no Redis. Valida limite diário (R$ 1.000,00).                          | `DECR` no Redis                  |
| 3 | `FetchCustomerStep`     | Busca dados do cliente na API de Cadastro (cache Redis 60s).                            | —                                |
| 4 | `ExecuteTransferStep`   | `SELECT FOR UPDATE` nas contas → debit/credit + INSERT transfer, tudo em uma transação. | Crédito/débito reverso           |
| 5 | `PublishBacenEventStep` | Publica `BacenTransferEvent` no SQS.                                                    | — (SAGA sofre rollback completo) |

> Steps 2 e 3 executam **em paralelo** via virtual threads para reduzir latência.  
> Requisições rejeitadas (validações) **não geram escrita no banco** — o INSERT da transferência ocorre apenas após
> todas as validações passarem.

**Ordenação de locks:** `findAllByIdsWithLock` usa `ORDER BY id` para evitar deadlocks em transações concorrentes.

---

## Redis — Caches e Estado Efêmero

| Repositório               | Prefixo da chave             | TTL | Estratégia                                          |
|---------------------------|------------------------------|-----|-----------------------------------------------------|
| `IdempotencyRepository`   | `idempotency::`              | 24h | Write-aside (controller)                            |
| `DailyLimitRepository`    | `limit::{accountId}::{data}` | 48h | Counter atômico (INCRBY/DECR em pipeline)           |
| `BalanceCacheRepository`  | `balance::`                  | 5s  | Read-through; evicção explícita pós-transferência   |
| `CustomerCacheRepository` | `customer::`                 | 60s | Read-through (cache da resposta da API de Cadastro) |

---

## Padrões de resiliência

| Dependência  | Circuit Breaker             | Retry                     | Timeout    |
|--------------|-----------------------------|---------------------------|------------|
| API Cadastro | ✅ (slide=10, threshold=50%) | ✅ (2 tentativas, backoff) | ✅ 500ms/2s |
| BACEN API    | ✅ (slide=10, threshold=50%) | ✅ (3 tentativas, backoff) | ✅ 500ms/2s |

Rate limit 429 do BACEN → `BacenRateLimitException` → mensagem re-enfileirada no SQS (sem retry imediato pelo
Resilience4j, evitando agravar o rate limit).

---

## Observabilidade

| Endpoint                   | Descrição                          |
|----------------------------|------------------------------------|
| `GET /actuator/health`     | Status da aplicação e dependências |
| `GET /actuator/prometheus` | Métricas no formato Prometheus     |
| `GET /actuator/metrics`    | Métricas detalhadas                |

Distributed tracing via **Micrometer + Brave (Zipkin)**. Sampling padrão: 1% (
`management.tracing.sampling.probability=0.01`).  
Logs incluem `traceId` e `spanId` no padrão: `[traceId=...,spanId=...]`.

---

## Performance

- **Virtual Threads** habilitadas (`spring.threads.virtual.enabled=true`) — Tomcat e steps paralelos da SAGA rodam em
  virtual threads.
- **Tomcat:** max 400 threads, 10.000 conexões concorrentes, accept-count 200.
- **HikariCP:** pool de 10–50 conexões, timeout de 3s.
- **Feign HC5:** pool de 200 conexões (50/rota).
- **JPA batch:** inserts/updates em lotes de 20.

---

## Contas de teste (data.sql)

| ID                 | Status   | Saldo       |
|--------------------|----------|-------------|
| `acc-origin-001`   | ACTIVE   | R$ 1.500,00 |
| `acc-dest-001`     | ACTIVE   | R$ 500,00   |
| `acc-inactive-001` | INACTIVE | R$ 200,00   |
| `acc-blocked-001`  | BLOCKED  | R$ 300,00   |
