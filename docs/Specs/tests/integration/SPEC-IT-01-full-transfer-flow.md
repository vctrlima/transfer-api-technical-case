Classe: TransferIntegrationTest

Pré-condições:

- Redis rodando (Testcontainers)
- SQS simulado via Ministack
- Cadastro API mockado via WireMock (200 OK)
- BACEN API mockado via WireMock (200 OK)

Cenários:

- transferência válida
  → HTTP 202
  → status = PROCESSING
  → evento publicado no SQS
  → limite diário atualizado no Redis

- mesma Idempotency-Key enviada duas vezes
  → segunda chamada retorna HTTP 202 com mesmo transferId
  → nenhuma operação reprocessada

- conta inativa
  → HTTP 422
  → body com código ACCOUNT_INACTIVE

- saldo insuficiente
  → HTTP 422
  → body com código INSUFFICIENT_BALANCE

- limite diário excedido
  → HTTP 422
  → body com código DAILY_LIMIT_EXCEEDED

- API Cadastro retorna 503
  → HTTP 502
  → Circuit Breaker registra falha
  → nenhuma etapa da SAGA avança

Ferramenta: JUnit 5 + Testcontainers + WireMock + Ministack