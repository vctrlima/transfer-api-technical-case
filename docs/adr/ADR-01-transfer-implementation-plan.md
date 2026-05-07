# Plano de Implementação — API de Transferências

## 1. Visão Geral da Solução

API REST em Java/Spring Boot responsável por **Consulta de Saldo** e **Transferência entre Contas**, projetada para
6.000 TPS com latência abaixo de 100ms.

As principais decisões arquiteturais são:

- **Orquestração via SAGA** para garantir consistência distribuída com compensações explícitas
- **Redis** como camada atômica de controle de limite diário (INCRBY) e cache de saldo (TTL curto)
- **SQS** para desacoplar a notificação ao BACEN do fluxo principal
- **Idempotência via header** (`Idempotency-Key`) com chave persistida no Redis (TTL 24h)
- **HTTP 202** como resposta ao cliente, refletindo o processamento assíncrono pós-transferência

---

## 2. Estrutura do Projeto

```
src/
├── application/
│   ├── saga/
│   │   ├── TransferSagaOrchestrator.java
│   │   └── steps/
│   │       ├── FetchCustomerStep.java
│   │       ├── ValidateAccountStep.java
│   │       ├── ValidateLimitStep.java
│   │       ├── ExecuteTransferStep.java
│   │       └── PublishBacenEventStep.java
│   └── usecases/
│       ├── TransferUseCase.java
│       └── BalanceUseCase.java
├── domain/
│   ├── entities/
│   │   ├── Account.java
│   │   ├── Transfer.java
│   │   └── TransferStatus.java (enum)
│   ├── valueobjects/
│   │   ├── Money.java
│   │   └── IdempotencyKey.java
│   └── exceptions/
│       ├── AccountInactiveException.java
│       ├── InsufficientBalanceException.java
│       └── DailyLimitExceededException.java
├── infrastructure/
│   ├── adapters/
│   │   ├── CadastroApiAdapter.java
│   │   └── BacenApiAdapter.java
│   ├── redis/
│   │   ├── IdempotencyRepository.java
│   │   └── DailyLimitRepository.java
│   ├── sqs/
│   │   ├── BacenEventPublisher.java
│   │   └── BacenEventConsumer.java
│   └── persistence/
│       └── TransferRepository.java
└── interfaces/
    ├── controllers/
    │   ├── TransferController.java
    │   └── BalanceController.java
    └── dto/
        ├── TransferRequest.java
        └── TransferResponse.java
```

---

## 3. Ordem de Implementação

### Fase 1 — Fundação de Domínio

**Objetivo:** Ter as regras de negócio implementadas e testáveis sem depender de nenhuma infraestrutura.

Tarefas:

- Criar entidades `Account`, `Transfer` e enum `TransferStatus` (`PROCESSING`, `COMPLETED`, `FAILED`, `ROLLED_BACK`)
- Criar value objects `Money` (com validação de precisão e valor positivo) e `IdempotencyKey`
- Implementar exceções de domínio tipadas por regra de negócio
- Escrever testes unitários para cada regra (RN-01 a RN-05)

Dependências: nenhuma.

---

### Fase 2 — Camada Redis

**Objetivo:** Implementar as duas responsabilidades do Redis antes de qualquer lógica de negócio que dependa delas.

Tarefas:

- Configurar Redis com **Ministack** (substituto gratuito ao LocalStack para simular AWS ElastiCache localmente)
- Implementar `IdempotencyRepository` — salvar e consultar `Idempotency-Key` com TTL de 24h
- Implementar `DailyLimitRepository` — operação atômica com `INCRBY` e TTL de 48h na chave `limit::{accountId}::{date}`
- Escrever testes de integração com Testcontainers (Redis container)

Dependências: Fase 1.

---

### Fase 3 — Adaptadores Externos

**Objetivo:** Isolar as chamadas às APIs externas (Cadastro e BACEN) atrás de interfaces, permitindo mock nos testes.

Tarefas:

- Definir interfaces `CadastroApiPort` e `BacenApiPort`
- Implementar `CadastroApiAdapter` com **Resilience4j** (Circuit Breaker + Retry com backoff exponencial)
- Implementar `BacenApiAdapter` com tratamento explícito de HTTP 429 (não fazer retry imediato — o SQS cuidará disso)
- Criar mocks WireMock para ambas as APIs nos testes
- Configurar **Ministack** para simular o SQS localmente

Dependências: Fase 1.

---

### Fase 4 — Orquestrador SAGA

**Objetivo:** Implementar o fluxo principal de transferência com compensações explícitas.

Tarefas:

- Implementar `TransferSagaOrchestrator` com as 5 etapas em sequência
- Cada `Step` deve implementar `execute()` e `compensate()`
- O orquestrador persiste o estado da SAGA a cada etapa (para rastreabilidade e auditoria)
- Implementar compensação de débito/crédito caso a publicação no SQS falhe
- Escrever testes de integração cobrindo os cenários de falha em cada etapa

Dependências: Fases 2 e 3.

---

### Fase 5 — Consumer SQS e Notificação ao BACEN

**Objetivo:** Garantir que todo evento enfileirado seja notificado ao BACEN, mesmo sob rate limit.

Tarefas:

- Implementar `BacenEventConsumer` consumindo a fila SQS via **Ministack**
- Configurar retry com backoff exponencial para respostas 429
- Configurar Dead Letter Queue (DLQ) para eventos que esgotaram os retries — com alarme de monitoramento
- Garantir idempotência no consumer (o mesmo evento pode ser entregue mais de uma vez pelo SQS)
- Escrever testes com WireMock simulando 429 e posterior sucesso

Dependências: Fase 4.

---

### Fase 6 — Controllers e Tratamento de Erros

**Objetivo:** Expor a API REST com contratos bem definidos e tratamento centralizado de erros.

Tarefas:

- Implementar `TransferController` (POST `/v1/transfers`) e `BalanceController` (GET `/v1/accounts/{id}/balance`)
- Implementar `GlobalExceptionHandler` com `@RestControllerAdvice` mapeando cada exceção de domínio para o código HTTP
  correto
- Validar `Idempotency-Key` no nível do controller (header obrigatório)
- Escrever testes de integração do controller com MockMvc

Dependências: Fase 4.

---

### Fase 7 — Observabilidade

**Objetivo:** Garantir rastreabilidade completa do fluxo, especialmente nas falhas.

Tarefas:

- Configurar **Micrometer + Prometheus** para métricas (TPS, latência, taxa de erro por etapa da SAGA)
- Configurar **OpenTelemetry** para distributed tracing (propagar `traceId` em todas as chamadas externas)
- Estruturar logs em JSON com campos obrigatórios: `traceId`, `transferId`, `accountId`, `sagaStep`, `status`
- Instrumentar cada etapa da SAGA com logs de início, sucesso e falha

Dependências: Fase 4.

---

## 4. Configuração do Ministack

O **Ministack** será usado para simular localmente os serviços AWS utilizados na solução — sem custo e sem dependência
de conta AWS durante o desenvolvimento.

Serviços simulados:

```yaml
# docker-compose.yml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  ministack:
    image: ministackdev/ministack:latest
    ports:
      - "4566:4566"
    environment:
      - SERVICES=sqs
      - DEFAULT_REGION=us-east-1

  app:
    build: .
    depends_on:
      - redis
      - ministack
    environment:
      - AWS_ENDPOINT_URL=http://ministack:4566
      - SPRING_REDIS_HOST=redis
```

Configuração no Spring Boot:

```java

@Configuration
public class SqsConfig {

    @Value("${aws.endpoint-url}")
    private String endpointUrl;

    @Bean
    public SqsAsyncClient sqsAsyncClient() {
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create(endpointUrl))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
    }
}
```

---

## 5. Design Patterns Aplicados

| Pattern            | Onde                                    | Justificativa                                                         |
|--------------------|-----------------------------------------|-----------------------------------------------------------------------|
| Orchestration SAGA | `TransferSagaOrchestrator`              | Consistência distribuída com compensações explícitas                  |
| Circuit Breaker    | Adapters externos                       | Impede cascata de falhas quando Cadastro ou BACEN estão indisponíveis |
| Retry + Backoff    | BACEN consumer                          | Lida com rate limit 429 sem sobrecarregar o serviço                   |
| Idempotency Key    | Controller + Redis                      | Evita reprocessamento de retries do cliente                           |
| Adapter            | `CadastroApiAdapter`, `BacenApiAdapter` | Isola dependências externas, facilita mock em testes                  |
| Cache-Aside        | `BalanceUseCase`                        | Leitura rápida do saldo sem pressão no banco                          |
