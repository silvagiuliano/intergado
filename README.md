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

O Nginx encaminha `/api/purchases` ao Spring Boot e `/api/analytics` ao Quarkus. Os serviços Java usam o mesmo arquivo; o Spring Boot cria as tabelas e os dados de demonstração antes de o Quarkus iniciar. Não é necessário instalar ou executar um servidor de banco de dados. Os dados iniciais são **apenas exemplos**, identificados no banco e no painel.

O bloco **Fazendas** consulta `GET /api/farms`. A base nova contém Santa Cruz, Ilha Verde, Novo Mundo e Campo Verde, com o último saldo datado e o saldo Adepará de cada aba de `docs/planilhas/01_Controle_gado.xlsx`. Os registros iniciais são de 2021 e podem ser editados pela API. Ainda não há importação automática de todas as linhas das planilhas. As compras demonstrativas são independentes desses saldos. Em bases SQLite antigas, exemplos com a fazenda fictícia `Boa Vista` permanecem intactos; novas bases usam os nomes das quatro fazendas nos exemplos.

## Iniciar com Docker

Requisitos: Docker Engine e Docker Compose plugin.

```bash
cp .env.example .env
docker compose up --build -d
```

Abra **http://localhost:3000**. `APP_LISTEN_HOST` e `WEB_PORT` em `.env` controlam o endereço e a porta publicados pelo Docker Compose. Para ver logs: `docker compose logs -f`. Para parar: `docker compose down`. O arquivo SQLite persiste no volume `sqlite_data`; `docker compose down -v` também remove os dados.

Para produção, são necessários autenticação, HTTPS, backup e controle de acesso; esta entrega é um ambiente inicial de demonstração.

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

O Vite redireciona `/api/purchases` e `/api/analytics` para as portas configuradas. Se as APIs estiverem em outra máquina, defina também `PURCHASES_API_URL` e `ANALYTICS_API_URL` em `.env`. Para acessar pela rede, abra `http://IP_DA_MAQUINA:5173`. Ambos os serviços Java usam `data/intergado.db` por padrão, relativo à raiz do projeto. Em Docker, o arquivo fica no volume `sqlite_data`.

Se o painel mostrar **“Serviços indisponíveis”**, confirme que os dois serviços estão ativos: `curl http://127.0.0.1:8080/actuator/health` e `curl http://127.0.0.1:8081/q/health/ready` (ajuste as portas se as alterou).

O Quarkus exige Maven 3.9 ou superior. `./dev-quarkus.sh` usa o Maven Wrapper do serviço, que baixa e executa Maven 3.9.16 na primeira execução. O comando global `mvn` continua na versão instalada no sistema; para conferir a versão usada pelo projeto, execute `./dev-quarkus.sh --version` na raiz.

## Endpoints

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/api/purchases` | Até 100 solicitações recentes; aceita `?status=RECEIVED` |
| GET | `/api/purchases/{id}` | Detalhe de uma solicitação |
| POST | `/api/purchases` | Cria uma solicitação com `title`, `farm`, `category`, `requester`, `supplier` opcional e `amount` |
| PUT | `/api/purchases/{id}` | Edita todos os campos de entrada da solicitação |
| PUT | `/api/purchases/{id}/status` | Atualiza com `{ "status": "QUOTING" }` |
| GET | `/api/farms` | Lista fazendas e seus saldos datados |
| GET | `/api/farms/{id}` | Detalhe de uma fazenda |
| POST | `/api/farms` | Cadastra fazenda, data, saldos e fontes |
| PUT | `/api/farms/{id}` | Edita a fazenda; ao renomeá-la, atualiza o nome nas compras associadas |
| GET | `/api/analytics/overview` | Totais, valores e séries para o dashboard |
| GET | `/actuator/health` | Saúde do Spring Boot |
| GET | `/q/health/ready` | Prontidão do Quarkus |

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
