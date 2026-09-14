# 🏛️ Amostras de Arquitetura e Engenharia de Software (Code Samples)

Este diretório contém uma seleção curada de arquivos de código-fonte extraídos do ecossistema **CTRLS-ITSM** para fins de avaliação técnica e portfólio. As amostras evidenciam a maturidade arquitetural, padrões de concorrência moderna e boas práticas de Clean Architecture.

---

### 1. [`AsyncConfiguration.java`](AsyncConfiguration.java) — Concorrência de Alta Performance (Java 21 Virtual Threads)
* **Camada:** Infraestrutura & Configuração de Plataforma.
* **Propósito:** Configuração do Spring Framework para delegar tarefas assíncronas (`@Async`) e despachos I/O intensivos a **Virtual Threads (Project Loom)** via `Executors.newVirtualThreadPerTaskExecutor()`.
* **Destaque:** Incorpora o `MdcTaskDecorator` para garantir a propagação transparente do contexto de rastreabilidade (Trace ID e Correlation ID) entre threads virtuais e o pipeline de logs em produção.

---

### 2. [`MonitorAppointmentNudgesUseCase.java`](MonitorAppointmentNudgesUseCase.java) — Orquestração de Casos de Uso (Clean Architecture)
* **Camada:** Aplicação / Casos de Uso (Application Layer).
* **Propósito:** Motor transacional responsável pelo ciclo de vida de confirmações de consultas médicas e reenvio de lembretes (*nudges*) a cada 2 horas para pacientes pendentes.
* **Destaque:**
  * Respeito às regras médicas customizadas (aditamento de horário, restrição horária de 07:00 às 19:00).
  * Blindagem e verificação de elegibilidade médica (`DoctorEligibilityService`).
  * Despacho em lote agrupado (`currentGroupId`) e orquestração de mensageria omnichannel (Take Blip).

---

### 3. [`AccessWindowCalculator.java`](AccessWindowCalculator.java) — Lógica de Domínio Puro (Domain Layer)
* **Camada:** Núcleo de Domínio (Domain Services / Ports & Adapters).
* **Propósito:** Serviço desacoplado de frameworks que calcula com precisão milimétrica as janelas de liberação física das catracas do edifício com base em fusos horários locais (`America/Sao_Paulo`).
* **Destaque:** Uso de **Java Records**, imutabilidade, formatação padronizada para hardware legado de controle de acesso (GerAcesso) e validações temporais preventivas.

---

### 4. [`RedisRateLimiter.java`](RedisRateLimiter.java) — Resiliência Distribuída & Rate Limiting
* **Camada:** Infraestrutura de Cache & Segurança de Rede.
* **Propósito:** Limitador de taxa distribuído atômico sobre Redis (`opsForValue().increment` com `PEXPIRE`), protegendo endpoints sensíveis contra força bruta e controlando o *pacing* de integrações externas.
* **Destaque:** Operações atômicas para evitar condições de corrida (*race conditions*) em clusters com múltiplas instâncias da aplicação.

---

> [!NOTE]
> O código-fonte integral, esquemas de migração Flyway e a aplicação frontend em React 19 estão mantidos em repositório proprietário privado. Para auditoria técnica completa durante processos seletivos, solicite acesso de visualização temporária ao autor.
