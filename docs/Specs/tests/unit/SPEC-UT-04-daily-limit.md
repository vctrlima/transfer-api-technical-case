Classe: ValidateLimitStepTest

Cenários:

- acumulado = 0, amount = 1000 → aprovar (limite exato)
- acumulado = 0, amount = 1001 → lançar DailyLimitExceededException
- acumulado = 600, amount = 400 → aprovar (soma exata)
- acumulado = 600, amount = 401 → lançar DailyLimitExceededException
- acumulado = 1000, amount = 1 → lançar DailyLimitExceededException

Ferramenta: JUnit 5 + Mockito
Dependências: DailyLimitRepository mockado