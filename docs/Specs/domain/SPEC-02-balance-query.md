# SPEC: Consulta de Saldo

Versão: 1.0

## Objetivo

Retornar saldo atual da conta com baixa latência (<100ms).

## Contrato

GET /v1/accounts/{accountId}/balance

Headers:
Authorization: Bearer <token>

## Contrato de Saída — Sucesso

HTTP 200 OK
{
"accountId": "string",
"balance": number,
"availableLimit": number,
"dailyLimitUsed": number,
"dailyLimitRemaining": number,
"updatedAt": "ISO-8601"
}

## Estratégia de Leitura

- Saldo lido do Redis (cache-aside, TTL curto)
- Cache miss → leitura no banco + atualização do cache
- dailyLimitUsed lido diretamente do Redis (INCRBY já mantém o valor atômico)

## Invariantes

- Nunca retornar saldo negativo
- Cache nunca pode ter TTL > 5s para saldo