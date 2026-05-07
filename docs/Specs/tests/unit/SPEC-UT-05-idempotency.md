Classe: IdempotencyServiceTest

Cenários:

- chave nova → retornar empty, prosseguir fluxo
- chave já processada → retornar resultado original sem reprocessar
- chave expirada (TTL) → tratar como chave nova

Ferramenta: JUnit 5 + Mockito
Dependências: IdempotencyRepository mockado