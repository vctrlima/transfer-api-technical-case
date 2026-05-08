# Transfer API — Desafio Técnico Itaú

API REST para **Consulta de Saldo** e **Transferência entre Contas**, desenvolvida em Java 21 + Spring Boot 3.3.2.

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

## Padrões de resiliência

| Dependência  | Circuit Breaker             | Retry                     | Timeout    |
|--------------|-----------------------------|---------------------------|------------|
| API Cadastro | ✅ (slide=10, threshold=50%) | ✅ (2 tentativas, backoff) | ✅ 500ms/2s |
| BACEN API    | ✅ (slide=10, threshold=50%) | ✅ (3 tentativas, backoff) | ✅ 500ms/2s |

Rate limit 429 do BACEN → `BacenRateLimitException` → mensagem re-enfileirada no SQS (sem retry imediato pelo
Resilience4j, evitando agravar o rate limit).

---

## Contas de teste (data.sql)

| ID                 | Status   | Saldo       |
|--------------------|----------|-------------|
| `acc-origin-001`   | ACTIVE   | R$ 1.500,00 |
| `acc-dest-001`     | ACTIVE   | R$ 500,00   |
| `acc-inactive-001` | INACTIVE | R$ 200,00   |
| `acc-blocked-001`  | BLOCKED  | R$ 300,00   |

