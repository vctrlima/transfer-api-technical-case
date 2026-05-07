Classe: ValidateAccountStepTest

Cenários:

- saldo = 500, amount = 300 → aprovar
- saldo = 500, amount = 500 → aprovar (limite exato)
- saldo = 500, amount = 501 → lançar InsufficientBalanceException
- saldo = 0, amount = 1 → lançar InsufficientBalanceException

Ferramenta: JUnit 5 + Mockito
Dependências: AccountRepository mockado