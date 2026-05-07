Classe: BacenConsumerIntegrationTest

Pré-condições:

- SQS simulado via Ministack com DLQ configurada
- BACEN API mockado via WireMock

Cenários:

- BACEN retorna 200 na primeira tentativa
  → mensagem removida da fila
  → status da transferência = COMPLETED

- BACEN retorna 429 nas 3 primeiras tentativas, 200 na quarta
  → retry com backoff exponencial executado
  → mensagem removida da fila após sucesso
  → status = COMPLETED

- BACEN retorna 429 em todas as tentativas
  → mensagem movida para DLQ após esgotar retries
  → alarme disparado (verificar métrica)

- Evento duplicado entregue pelo SQS
  → segundo processamento ignorado por idempotência
  → nenhuma chamada duplicada ao BACEN

Ferramenta: JUnit 5 + Testcontainers + WireMock + Ministack