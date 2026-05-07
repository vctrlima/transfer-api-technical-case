# SPEC: Transferência entre Contas

Versão: 1.0
Status: Aprovada

## Objetivo

Permitir que um cliente transfira valores entre contas, garantindo
consistência, rastreabilidade regulatória e resiliência a falhas.

## Pré-condições

- Conta origem existe e está ATIVA
- Cliente autenticado e identificado
- Chave de idempotência presente no header

## Contrato de Entrada

POST /v1/transfers

Headers:
Idempotency-Key: <UUID v4 gerado pelo cliente>
Authorization: Bearer <token>

Body:
{
"originAccountId": "string",
"destinationAccountId": "string",
"amount": number (positivo, máximo 2 casas decimais),
"description": "string (opcional)"
}

## Contrato de Saída — Sucesso

HTTP 202 Accepted
{
"transferId": "uuid",
"status": "PROCESSING",
"amount": number,
"createdAt": "ISO-8601"
}

## Regras de Negócio

RN-01: amount > 0
RN-02: Conta origem deve estar com status ACTIVE
RN-03: Saldo disponível >= amount
RN-04: Total transferido no dia corrente + amount <= 1000.00
RN-05: Idempotency-Key já processada → retornar resultado original sem reprocessar

## Fluxo Orquestrado (SAGA)

Etapa 1 → Buscar dados do cliente na API Cadastro
Falha: retornar 502, não avançar

Etapa 2 → Validar conta ativa + saldo + limite diário (Redis INCRBY atômico)
Falha de negócio: retornar 422 com código de erro específico
Falha técnica: retornar 503

Etapa 3 → Debitar conta origem / Creditar conta destino
Falha: retornar 503, não avançar (nada foi debitado)

Etapa 4 → Publicar evento na fila SQS para notificação ao BACEN
Falha: executar compensação da Etapa 3 (rollback do débito/crédito)
Retornar 503

Etapa 5 → Retornar 202 ao cliente

## Compensações (Rollback)

Etapa 3 falhou após débito → estornar crédito/débito e atualizar Redis
Etapa 4 falhou → idem acima + registrar falha no log de auditoria

## Invariantes

- Nunca retornar 200 OK (sempre 202 para transferências)
- Nunca debitar sem creditar
- Nunca notificar o BACEN sem transferência concluída
- Idempotency-Key deve ser armazenada com TTL de 24h no Redis

## Códigos de Erro

400 → Requisição malformada
401 → Não autenticado
422 → Falha de regra de negócio (body com código específico)
429 → Rate limit interno da API
502 → Dependência externa indisponível
503 → Erro interno recuperável