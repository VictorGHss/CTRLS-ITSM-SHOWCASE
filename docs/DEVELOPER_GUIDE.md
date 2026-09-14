# Guia do Desenvolvedor e Operações (SRE) — CTRLS ITSM

Este documento orienta a configuração do ambiente de desenvolvimento, parametrização de variáveis de ambiente, execução de testes automatizados, observabilidade e runbooks de resolução de incidentes em produção.

---

## 1. Configuração do Ambiente Local

### 1.1 Requisitos Mínimos
* **Java SE Development Kit (JDK) 21:** Necessário para compilação com suporte a Virtual Threads.
* **Node.js (v20 ou superior)** e **npm:** Para execução do frontend React SPA.
* **Docker** e **Docker Compose:** Para orquestração local do banco de dados, cache e telemetria.
* **Apache Maven (3.9+):** Opcional (o wrapper `./mvnw` está incluído no diretório `api/`).

### 1.2 Inicialização dos Contêineres de Apoio
Execute na raiz do projeto:
```bash
docker compose up -d db redis prometheus grafana
```

Portas mapeadas no ambiente local:
* **PostgreSQL (`itsm_db`):** `localhost:5436` (porta interna `5432`).
* **Redis (`itsm_redis`):** `localhost:6380` (porta interna `6379`).
* **Prometheus (`itsm_prometheus`):** `localhost:9095` (porta interna `9090`).
* **Grafana (`itsm_grafana`):** `localhost:3001` (porta interna `3000`).

---

## 2. Dicionário de Variáveis de Ambiente (`.env`)

Copie o modelo para os ambientes da aplicação:
```bash
cp .env.example .env
cp .env.example api/.env
```

### 2.1 Mapeamento das Principais Chaves

| Variável | Descrição | Exemplo / Padrão |
|---|---|---|
| `POSTGRES_DB` | Nome do banco de dados | `itsm` |
| `POSTGRES_USER` | Usuário do banco | `postgres` |
| `POSTGRES_PASSWORD` | Senha do banco | `postgres` |
| `JWT_SECRET` | Chave HMAC de 256 bits para tokens | *(String secreta com 32+ caracteres)* |
| `VAULT_ENCRYPTION_KEY` | Chave AES-GCM em Base64 para o cofre | *(Chave simétrica de 256 bits)* |
| `SPRING_REDIS_HOST` | Host do Redis | `localhost` (ou `redis` no Docker) |
| `SPRING_REDIS_PORT` | Porta do Redis | `6380` (ou `6379` no Docker) |
| `APP_APPOINTMENT_FEEGOW_API_BASE_URL` | URL base da API do Feegow | `https://api.feegow.com` |
| `APP_APPOINTMENT_FEEGOW_API_TOKEN` | Token de acesso à API do Feegow | *(Token x-access-token)* |
| `APP_APPOINTMENT_BLIP_BOT_KEY` | Key do Roteador Principal Take Blip | `Key cm91dGVy...` |
| `APP_APPOINTMENT_BLIP_DESK_KEY` | Key do Túnel do Blip Desk | `Key dHVubmVs...` |
| `GERACESSO_URL` | Endpoint da controladora de catracas | `http://192.168.1.100:8082/AgendamentoVisita` |
| `GERACESSO_TOKEN` | Bearer token de autorização GerAcesso | *(Token JWT GerAcesso)* |
| `CONTAAZUL_CLIENT_ID` | Client ID da aplicação Conta Azul V2 | *(UUID Conta Azul)* |
| `CONTAAZUL_CLIENT_SECRET` | Client Secret da Conta Azul V2 | *(String secreta)* |
| `DISCORD_BOT_TOKEN` | Token do bot Discord (JDA 5) | *(Token de Bot Discord)* |
| `DISCORD_OPERATIONAL_WEBHOOK_URL` | Webhook do canal de incidentes | `https://discord.com/api/webhooks/...` |

---

## 3. Execução das Aplicações

### 3.1 Backend (Spring Boot 3 / Java 21)
```bash
cd api

# Compilar e validar tipos
./mvnw clean compile -DskipTests

# Executar testes unitários e de integração
./mvnw test

# Iniciar o servidor local (Porta 8085)
./mvnw spring-boot:run
```

A documentação interativa Swagger estará acessível em: `http://localhost:8085/api/swagger-ui.html`

### 3.2 Frontend (React 19 + Vite + TypeScript)
```bash
cd front

# Instalar dependências
npm install

# Validar tipagem e regras de lint (0 erros / 0 avisos)
npm run lint

# Compilar bundle de produção otimizado
npm run build

# Iniciar servidor de desenvolvimento (Porta 5173)
npm run dev
```

---

## 4. Runbooks Operacionais de SRE

### 4.1 Runbook: Re-autorização Manual da Conta Azul
Se a integração retornar `invalid_grant` por expiração ou revogação de tokens:
1. Obtenha um token de administrador via login (`POST /api/auth/login`).
2. Acesse o menu **Financeiro -> Conta Azul -> Conectar** no painel web para gerar nova URL de consentimento.
3. Se necessário expurgar tokens corrompidos diretamente no banco de dados:
   ```sql
   DELETE FROM contaazul_oauth_tokens;
   ```

### 4.2 Runbook: Diagnóstico de Transbordo no Blip Desk
Caso um atendimento não seja direcionado para a fila correta:
1. Verifique os logs de sincronização de contato:
   ```bash
   docker logs itsm_api --tail=200 | grep "BlipContact-Adapter"
   ```
2. Confirme se os campos `fila` e `Medico` foram sincronizados no contato do paciente no Roteador e no Túnel do Desk.
3. Certifique-se de que o nome da fila em `appointment_doctor_mapping.blip_queue_id` corresponde exatamente ao nome cadastrado no Blip Desk (ex: `Ortopedia - Consultorio 01`).

### 4.3 Runbook: Falha de Comunicação ou Bloqueio nas Catracas (GerAcesso)
1. **Teste de Conectividade de Rede Local:**
   ```bash
   curl -I -H "Authorization: Bearer $GERACESSO_TOKEN" http://192.168.1.100:8082/AgendamentoVisita
   ```
2. **Inspeção de Logs em Tempo Real:**
   ```bash
   docker logs itsm_api --tail=200 | grep -E "GerAcesso-Adapter|CATRACA-POST|ReactivateAccess"
   ```
3. **Resolução de Anti-Passback / Leitor Travado:**
   * Se o paciente já apresentou o QR Code e não girou o braço da catraca a tempo, a controladora bloqueia o código anterior.
   * Acione a reativação dinâmica via REST ou solicite ao paciente clicar em *"Atualizar QR Code"* no celular:
     ```bash
     curl -X POST http://localhost:8085/api/v1/access/reactivate/{appointmentId}
     ```
   * O motor gera uma nova visita com janela retroativa de 5 minutos (`now.minusMinutes(5)`) e substitui a credencial no banco e na tela instantaneamente.
4. **Verificação de CPF:** Se o agendamento no Feegow estiver sem CPF cadastrado, o sistema responderá com `"requiresCpfFallback": true` para coleta via WhatsApp ou totem.
5. **Parâmetro Mandatório:** Certifique-se de que o payload contém o campo `"tipovisista": 1`. Sem este campo exato, o hardware GerAcesso rejeita a liberação.

### 4.4 Runbook: Deploy e Atualização em Produção
No servidor de produção:
```bash
cd /opt/ctrls-itsm/CTRLS-ITSM
git pull
docker compose down
docker compose up -d --build
docker compose logs -f api
```

### 4.5 Runbook: Procedimento de Desativação Segura e Backup (Decommissioning)
Caso o ecossistema precise ser desativado ou transferido de infraestrutura:
1. **Backup Completo do Banco de Dados (PostgreSQL 16):**
   ```bash
   docker exec -t itsm_db pg_dump -U postgres -d itsm -F c -b -v -f /tmp/itsm_backup_full.dump
   docker cp itsm_db:/tmp/itsm_backup_full.dump ./itsm_backup_$(date +%Y%m%d).dump
   ```
2. **Backup de Configurações e Variáveis de Ambiente:**
   ```bash
   tar -czvf itsm_env_backup_$(date +%Y%m%d).tar.gz .env api/.env
   ```
3. **Parada Segura dos Serviços:**
   ```bash
   docker compose down -v  # ou docker compose stop caso deseje manter volumes locais
   ```
4. **Desconexão de Webhooks Externos:**
   * **Take Blip:** Remover a URL de webhook configurada nas ações de entrada/saída do fluxo do bot.
   * **Conta Azul:** Revogar a aplicação nas configurações de desenvolvedor do portal Conta Azul.
5. **Preservação de Propriedade Intelectual:**
   * O código-fonte, histórico Git e esquemas de migração Flyway constituem propriedade integral do autor, prontos para empacotamento em modelo SaaS white-label para novas organizações.
