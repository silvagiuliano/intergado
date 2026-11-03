# Intergado — dashboard rural e de compras

Aplicação web para acompanhar solicitações de compras rurais e visualizar um recorte histórico das fazendas identificadas nas planilhas.

Para experimentar a aplicação, siga o [roteiro de avaliação](docs/ROTEIRO_AVALIACAO_APP.md).

Para entender o estado atual, as decisões e as pendências, consulte a [memória do projeto](docs/MEMORIA_PROJETO.md).

Veja também os [diagramas de arquitetura e sequência](docs/DIAGRAMAS_ARQUITETURA_SEQUENCIA.md).

Para publicar em um servidor com HTTPS, siga o [guia de deploy](docs/DEPLOY.md).

## Arquitetura

| Componente | Tecnologia | Papel |
| --- | --- | --- |
| `web` | React 19 + Vite + Nginx | Dashboard, filtros, cadastro e atualização de status |
| `purchases-api` | Java 17 + Spring Boot 4 + Maven | API de fazendas e solicitações; inicialização do SQLite; Swagger de cadastros |
| `analytics-api` | Java 17 + Quarkus 3 + Maven | Indicadores agregados e Swagger de consultas |
| `sqlite_data` | Arquivo SQLite em volume Docker | Persistência compartilhada entre os serviços |
| Caddy | Caddy geral do host (ou o serviço `caddy` do profile `standalone`) | HTTPS na frente do Nginx, com certificado automático e redirecionamento de HTTP |

O Nginx encaminha `/api/auth`, `/api/users`, `/api/integrations`, `/api/farms` e `/api/purchases` ao Spring Boot e `/api/analytics` ao Quarkus. Os serviços Java usam o mesmo arquivo; o Spring Boot cria as tabelas e os dados de demonstração antes de o Quarkus iniciar. Não é necessário instalar ou executar um servidor de banco de dados. Os dados iniciais são **apenas exemplos**, identificados no banco e no painel.

O bloco **Fazendas** consulta `GET /api/farms`. A base nova contém Santa Cruz, Ilha Verde, Novo Mundo e Campo Verde, com o último saldo datado e o saldo Adepará de cada aba de `docs/planilhas/01_Controle_gado.xlsx`. Os registros iniciais são de 2021. Operadores e administradores cadastram e editam fazendas no próprio painel (**Nova fazenda** e **Editar** nos cartões) ou pela API. Ainda não há importação automática de todas as linhas das planilhas. As compras demonstrativas são independentes desses saldos. Em bases SQLite antigas, exemplos com a fazenda fictícia `Boa Vista` permanecem intactos; novas bases usam os nomes das quatro fazendas nos exemplos.

## Integrações e WhatsApp (testes)

A tela **Integrações**, no menu lateral do painel, mostra e edita os dados dos serviços externos: o WhatsApp pelo [Evolution API](docs/EVOLUTION_API.md) e o remetente do Resend. O valor salvo na tela tem prioridade sobre o `.env`; chaves e senhas ficam só no `.env`.

O Evolution API roda em um Compose separado, só para testes:

```bash
docker compose -f compose.yaml -f compose.evolution.yaml up -d   # aplicação + Evolution
docker compose -f compose.evolution.yaml up -d                   # só o Evolution, para os scripts dev-*.sh
```

Defina `EVOLUTION_API_KEY` no `.env` antes. O passo a passo de pareamento e teste está em [docs/EVOLUTION_API.md](docs/EVOLUTION_API.md). As mensagens só vão para os números de teste e os grupos cadastrados na tela.

## Iniciar com Docker

Requisitos: Docker Engine e Docker Compose plugin.

```bash
cp .env.example .env
docker compose up --build -d
```

Abra **http://localhost:3000** na própria máquina: o Nginx é publicado só em `127.0.0.1:WEB_PORT`. O acesso pela rede passa pelo Caddy (seção abaixo). Em servidor, prefira `./scripts/deploy-compose.sh`, que valida o `.env` e confere os health checks. Para ver logs: `docker compose logs -f`. Para parar: `docker compose down`. O arquivo SQLite persiste no volume `sqlite_data`; `docker compose down -v` também remove os dados.

Para produção, ainda são necessários backup automático e histórico de alterações; esta entrega é um ambiente inicial de demonstração. O passo a passo para um servidor está em [docs/DEPLOY.md](docs/DEPLOY.md).

## Login por e-mail

O acesso usa um código de 6 dígitos enviado por e-mail pelo [Resend](https://resend.com). Antes de iniciar, preencha no `.env`:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `AUTH_ADMIN_EMAILS` | vazio | Administradores iniciais, separados por vírgula (ver [Usuários e perfis](#usuários-e-perfis)). Nome antigo aceito: `AUTH_ALLOWED_EMAILS` |
| `RESEND_API_KEY` | vazio | Chave da API do Resend. Vazio: o código aparece só no log do Spring Boot |
| `AUTH_EMAIL_FROM` | `Intergado <onboarding@resend.dev>` | Remetente; use um domínio verificado no Resend |
| `AUTH_COOKIE_SECURE` | `false` | Use `true` quando o site estiver em HTTPS |
| `AUTH_CODE_MINUTES` | `10` | Validade do código |
| `AUTH_SESSION_DAYS` | `7` | Validade da sessão |

No `.env`, mantenha o remetente entre aspas, como em `AUTH_EMAIL_FROM="Intergado <login@seu-dominio.com.br>"`: sem elas, o `<` quebra a leitura do arquivo pelos scripts `dev-*.sh`.

O remetente de teste `onboarding@resend.dev` só entrega para o e-mail dono da conta Resend. Para outros destinatários, verifique um domínio no Resend.

Só entram e-mails ativos da tabela `users`, cadastrados na tela **Usuários** ou criados por `AUTH_ADMIN_EMAILS`. O código expira em 10 minutos, aceita 5 tentativas e só pode ser usado uma vez; um novo código só é gerado 60 segundos após o anterior. A resposta ao pedido de código é sempre a mesma, para não revelar quais e-mails estão autorizados. Se o envio pelo Resend falhar, o erro aparece no log do Spring Boot.

A sessão é um cookie `HttpOnly` (`intergado_session`, `SameSite=Lax`) guardado como hash na tabela `sessions`. O Spring Boot e o Quarkus validam esse cookie em todas as rotas `/api/*`, exceto `request-code`, `verify` e `logout`. O Swagger continua abrindo, mas as chamadas só funcionam depois do login no mesmo navegador.

## Usuários e perfis

Quem pode entrar e o que cada um pode fazer é definido na tela **Usuários** (menu lateral, só para administradores). Não há senha: o acesso é sempre pelo código enviado ao e-mail cadastrado.

| Perfil | Pode |
| --- | --- |
| `LEITOR` | Consultar painel, fazendas, compras e indicadores |
| `OPERADOR` | Também cadastrar e editar compras e fazendas e alterar status |
| `ADMIN` | Também cadastrar usuários, mudar perfis, desativar acessos e editar integrações |

- **Administrador inicial:** os e-mails de `AUTH_ADMIN_EMAILS` são criados como `ADMIN` na inicialização, se ainda não existirem. Depois disso, a tela manda: outro administrador pode mudar o perfil ou desativar esse e-mail, e o `.env` não o reativa.
- **Recuperação:** se não restar nenhum administrador ativo, os e-mails de `AUTH_ADMIN_EMAILS` voltam a ser administradores ativos no próximo reinício do `purchases-api`.
- **Proteções:** ninguém muda o próprio perfil nem desativa o próprio acesso, e o último administrador ativo não pode ser rebaixado nem desativado.
- **Efeito imediato:** o perfil é lido a cada requisição; desativar um usuário encerra as sessões dele. Sem permissão, a API responde `403`.
- Usuários são desativados, não apagados, e ficam registrados quem criou, quem alterou por último e o último acesso.

Bases criadas antes dos perfis são migradas na inicialização: os usuários existentes passam a ser `ADMIN`, como era o acesso deles até então. `AUTH_ALLOWED_EMAILS`, o nome antigo da variável, continua aceito.

## HTTPS com Caddy

Mesmo padrão do projeto `jev`, com dois modos de borda (detalhes em [caddy/README.md](caddy/README.md)):

| Modo | Como ativar | Quem escuta 80/443 |
| --- | --- | --- |
| **Caddy do host (padrão)** | `COMPOSE_PROFILES` sem `standalone` | `caddy.service` do host, que atende todos os projetos |
| Caddy do projeto | `COMPOSE_PROFILES=standalone` no `.env` | o serviço `caddy` deste Compose |

**Caddy do host.** O Nginx fica só em `127.0.0.1:WEB_PORT`. O site é gerado do `.env` a partir de [`caddy/host/intergado.caddy.template`](caddy/host/intergado.caddy.template) e instalado em `/etc/caddy/sites/intergado.caddy`:

```bash
./scripts/install-host-caddy.sh --print   # só mostra o site gerado
./scripts/install-host-caddy.sh           # instala, valida como usuário caddy e recarrega (usa sudo)
```

O `/etc/caddy/Caddyfile` do host precisa de `import /etc/caddy/sites/*.caddy`. `CADDY_DOMAIN` é o domínio real ou um nome local como `intergado.localhost`; `localhost` puro é recusado, porque capturaria todos os projetos do host. Instalação do Caddy no Ubuntu 24.04: [docs/DEPLOY.md](docs/DEPLOY.md), seção 1.1.

**Caddy do projeto (`standalone`).** Para máquinas sem Caddy no host:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `CADDY_DOMAIN` | `localhost` | Domínio atendido. Domínio real: certificado Let's Encrypt; `localhost`: certificado interno |
| `CADDYFILE` | `./caddy/Caddyfile` | `./caddy/Caddyfile` (HTTPS) ou `./caddy/Caddyfile.local` (HTTP sem TLS, porta 80) |
| `CADDY_HTTP_PORT`, `CADDY_HTTPS_PORT` | `80`, `443` | Portas publicadas. Em produção mantenha 80 e 443 |

Nos dois modos, com acesso por HTTPS, use `AUTH_COOKIE_SECURE=true` para o cookie de sessão só trafegar em HTTPS. Com `true`, o login deixa de funcionar pelo HTTP direto (`http://127.0.0.1:WEB_PORT`) e pelos scripts de desenvolvimento, que usam o mesmo `.env`.

## Desenvolvimento local

Sem Docker, abra três terminais na raiz do projeto:

```bash
./dev-purchases.sh
```

```bash
./dev-quarkus.sh
```

```bash
cd web && npm install && npm run dev
```

Os scripts criam `data/` e leem `.env` da raiz. Você pode alterar os endereços e portas nesse arquivo:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `APP_LISTEN_HOST` | `0.0.0.0` | Endereço de escuta do Vite e endereço publicado pelo Compose |
| `APP_DEV_PORT` | `5173` | Porta do Vite |
| `API_LISTEN_HOST` | `0.0.0.0` | Endereço de escuta das duas APIs Java em dev |
| `PURCHASES_API_PORT` | `8080` | Porta do Spring Boot em dev |
| `ANALYTICS_API_PORT` | `8081` | Porta do Quarkus em dev |
| `WEB_PORT` | `3000` | Porta do site em Docker Compose |

O Vite redireciona `/api/auth`, `/api/users`, `/api/integrations`, `/api/farms`, `/api/purchases` e `/api/analytics` para as portas configuradas. Se as APIs estiverem em outra máquina, defina também `PURCHASES_API_URL` e `ANALYTICS_API_URL` em `.env`. Para acessar pela rede, abra `http://IP_DA_MAQUINA:5173`. Ambos os serviços Java usam `data/intergado.db` por padrão, relativo à raiz do projeto. Em Docker, o arquivo fica no volume `sqlite_data`.

Se o painel mostrar **“Serviços indisponíveis”**, confirme que os dois serviços estão ativos: `curl http://127.0.0.1:8080/actuator/health` e `curl http://127.0.0.1:8081/q/health/ready` (ajuste as portas se as alterou).

O Quarkus exige Maven 3.9 ou superior. `./dev-quarkus.sh` usa o Maven Wrapper do serviço, que baixa e executa Maven 3.9.16 na primeira execução. O comando global `mvn` continua na versão instalada no sistema; para conferir a versão usada pelo projeto, execute `./dev-quarkus.sh --version` na raiz.

## Endpoints

| Método | Rota | Uso |
| --- | --- | --- |
| POST | `/api/auth/request-code` | Envia código para `{ "email": "..." }` se o e-mail estiver autorizado (sempre `202`) |
| POST | `/api/auth/verify` | Valida `{ "email": "...", "code": "123456" }` e cria o cookie de sessão |
| GET | `/api/auth/me` | E-mail, nome e perfil da sessão atual |
| POST | `/api/auth/logout` | Encerra a sessão |
| GET | `/api/purchases` | Até 100 solicitações recentes; aceita `?status=RECEIVED` |
| GET | `/api/purchases/{id}` | Detalhe de uma solicitação |
| POST | `/api/purchases` | Cria uma solicitação com `title`, `farm`, `category`, `requester`, `supplier` opcional e `amount` |
| PUT | `/api/purchases/{id}` | Edita todos os campos de entrada da solicitação |
| PUT | `/api/purchases/{id}/status` | Atualiza com `{ "status": "QUOTING" }` |
| GET | `/api/farms` | Lista fazendas e seus saldos datados |
| GET | `/api/farms/{id}` | Detalhe de uma fazenda |
| POST | `/api/farms` | Cadastra fazenda, data, saldos e fontes |
| PUT | `/api/farms/{id}` | Edita a fazenda; ao renomeá-la, atualiza o nome nas compras associadas |
| GET | `/api/users` | Usuários, perfis e situação (`ADMIN`) |
| GET | `/api/users/roles` | Perfis disponíveis e o que cada um pode (`ADMIN`) |
| POST | `/api/users` | Cadastra `{ "email", "name", "role" }` (`ADMIN`) |
| PUT | `/api/users/{email}` | Altera `name`, `role` ou `active` (`ADMIN`) |
| GET | `/api/integrations` | Configuração das integrações (Evolution, Resend) (`ADMIN`) |
| PUT | `/api/integrations/{chave}` | Salva campos de uma integração |
| GET/POST | `/api/integrations/evolution/...` | `connection`, `connect`, `groups`, `logout` e `test` do WhatsApp |
| GET | `/api/analytics/overview` | Totais, valores e séries para o dashboard |
| GET | `/actuator/health` | Saúde do Spring Boot |
| GET | `/q/health/ready` | Prontidão do Quarkus |

As demais rotas `/api/*` respondem `401` sem sessão válida e `403` quando o perfil não permite: leituras para todos, escrita em compras e fazendas a partir de `OPERADOR`, usuários e integrações só `ADMIN`.

Status disponíveis: `RECEIVED`, `QUOTING`, `AWAITING_APPROVAL`, `APPROVED`, `ORDERED`, `DELIVERED`, `PAID`.

### Swagger / OpenAPI

Com a aplicação no Docker, abra:

- **Cadastros e compras:** [http://localhost:3000/swagger-ui/index.html](http://localhost:3000/swagger-ui/index.html) — contrato JSON em `/v3/api-docs`.
- **Indicadores:** [http://localhost:3000/q/swagger-ui](http://localhost:3000/q/swagger-ui) — contrato em `/q/openapi`.

No desenvolvimento local, substitua a porta `3000` pela `APP_DEV_PORT` (padrão `5173`). Os links também aparecem na navegação lateral do dashboard. `POST /api/farms` aceita, por exemplo:

```json
{
  "name": "Fazenda Exemplo",
  "balanceDate": "2026-10-25",
  "herdBalance": 100,
  "adeparaBalance": 98,
  "sources": "Lançamento manual"
}
```

O Swagger permite enviar os dados e conferir as respostas sem instalar outro cliente HTTP. As rotas de escrita atuais abrangem os campos usados no painel: fazendas, saldos resumidos, compras e status. Movimentações individuais de gado, ciclos de manejo, lotes de ração e planejamento de aplicação ainda precisam de modelo e importação próprios.

As integrações com WhatsApp, IA, OpenSearch, fornecedores e Realtec descritas na documentação são oportunidades futuras e ainda não foram implementadas neste primeiro painel.
