# Memória do projeto Intergado

**Atualizada em:** 30/10/2026 (login por e-mail)

**Natureza:** registro do estado observado no repositório e das decisões tomadas durante a construção da demonstração.
**Ponto de entrada:** [README](../README.md) e [roteiro de avaliação](ROTEIRO_AVALIACAO_APP.md).

## 1. Objetivo e contexto

O projeto começou como uma aplicação web para apresentar dashboards rurais a partir de documentos das fazendas. O usuário pediu Java, React, Spring Boot, Maven, Quarkus e PostgreSQL. Para facilitar a demonstração sem instalar um servidor de banco de dados, a implementação atual usa **SQLite**. PostgreSQL permanece uma possibilidade para uma implantação futura; não há configuração PostgreSQL funcional neste repositório.

O escopo entregue até agora combina dois assuntos:

1. **Compras rurais:** cadastro de solicitações, mudança de status e indicadores agregados.
2. **Fazendas:** quatro registros iniciais com o último saldo datado do controle de gado, extraídos das planilhas e exibidos separadamente no dashboard.

Os documentos de origem e de análise estão em [`docs/`](.). A avaliação detalhada das planilhas está em [AVALIACAO_PLANILHAS_DASHBOARDS.md](AVALIACAO_PLANILHAS_DASHBOARDS.md). A arquitetura e o fluxo de uso estão em [DIAGRAMAS_ARQUITETURA_SEQUENCIA.md](DIAGRAMAS_ARQUITETURA_SEQUENCIA.md), também disponíveis como [SVG de arquitetura](ARQUITETURA_APP.svg) e [SVG de sequência](SEQUENCIA_APP.svg).

O material de contexto inclui o [resumo executivo](01_Resumo_Executivo_Caso_Augusto.pdf), os [fluxos revisados](02_Fluxos_Revisados_01_a_08.pdf), o [diagnóstico preliminar](03_Diagnostico_Preliminar_Processos_POPs.pdf), as transcrições de [13/05/2026](04_Transcricao_Reuniao_13-05-2026.pdf) e [19/06/2026](05_Transcricao_Reuniao_19-06-2026.pdf), além das [oportunidades de melhoria](OPORTUNIDADES_MELHORIAS.md). Esses arquivos são contexto de negócio; esta memória descreve sobretudo o software presente no repositório.

## 2. O que está implementado

| Área | Estado atual |
| --- | --- |
| Interface | React 19 com Vite em desenvolvimento; Nginx serve o build no Docker. Mostra fazendas, quatro indicadores de compras, atividade de sete dias, categorias e solicitações recentes. |
| API de escrita | Spring Boot 4.1.2, Java 17 e Maven. Cadastra e edita fazendas e compras; altera o status de compras. |
| API de leitura analítica | Quarkus 3.40.1.1, Java 17 e Maven Wrapper. Calcula indicadores a partir das compras. |
| Persistência | Arquivo SQLite compartilhado pelas duas APIs. O Spring Boot cria tabelas e insere exemplos ausentes na inicialização. |
| Documentação de API | Swagger/OpenAPI separado para Spring Boot e Quarkus. |
| Login | Código de 6 dígitos enviado por e-mail via Resend para uma lista de e-mails autorizados. Sessão em cookie `HttpOnly` validada pelo Spring Boot e pelo Quarkus em todas as rotas `/api/*`. |
| Integrações | Tela **Integrações** (React, `#integracoes`) e API `/api/integrations` para Evolution API (WhatsApp, fase de testes) e Resend. Valores da tela ficam em `integration_settings` e têm prioridade sobre o `.env`; segredos ficam só no `.env`. |
| WhatsApp | Evolution API 2.3.7 em `compose.evolution.yaml`. Mensagem de teste e aviso automático de mudança de status, **apenas para números de teste**. Detalhes em [EVOLUTION_API.md](EVOLUTION_API.md). |
| HTTPS | Serviço `caddy` (2.11.7) no Compose, na frente do Nginx: certificado automático (Let's Encrypt para domínio público, CA interna para localhost/IP), HTTP → HTTPS mantendo a porta, compressão zstd/gzip. Configurado por `Caddyfile` e `CADDY_*`. |
| Dados das planilhas | Apenas **quatro saldos históricos resumidos** entram no cadastro inicial. As linhas das planilhas não são importadas automaticamente. |

Ao abrir, o frontend consulta `GET /api/auth/me`; sem sessão, mostra a tela de login (e-mail → código). Depois do login, consulta três rotas ao abrir ou atualizar a página: `GET /api/farms`, `GET /api/purchases` e `GET /api/analytics/overview`. As chamadas ocorrem em paralelo; se alguma falhar, a interface mostra “Serviços indisponíveis”. O formulário da interface cria compras, e a tabela permite mudar seu status. Qualquer `401` devolve o usuário à tela de login. O Swagger expõe também as operações de cadastro e edição que ainda não têm formulário próprio no React.

## 3. Arquitetura e arquivos principais

```text
Navegador
  └─ Caddy (Docker, HTTPS 443 / HTTP 80 → redireciona)
  └─ Nginx (Docker, porta 3000, também acessível direto em HTTP) ou Vite (dev, porta 5173)
       ├─ /api/auth, /api/integrations, /api/farms, /api/purchases → Spring Boot :8080 → SQLite
       │                                    └─ Evolution API :8090 (compose.evolution.yaml) → WhatsApp
       ├─ /api/analytics          → Quarkus :8081     → SQLite
       ├─ /swagger-ui, /v3/api-docs → Swagger Spring Boot
       └─ /q/swagger-ui, /q/openapi → Swagger Quarkus
```

| Caminho | Responsabilidade |
| --- | --- |
| [`web/src/main.jsx`](../web/src/main.jsx) | Estado e telas do dashboard, filtros, formulário de compra e chamadas HTTP. |
| [`web/src/style.css`](../web/src/style.css) | Estilos responsivos da interface. |
| [`web/vite.config.js`](../web/vite.config.js) | Endereço de escuta local e proxy das APIs/Swagger no desenvolvimento. |
| [`web/nginx.conf`](../web/nginx.conf) | Encaminhamento de rotas no contêiner web. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/FarmController.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/FarmController.java) | CRUD parcial de fazendas: criar, listar, consultar e editar. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/PurchaseController.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/PurchaseController.java) | Cadastro, consulta, edição e mudança de status de compras. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/AuthController.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/AuthController.java) | Login: sincroniza a lista de e-mails, gera e valida códigos, cria e encerra sessões. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/AuthFilter.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/AuthFilter.java) | Exige sessão nas rotas `/api/*` do Spring Boot. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/ResendMailer.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/ResendMailer.java) | Envio do código pela API HTTP do Resend; sem chave, registra o código no log. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/IntegrationSettings.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/IntegrationSettings.java) | Definição dos campos de cada integração, validação e leitura tela > `.env` > padrão. |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/IntegrationController.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/IntegrationController.java) | Rotas `/api/integrations` e ações do Evolution (estado, conectar, desconectar, teste). |
| [`services/purchases-api/src/main/java/br/com/intergado/purchases/EvolutionClient.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/EvolutionClient.java), [`WhatsAppNotifier.java`](../services/purchases-api/src/main/java/br/com/intergado/purchases/WhatsAppNotifier.java) | Chamadas HTTP ao Evolution e envio aos números de teste (em segundo plano na mudança de status). |
| [`Caddyfile`](../Caddyfile) | Caddy: endereço, modo de TLS e portas vindos do `.env`; redirecionamento explícito de HTTP para manter portas não padrão. |
| [`compose.evolution.yaml`](../compose.evolution.yaml) | Evolution API, PostgreSQL e Redis próprios, apenas para testes. |
| [`services/analytics-api/src/main/java/br/com/intergado/analytics/AuthFilter.java`](../services/analytics-api/src/main/java/br/com/intergado/analytics/AuthFilter.java) | Valida no Quarkus o mesmo cookie de sessão, consultando o SQLite. |
| [`services/analytics-api/src/main/java/br/com/intergado/analytics/AnalyticsResource.java`](../services/analytics-api/src/main/java/br/com/intergado/analytics/AnalyticsResource.java) | Agregações de compras para o dashboard. |
| [`services/purchases-api/src/main/resources/schema.sql`](../services/purchases-api/src/main/resources/schema.sql) | Tabelas, restrições e índices SQLite. |
| [`services/purchases-api/src/main/resources/data.sql`](../services/purchases-api/src/main/resources/data.sql) | Quatro fazendas e sete compras de exemplo, inseridas com `INSERT OR IGNORE`. |
| [`compose.yaml`](../compose.yaml) | Três serviços e volume persistente `sqlite_data`. |
| [`dev-purchases.sh`](../dev-purchases.sh), [`dev-quarkus.sh`](../dev-quarkus.sh) | Inicialização local das APIs com variáveis da raiz. |

No Compose, o Spring Boot deve ficar saudável antes da inicialização do Quarkus, e ambos devem ficar saudáveis antes do serviço web. Somente o serviço web publica uma porta no host. As APIs e o Swagger são acessados por meio do proxy web.

## 4. Modelo de dados atual

### `farms`

Campos: `id`, `name` único, `balance_date`, `herd_balance`, `adepara_balance`, `sources` e `demo`. A API aceita `name`, `balanceDate`, `herdBalance`, `adeparaBalance` e `sources`. Os saldos não podem ser negativos. O nome não pode se repetir. Ao renomear uma fazenda pela API, os registros de compra cujo campo `farm` corresponde exatamente ao nome anterior são atualizados.

Os quatro registros iniciais são:

| Fazenda | Data do último lançamento datado | Saldo da planilha | Saldo Adepará |
| --- | --- | ---: | ---: |
| Santa Cruz | 25/02/2021 | 2.881 | 2.676 |
| Ilha Verde | 15/02/2021 | 3.985 | 3.337 |
| Novo Mundo | 10/03/2021 | 2.271 | 2.617 |
| Campo Verde | 17/03/2021 | 923 | 895 |

Esses valores são **históricos e têm datas diferentes**. Não representam o tamanho atual dos rebanhos. A relação entre saldo interno e saldo Adepará ainda precisa de interpretação e conciliação operacional. O campo `sources` é texto separado por vírgulas; não existe tabela de fontes ou proveniência por linha.

### `purchase_requests`

Campos: `id`, `title`, `farm`, `category`, `requester`, `supplier`, `amount`, `status`, `requested_at`, `updated_at` e `demo`. `farm` ainda é texto, sem chave estrangeira para `farms`; a API de compras não valida se a fazenda informada já foi cadastrada. A listagem retorna no máximo 100 solicitações recentes e permite filtrar por status. Os sete registros iniciais são **ilustrativos**, não foram importados das planilhas. Bases antigas podem conservar compras com a fazenda fictícia “Boa Vista”; a inicialização não altera esses dados.

Os status disponíveis são `RECEIVED`, `QUOTING`, `AWAITING_APPROVAL`, `APPROVED`, `ORDERED`, `DELIVERED` e `PAID`. A API analítica conta como “em andamento” todos os registros fora de `DELIVERED` e `PAID`. Ela calcula total, aguardando aprovação, concluídos, valor estimado, valor pago, grupos por status/categoria/fazenda e contagem diária dos últimos sete dias. Os indicadores são de **compras**, não de produtividade ou estoque do gado.

### Autenticação: `users`, `login_codes`, `sessions`

`users` guarda `email` (minúsculo) e `active`. A cada inicialização, o Spring Boot marca como ativos apenas os e-mails de `AUTH_ALLOWED_EMAILS` e apaga as sessões dos demais. `login_codes` guarda o hash SHA-256 de `email:código`, tentativas, validade e uso; `sessions` guarda o hash SHA-256 do token do cookie, o e-mail e a validade. Nem códigos nem tokens ficam em texto puro no banco.

Regras: código de 6 dígitos válido por `AUTH_CODE_MINUTES` (10), no máximo 5 tentativas, uso único; pedir novo código invalida os anteriores, e só um código é gerado a cada 60 segundos por e-mail. O pedido sempre responde `202` com a mesma mensagem, autorizado ou não. Sessões duram `AUTH_SESSION_DAYS` (7). Sessões vencidas e códigos com mais de um dia são apagados a cada login bem-sucedido.

### `integration_settings`

Chave `(integration, name)`, `value`, `updated_at` e `updated_by` (e-mail da sessão). Só guarda valores alterados na tela; ao salvar um campo vazio, a linha é apagada e volta a valer o `.env`. Os campos possíveis são definidos em `IntegrationSettings.DEFINITIONS`; para um novo integrador, acrescente uma definição ali.

## 5. APIs e Swagger

| Método | Rota | Serviço | Finalidade |
| --- | --- | --- | --- |
| `POST` | `/api/auth/request-code` | Spring Boot | Pedir código (público; sempre `202`). |
| `POST` | `/api/auth/verify` | Spring Boot | Validar código e criar cookie de sessão (público). |
| `GET` | `/api/auth/me` | Spring Boot | E-mail da sessão atual. |
| `POST` | `/api/auth/logout` | Spring Boot | Encerrar sessão (público). |
| `GET` | `/api/integrations` | Spring Boot | Configuração das integrações. |
| `PUT` | `/api/integrations/{chave}` | Spring Boot | Salvar campos de uma integração. |
| `GET`, `POST` | `/api/integrations/evolution/{connection,connect,logout,test}` | Spring Boot | Estado, pareamento, desconexão e teste do WhatsApp. |
| `GET` | `/api/farms` | Spring Boot | Listar fazendas. |
| `GET` | `/api/farms/{id}` | Spring Boot | Consultar fazenda. |
| `POST` | `/api/farms` | Spring Boot | Cadastrar fazenda e saldos. |
| `PUT` | `/api/farms/{id}` | Spring Boot | Editar todos os campos de entrada. |
| `GET` | `/api/purchases` | Spring Boot | Listar até 100 compras; filtro opcional `status`. |
| `GET` | `/api/purchases/{id}` | Spring Boot | Consultar compra. |
| `POST` | `/api/purchases` | Spring Boot | Criar compra com status `RECEIVED`. |
| `PUT` | `/api/purchases/{id}` | Spring Boot | Editar título, fazenda, categoria, solicitante, fornecedor e valor. |
| `PUT` | `/api/purchases/{id}/status` | Spring Boot | Alterar status. |
| `GET` | `/api/analytics/overview` | Quarkus | Consultar indicadores agregados. |

Todas as rotas `/api/*`, exceto as três públicas de login, respondem `401` sem sessão válida. O Swagger, o OpenAPI e os health checks continuam públicos; as chamadas feitas pelo Swagger usam o cookie do navegador, então é preciso entrar no painel antes.

Com as portas padrão, o Swagger de cadastros e compras fica em `http://localhost:5173/swagger-ui/index.html` no desenvolvimento ou `http://localhost:3000/swagger-ui/index.html` no Docker. O Swagger de indicadores fica nos mesmos hosts em `/q/swagger-ui`. Os contratos OpenAPI estão em `/v3/api-docs` e `/q/openapi`, respectivamente. As APIs Java também expõem `/actuator/health` e `/q/health/ready` em suas portas internas.

## 6. Planilhas e decisões sobre os dados

Os quatro arquivos estão em [`docs/planilhas/`](planilhas/). A análise completa, com referências a abas e células, está em [AVALIACAO_PLANILHAS_DASHBOARDS.md](AVALIACAO_PLANILHAS_DASHBOARDS.md).

| Arquivo | Fazendas | Possível uso após tratamento |
| --- | --- | --- |
| `01_Controle_gado.xlsx` | Santa Cruz, Ilha Verde, Novo Mundo, Campo Verde | Histórico de movimentações e saldos por fazenda. |
| `01_Fichas_rotacionado_SC_M_JUL_2026.xlsx` | Santa Cruz | Manejo dos módulos e pastos, com separação entre ocorrido e planejado. |
| `01_Rendimento_bois_da_ração.xlsx` | Ilha Verde, Novo Mundo, Santa Cruz | Lotes e ganho de peso, após padronizar fórmulas e conciliar saídas. |
| `01_Aplicação_PT_SANTA_CRUZ_2024-25.xlsx` | Santa Cruz | Planejamento de área e quantidades de produtos, sem comprovação de aplicação realizada. |

Pontos de qualidade que precisam acompanhar qualquer importação futura:

- Há movimentações sem data e datas fora de ordem no controle de gado. Não atribuir datas automaticamente.
- A coluna `COMPRA` também contém registros descritos como nascimento ou desmama; a classificação exige leitura da descrição.
- As fichas de manejo misturam observações, fórmulas e projeções. Alguns caches de fórmulas estão desatualizados e há erros `#REF!` na aba `PARADO`.
- Um lote de ração pode ter mais de uma saída. As fórmulas de ganho diário não têm a mesma base em todas as abas.
- A planilha de aplicação fornece quantidades **planejadas**; faltam datas de execução, unidades confirmadas, preços e compras realizadas.

O modelo recomendado na avaliação inclui cadastro canônico de fazenda, pasto e módulo, movimentação de rebanho, ciclo de pastejo, lote e saída de ração, plano de tratamento e lote de importação com linha de origem. Nada disso além da tabela simples `farms` foi implementado ainda.

## 7. Execução e configuração

### Desenvolvimento local

Requisitos: Java 17, Maven para o Spring Boot, Node/npm para o React e acesso às dependências na primeira execução. O Quarkus usa o Maven Wrapper incluído em `services/analytics-api` (Maven 3.9.16). A partir da raiz, abra três terminais:

```bash
./dev-purchases.sh
./dev-quarkus.sh
cd web && npm install && npm run dev
```

Abra `http://localhost:5173` ou `http://IP_DA_MAQUINA:5173`. Os scripts Java leem [`.env.example`](../.env.example) depois que ele for copiado para `.env`; o Vite lê o `.env` da raiz diretamente. O SQLite local é `data/intergado.db`. O diretório `data/` e o arquivo `.env` são locais e não devem ser substituídos sem considerar os dados e ajustes do usuário.

| Variável | Valor padrão | Efeito |
| --- | --- | --- |
| `APP_LISTEN_HOST` | `0.0.0.0` | Endereço de escuta do Vite e bind publicado do Compose. |
| `APP_DEV_PORT` | `5173` | Porta do Vite. |
| `API_LISTEN_HOST` | `0.0.0.0` | Endereço de escuta das APIs Java no perfil de desenvolvimento. |
| `PURCHASES_API_PORT` | `8080` | Porta do Spring Boot em desenvolvimento e alvo do proxy Vite. |
| `ANALYTICS_API_PORT` | `8081` | Porta do Quarkus em desenvolvimento e alvo do proxy Vite. |
| `WEB_PORT` | `3000` | Porta publicada pelo Compose para o site. |
| `PURCHASES_API_URL` | derivada do host/porta | Alvo opcional do proxy Vite para Spring Boot. |
| `ANALYTICS_API_URL` | derivada do host/porta | Alvo opcional do proxy Vite para Quarkus. |
| `AUTH_ALLOWED_EMAILS` | vazio | E-mails autorizados, separados por vírgula. Vazio: ninguém entra. |
| `RESEND_API_KEY` | vazio | Chave do Resend. Vazio: o código só aparece no log do Spring Boot. |
| `AUTH_EMAIL_FROM` | `Intergado <onboarding@resend.dev>` | Remetente. O endereço de teste do Resend só entrega ao dono da conta. |
| `AUTH_COOKIE_SECURE` | `false` | `true` em HTTPS. |
| `AUTH_CODE_MINUTES`, `AUTH_SESSION_DAYS` | `10`, `7` | Validade do código e da sessão. |
| `CADDY_SITE_ADDRESS`, `CADDY_TLS`, `CADDY_HTTP_PORT`, `CADDY_HTTPS_PORT` | `localhost`, `auto`, `80`, `443` | Endereço, modo de certificado e portas do Caddy. |
| `EVOLUTION_*`, `WHATSAPP_*` | ver [EVOLUTION_API.md](EVOLUTION_API.md#variáveis) | Evolution API e WhatsApp; parte delas também editável na tela Integrações. |

### Docker Compose

```bash
cp .env.example .env
docker compose up --build -d
docker compose ps
```

Abra `http://localhost:3000`, salvo ajuste em `WEB_PORT`. O volume `sqlite_data` guarda o arquivo SQLite. `docker compose down` preserva o volume; `docker compose down -v` o remove e **apaga os dados da demonstração**. Os serviços Java no Compose mantêm portas internas 8080/8081; as variáveis `API_LISTEN_HOST` e `*_API_PORT` documentadas acima controlam o fluxo de desenvolvimento local.

## 8. Diagnóstico conhecido

| Sintoma | Causa já observada / verificação |
| --- | --- |
| `Detected Maven Version (3.8.7) is not supported` no Quarkus | Usar `./dev-quarkus.sh` ou `services/analytics-api/./mvnw quarkus:dev`, que usam Maven 3.9.16. |
| `No plugin found for prefix 'quarckus'` | Erro de digitação: o prefixo correto é `quarkus`. |
| `BindException: Endereço já em uso` na porta 8080 | Já há outro processo escutando na porta. Conferir com `ss -ltnp` e encerrar a instância duplicada ou mudar `PURCHASES_API_PORT` em `.env`. O mesmo princípio vale para 8081 e 5173. |
| Dashboard mostra “Serviços indisponíveis” | O React precisa das três respostas. Verificar Spring Boot, Quarkus, proxy e `GET /api/farms`, `/api/purchases`, `/api/analytics/overview`. |
| Painel volta para o login ou APIs respondem `401` | Sessão ausente, vencida ou e-mail removido de `AUTH_ALLOWED_EMAILS`. Entrar de novo. |
| Código não chega | Procurar `Falha ao enviar` no log do Spring Boot; conferir `RESEND_API_KEY` e domínio verificado do remetente. Sem chave, o código está no log (`Código de acesso para`). Novo código só após 60 s. |
| WhatsApp: “não conectado” ou “Evolution API não respondeu” | Parear em Integrações → Conectar; conferir se `compose.evolution.yaml` está ativo e se `EVOLUTION_API_KEY` é a mesma nos dois lados. Ver [EVOLUTION_API.md](EVOLUTION_API.md#comportamento-e-diagnóstico). |
| Caddy não sobe: `port is already allocated` | As portas 80/443 estão em uso (nesta máquina, pelo Caddy de outro projeto, `jev-caddy-1`). Ajustar `CADDY_HTTP_PORT`/`CADDY_HTTPS_PORT`; o `.env` local usa 8088/8443. Let's Encrypt exige 80/443 livres. |
| Aviso de certificado no navegador | Certificado interno do Caddy (localhost, IP ou `CADDY_TLS=internal`). Exportar e confiar em `/data/caddy/pki/authorities/local/root.crt` do contêiner `caddy`. |
| Contagens diferentes de 7 compras e 4 fazendas | O banco persiste entre reinicializações. As inserções iniciais usam `INSERT OR IGNORE`; registros já existentes são preservados. |

As verificações feitas durante a implementação incluíram build das duas APIs e do frontend, respostas `200` para OpenAPI/Swagger via proxy, cadastro e edição de fazenda e compra em **SQLite temporário**, retorno `409` para nome duplicado e atualização do nome da fazenda nas compras associadas. Isso não substitui uma execução de ponta a ponta no ambiente atual; não há indicação de que os serviços estejam ativos neste momento.

O login foi verificado em 30/10/2026 com os JARs compilados e um SQLite temporário: `401` sem sessão nas duas APIs, `202` idêntico para e-mail autorizado e não autorizado, `400` para e-mail inválido, intervalo de 60 s, código errado/reutilizado e bloqueio após 5 tentativas com `401`, login aceitando e-mail em maiúsculas, cookie `HttpOnly; SameSite=Lax`, acesso às duas APIs com sessão, `401` com token falso e após logout, desativação de e-mail removido da lista e falha do Resend (chave inválida) registrada no log sem deixar código pendente. O envio real por uma conta Resend não foi testado.

**Controle de versão:** a aplicação foi registrada no commit `fe1f439`. O `.env` e o banco `data/intergado.db` são dados locais e não devem ser incluídos por engano.

## 9. Limitações e próximos passos

1. **Importação auditável das planilhas:** criar entidades por linha, preservar arquivo/aba/célula, registrar erros, tratar datas Excel, distinguir planejado de realizado e impedir duplicação na reimportação.
2. **Modelo canônico:** relacionar compras à fazenda por identificador, cadastrar pastos/produtos/unidades e definir regras de mudança de nome. O vínculo atual usa texto.
3. **Dashboards rurais reais:** histórico de movimentos, manejo, ração e insumos só devem ser publicados após as regras de negócio e qualidade de dados acima.
4. **Interface de administração:** o React cadastra compras e altera status; cadastro/edição de fazendas e edição completa de compras são feitos pelo Swagger/API.
5. **Operação para múltiplos usuários:** o login por e-mail e o HTTPS (Caddy) existem, mas todos os usuários autorizados têm o mesmo acesso. Faltam perfis/permissões, cadastro de usuários pela interface, limite de pedidos por IP, backup, migrações de schema e testes de integração; avaliar PostgreSQL quando a demonstração evoluir para uso compartilhado.
6. **Integrações propostas:** o WhatsApp tem só a Fase 1 (envio de teste via Evolution API). Recebimento de mensagens, IA, OpenSearch, fornecedores e Realtec constam da documentação de oportunidades, mas não estão implementados.
7. **Tela Integrações:** qualquer usuário logado pode alterá-la; falta perfil de administrador e histórico de alterações (só o último `updated_by` fica gravado).

As dúvidas de negócio que bloqueiam indicadores mais fortes estão listadas no fim de [AVALIACAO_PLANILHAS_DASHBOARDS.md](AVALIACAO_PLANILHAS_DASHBOARDS.md): confirmação de eventos realizados, definição do rendimento da ração, significado das diferenças de saldos, unidades dos produtos e identificadores oficiais de pastos/módulos.

## 10. Histórico resumido de decisões

1. Criar uma demonstração web com React, Spring Boot e Quarkus, organizada em três serviços.
2. Trocar o banco de demonstração para SQLite para dispensar instalação de PostgreSQL.
3. Permitir que frontend e APIs escutem em `0.0.0.0` no desenvolvimento e configurar host/portas via `.env`.
4. Adicionar Maven Wrapper ao Quarkus após incompatibilidade do Maven global 3.8.7.
5. Avaliar as quatro planilhas, identificar as quatro fazendas e exibir seus saldos históricos de forma separada.
6. Mover o resumo de fazendas para a API/SQLite, permitir escrita por API e adicionar Swagger aos dois serviços.
7. Registrar arquitetura e sequência em Mermaid e SVG.
8. Adicionar login sem senha: código de 6 dígitos enviado via Resend, lista de e-mails autorizados por variável de ambiente e todas as rotas `/api/*` protegidas nas duas APIs (sem Spring Security; filtro próprio e sessão no SQLite).
9. Fase 1 do WhatsApp: Evolution API 2.3.7 em Compose separado, tela **Integrações** para cadastrar dados dos integradores (valor da tela > `.env` > padrão, segredos só no `.env`), mensagens apenas para números de teste.
10. Adicionar Caddy na frente do Nginx para HTTPS, com endereço, modo de certificado e portas no `.env`; o Nginx e as rotas não mudaram.

Este arquivo é um retrato do projeto, não uma fonte alternativa de dados operacionais. Ao alterar APIs, modelo, configuração ou importação, atualize também esta memória, o [README](../README.md), o [roteiro](ROTEIRO_AVALIACAO_APP.md) e os diagramas afetados.
