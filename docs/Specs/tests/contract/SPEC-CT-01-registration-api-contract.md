Classe: CadastroApiContractTest

Contratos esperados:

- GET /customers/{id}
  → 200: {"id": ‘string’, "name": ‘string’, "status": ‘string’ }
  → 404: {"error": ‘string’ }
  → 503: qualquer body — deve ativar Circuit Breaker

Verificações:

- Campos obrigatórios presentes e tipados corretamente
- Ausência de "name" → lançar exceção antes de avançar na SAGA
- HTTP 503 → Circuit Breaker abre após N tentativas

Ferramenta: Spring Cloud Contract + WireMock