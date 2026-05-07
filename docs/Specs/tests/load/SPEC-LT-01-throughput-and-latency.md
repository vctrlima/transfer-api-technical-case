Ferramenta: Gatling

Objetivo:
Validar que a API suporta 6.000 TPS com latência < 100ms no percentil 99.

Configuração:

- Ramp-up: 0 → 6.000 usuários em 60 segundos
- Sustentado: 6.000 TPS por 5 minutos
- Ramp-down: 6.000 → 0 em 30 segundos

Assertions:

- p99 latência < 100ms
- p95 latência < 80ms
- Taxa de erro < 0.1%
- Nenhum timeout no Circuit Breaker durante carga sustentada

Endpoints cobertos:

- POST /v1/transfers (70% das requisições)
- GET /v1/accounts/{id}/balance (30% das requisições)