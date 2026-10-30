# Diagramas da aplicação Intergado

Os diagramas abaixo representam a implementação atual com Docker Compose e SQLite. Em desenvolvimento local, o Vite serve o React na porta `APP_DEV_PORT` e encaminha as mesmas rotas para as APIs.

Versões para abrir ou compartilhar: [arquitetura em SVG](ARQUITETURA_APP.svg) e [sequência em SVG](SEQUENCIA_APP.svg).

## Arquitetura

```mermaid
flowchart LR
    U["Usuário<br/>navegador"] -->|HTTP: 3000 no Docker| W

    subgraph Compose["Docker Compose"]
        W["Web<br/>Nginx + React"]
        P["API de cadastros<br/>Spring Boot / Java<br/>porta 8080"]
        A["API de indicadores<br/>Quarkus / Java<br/>porta 8081"]
        DB[("SQLite<br/>volume sqlite_data")]

        W -->|"/api/farms e /api/purchases<br/>/swagger-ui e /v3/api-docs"| P
        W -->|"/api/analytics<br/>/q/swagger-ui e /q/openapi"| A
        P -->|"cadastro, edição e consulta"| DB
        A -->|"consulta e agregação"| DB
    end

    XLSX["Planilhas em docs/planilhas"] -.->|"resumo histórico extraído na preparação dos dados"| SEED["schema.sql + data.sql"]
    SEED -.->|"inicialização do SQLite"| P
```

As APIs compartilham o mesmo arquivo SQLite. O Spring Boot cria as tabelas e os registros iniciais; o Quarkus lê os dados para calcular indicadores. Os quatro saldos de fazendas cadastrados inicialmente são históricos, de 2021. As planilhas XLSX **não são importadas automaticamente** a cada execução.

## Sequência: abrir o painel e criar uma solicitação

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário
    participant React as Navegador / React
    participant Web as Nginx ou Vite
    participant Spring as Spring Boot
    participant Quarkus as Quarkus
    participant SQLite as SQLite

    Usuario->>React: Abre o dashboard
    React->>Web: GET /
    Web-->>React: HTML, JavaScript e CSS

    par Fazendas
        React->>Web: GET /api/farms
        Web->>Spring: Encaminha GET /api/farms
        Spring->>SQLite: SELECT farms
        SQLite-->>Spring: Fazendas e saldos datados
        Spring-->>Web: JSON das fazendas
        Web-->>React: JSON das fazendas
    and Compras
        React->>Web: GET /api/purchases
        Web->>Spring: Encaminha GET /api/purchases
        Spring->>SQLite: SELECT purchase_requests
        SQLite-->>Spring: Solicitações recentes
        Spring-->>Web: JSON das compras
        Web-->>React: JSON das compras
    and Indicadores
        React->>Web: GET /api/analytics/overview
        Web->>Quarkus: Encaminha GET /api/analytics/overview
        Quarkus->>SQLite: Consultas agregadas de compras
        SQLite-->>Quarkus: Totais, grupos e datas
        Quarkus-->>Web: JSON dos indicadores
        Web-->>React: JSON dos indicadores
    end

    React-->>Usuario: Exibe fazendas, compras e indicadores

    Usuario->>React: Preenche e envia nova solicitação
    React->>Web: POST /api/purchases
    Web->>Spring: Encaminha POST /api/purchases
    Spring->>SQLite: INSERT purchase_requests
    SQLite-->>Spring: ID e registro criado
    Spring-->>Web: 201 Created + JSON
    Web-->>React: 201 Created + JSON
    React->>Web: Recarrega as três consultas do painel
    Web-->>React: Dados atualizados das APIs
    React-->>Usuario: Exibe a nova solicitação e os indicadores
```

O formulário usa `POST /api/purchases`; a mudança de status usa `PUT /api/purchases/{id}/status`. O Swagger de cadastros permite também `POST` e `PUT /api/farms` e `PUT /api/purchases/{id}`. Consulte os endpoints no [README](../README.md#endpoints).
