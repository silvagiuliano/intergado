# Intergado — dashboard rural e de compras

Aplicação web para acompanhar solicitações de compras rurais e visualizar um recorte histórico das fazendas identificadas nas planilhas.

Para experimentar a aplicação, siga o [roteiro de avaliação](docs/ROTEIRO_AVALIACAO_APP.md).

Para entender o estado atual, as decisões e as pendências, consulte a [memória do projeto](docs/MEMORIA_PROJETO.md).

Veja também os [diagramas de arquitetura e sequência](docs/DIAGRAMAS_ARQUITETURA_SEQUENCIA.md).

## Arquitetura

| Componente | Tecnologia | Papel |
| --- | --- | --- |
| `web` | React 19 + Vite + Nginx | Dashboard, filtros, cadastro e atualização de status |
| `purchases-api` | Java 17 + Spring Boot 4 + Maven | API de fazendas e solicitações; inicialização do SQLite; Swagger de cadastros |
| `analytics-api` | Java 17 + Quarkus 3 + Maven | Indicadores agregados e Swagger de consultas |
| `sqlite_data` | Arquivo SQLite em volume Docker | Persistência compartilhada entre os serviços |
| `caddy` | Caddy 2.11 | HTTPS na frente do Nginx, com certificado automático e redirecionamento de HTTP |

O Nginx encaminha `/api/auth`, `/api/integrations`, `/api/farms` e `/api/purchases` ao Spring Boot e `/api/analytics` ao Quarkus. Os serviços Java usam o mesmo arquivo; o Spring Boot cria as tabelas e os dados de demonstração antes de o Quarkus iniciar. Não é necessário instalar ou executar um servidor de banco de dados. Os dados iniciais são **apenas exemplos**, identificados no banco e no painel.

O bloco **Fazendas** consulta `GET /api/farms`. A base nova contém Santa Cruz, Ilha Verde, Novo Mundo e Campo Verde, com o último saldo datado e o saldo Adepará de cada aba de `docs/planilhas/01_Controle_gado.xlsx`. Os registros iniciais são de 2021 e podem ser editados pela API. Ainda não há importação automática de todas as linhas das planilhas. As compras demonstrativas são independentes desses saldos. Em bases SQLite antigas, exemplos com a fazenda fictícia `Boa Vista` permanecem intactos; novas bases usam os nomes das quatro fazendas nos exemplos.

## Integrações e WhatsApp (testes)

A tela **Integrações**, no menu lateral do painel, mostra e edita os dados dos serviços externos: o WhatsApp pelo [Evolution API](docs/EVOLUTION_API.md) e o remetente do Resend. O valor salvo na tela tem prioridade sobre o `.env`; chaves e senhas ficam só no `.env`.

O Evolution API roda em um Compose separado, só para testes:

```bash
docker compose -f compose.yaml -f compose.evolution.yaml up -d   # aplicação + Evolution
docker compose -f compose.evolution.yaml up -d                   # só o Evolution, para os scripts dev-*.sh
```

Defina `EVOLUTION_API_KEY` no `.env` antes. O passo a passo de pareamento e teste está em [docs/EVOLUTION_API.md](docs/EVOLUTION_API.md). As mensagens só vão para os números de teste cadastrados.

## Iniciar com Docker

Requisitos: Docker Engine e Docker Compose plugin.

```bash
cp .env.example .env
docker compose up --build -d
```

Abra **http://localhost:3000**. `APP_LISTEN_HOST` e `WEB_PORT` em `.env` controlam o endereço e a porta publicados pelo Docker Compose. Para ver logs: `docker compose logs -f`. Para parar: `docker compose down`. O arquivo SQLite persiste no volume `sqlite_data`; `docker compose down -v` também remove os dados.

Para produção, ainda são necessários HTTPS, backup e perfis de permissão; esta entrega é um ambiente inicial de demonstração.

## Login por e-mail

O acesso usa um código de 6 dígitos enviado por e-mail pelo [Resend](https://resend.com). Antes de iniciar, preencha no `.env`:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `AUTH_ALLOWED_EMAILS` | vazio | E-mails autorizados, separados por vírgula. Vazio: ninguém entra |
| `RESEND_API_KEY` | vazio | Chave da API do Resend. Vazio: o código aparece só no log do Spring Boot |
| `AUTH_EMAIL_FROM` | `Intergado <onboarding@resend.dev>` | Remetente; use um domínio verificado no Resend |
| `AUTH_COOKIE_SECURE` | `false` | Use `true` quando o site estiver em HTTPS |
| `AUTH_CODE_MINUTES` | `10` | Validade do código |
| `AUTH_SESSION_DAYS` | `7` | Validade da sessão |

No `.env`, mantenha o remetente entre aspas, como em `AUTH_EMAIL_FROM="Intergado <login@seu-dominio.com.br>"`: sem elas, o `<` quebra a leitura do arquivo pelos scripts `dev-*.sh`.

O remetente de teste `onboarding@resend.dev` só entrega para o e-mail dono da conta Resend. Para outros destinatários, verifique um domínio no Resend.

A lista de e-mails é sincronizada com a tabela `users` a cada inicialização do Spring Boot; quem sai da lista perde as sessões abertas. O código expira em 10 minutos, aceita 5 tentativas e só pode ser usado uma vez; um novo código só é gerado 60 segundos após o anterior. A resposta ao pedido de código é sempre a mesma, para não revelar quais e-mails estão autorizados. Se o envio pelo Resend falhar, o erro aparece no log do Spring Boot.

A sessão é um cookie `HttpOnly` (`intergado_session`, `SameSite=Lax`) guardado como hash na tabela `sessions`. O Spring Boot e o Quarkus validam esse cookie em todas as rotas `/api/*`, exceto `request-code`, `verify` e `logout`. O Swagger continua abrindo, mas as chamadas só funcionam depois do login no mesmo navegador.

## HTTPS com Caddy

No Docker Compose, o serviço `caddy` recebe as conexões em HTTPS e as encaminha ao Nginx (`web`). A configuração fica no [`Caddyfile`](Caddyfile) e nestas variáveis do `.env`:

| Variável | Padrão | Uso |
| --- | --- | --- |
| `CADDY_SITE_ADDRESS` | `localhost` | Endereço atendido: `localhost`, um IP ou um domínio. Vários: `"localhost, 192.168.1.10"` (vírgula **e** espaço) |
| `CADDY_TLS` | `auto` | `auto`: Let's Encrypt para domínio público, certificado interno para `localhost`/IP. `internal`: sempre o certificado interno (rede local, nomes não públicos) |
| `CADDY_HTTP_PORT`, `CADDY_HTTPS_PORT` | `80`, `443` | Portas escutadas e publicadas. HTTP redireciona para HTTPS mantendo a porta |

Com os padrões, abra **https://localhost**. Para um domínio público, aponte o DNS para o servidor, mantenha as portas 80 e 443 abertas e defina `CADDY_SITE_ADDRESS=seu.dominio.com.br`: o certificado é emitido e renovado sozinho. Os certificados ficam no volume `caddy_data`.

Com o certificado interno, o navegador avisa até que a CA local do Caddy seja confiável. Para exportá-la: `docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-root.crt` e importe o arquivo no navegador ou no sistema.

Quando todo o acesso passar pelo Caddy, use `AUTH_COOKIE_SECURE=true` para o cookie de sessão só trafegar em HTTPS. Com `true`, o login deixa de funcionar pelo HTTP direto (`WEB_PORT`) e pelos scripts de desenvolvimento, que usam o mesmo `.env`. A porta `WEB_PORT` continua publicada para acesso direto ao Nginx em HTTP.

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

O Vite redireciona `/api/auth`, `/api/integrations`, `/api/farms`, `/api/purchases` e `/api/analytics` para as portas configuradas. Se as APIs estiverem em outra máquina, defina também `PURCHASES_API_URL` e `ANALYTICS_API_URL` em `.env`. Para acessar pela rede, abra `http://IP_DA_MAQUINA:5173`. Ambos os serviços Java usam `data/intergado.db` por padrão, relativo à raiz do projeto. Em Docker, o arquivo fica no volume `sqlite_data`.

Se o painel mostrar **“Serviços indisponíveis”**, confirme que os dois serviços estão ativos: `curl http://127.0.0.1:8080/actuator/health` e `curl http://127.0.0.1:8081/q/health/ready` (ajuste as portas se as alterou).

O Quarkus exige Maven 3.9 ou superior. `./dev-quarkus.sh` usa o Maven Wrapper do serviço, que baixa e executa Maven 3.9.16 na primeira execução. O comando global `mvn` continua na versão instalada no sistema; para conferir a versão usada pelo projeto, execute `./dev-quarkus.sh --version` na raiz.

## Endpoints

| Método | Rota | Uso |
| --- | --- | --- |
| POST | `/api/auth/request-code` | Envia código para `{ "email": "..." }` se o e-mail estiver autorizado (sempre `202`) |
| POST | `/api/auth/verify` | Valida `{ "email": "...", "code": "123456" }` e cria o cookie de sessão |
| GET | `/api/auth/me` | E-mail da sessão atual |
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
| GET | `/api/integrations` | Configuração das integrações (Evolution, Resend) |
| PUT | `/api/integrations/{chave}` | Salva campos de uma integração |
| GET/POST | `/api/integrations/evolution/...` | `connection`, `connect`, `logout` e `test` do WhatsApp |
| GET | `/api/analytics/overview` | Totais, valores e séries para o dashboard |
| GET | `/actuator/health` | Saúde do Spring Boot |
| GET | `/q/health/ready` | Prontidão do Quarkus |

As demais rotas `/api/*` respondem `401` sem sessão válida.

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
