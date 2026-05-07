Classe: BacenApiContractTest

Contratos esperados:

- POST /notify
  → 200: notificação aceita
  → 429: rate limit — NÃO retentar imediatamente, enfileirar para retry via SQS
  → 503: falha temporária — retentar com backoff exponencial

Verificações:

- 429 não dispara retry síncrono
- 503 dispara retry com backoff (1s, 2s, 4s)
- Após N retries esgotados → mensagem vai para DLQ

Ferramenta: Spring Cloud Contract + WireMock