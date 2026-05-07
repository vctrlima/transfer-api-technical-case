Classe: DailyLimitRaceConditionTest

Objetivo:
Garantir que o INCRBY atômico do Redis impede que duas transferências
simultâneas ultrapassem o limite de R$1.000,00.

Cenário:

- Limite diário zerado para o cliente
- 10 threads simultâneas tentam transferir R$200 cada (total = R$2.000)
- Apenas as 5 primeiras devem ser aprovadas (5 × R$200 = R$1.000)
- As 5 restantes devem receber DailyLimitExceededException

Verificação pós-execução:

- Valor no Redis = exatamente 1000
- Exatamente 5 transferências com status PROCESSING
- Exatamente 5 rejeições com código DAILY_LIMIT_EXCEEDED

Ferramenta: JUnit 5 + Testcontainers (Redis) + ExecutorService