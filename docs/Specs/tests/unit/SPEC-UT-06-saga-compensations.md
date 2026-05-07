Classe: TransferSagaOrchestratorTest

Cenários:

- falha na Etapa 3 (ExecuteTransfer) antes do débito
  → nenhuma compensação disparada

- falha na Etapa 4 (PublishBacenEvent) após débito
  → compensação da Etapa 3 executada
  → Redis atualizado (DECRBY do valor transferido)
  → status da SAGA = ROLLED_BACK
  → ‘log’ de auditoria registrado

- falha na Etapa 1 (FetchCustomer)
  → retornar 502, nenhuma etapa posterior executada

Ferramenta: JUnit 5 + Mockito
Dependências: todos os steps mockados