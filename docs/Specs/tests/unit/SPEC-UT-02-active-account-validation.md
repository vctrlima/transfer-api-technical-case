Classe: ValidateAccountStepTest

Cenários:

- status = ACTIVE → avançar para próxima etapa da SAGA
- status = INACTIVE → lançar AccountInactiveException
- status = BLOCKED → lançar AccountInactiveException

Ferramenta: JUnit 5 + Mockito
Dependências: AccountRepository mockado