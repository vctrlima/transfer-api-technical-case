Classe: MoneyTest

Cenários:

- amount = 0 → lançar IllegalArgumentException
- amount = −50 → lançar IllegalArgumentException
- amount = 0.001 → lançar IllegalArgumentException (mais de 2 casas decimais)
- amount = 100,00 → instanciar com sucesso
- amount = 999,99 → instanciar com sucesso

Ferramenta: JUnit 5
Dependências: nenhuma