# Diagramas da aplicação Intergado

Os diagramas abaixo representam a implementação atual com Docker Compose, Caddy (HTTPS), SQLite e login por código enviado por e-mail. No diagrama de sequência, “Nginx ou Vite” inclui o Caddy à frente quando o acesso é por HTTPS. Em desenvolvimento local, o Vite serve o React na porta `APP_DEV_PORT` e encaminha as mesmas rotas para as APIs.

Versões para abrir ou compartilhar: [arquitetura em SVG](ARQUITETURA_APP.svg) e [sequência em SVG](SEQUENCIA_APP.svg).

## Arquitetura

```mermaid
flowchart LR
    U["Usuário<br/>navegador"] -->|"HTTPS: 443 (CADDY_HTTPS_PORT)"| CAD
    U -.->|"HTTP direto: 3000"| W

    subgraph Compose["Docker Compose"]
        CAD["Caddy<br/>certificado e HTTPS"]
        W["Web<br/>Nginx + React"]
        CAD -->|"HTTP interno"| W
        P["API de cadastros<br/>Spring Boot / Java<br/>porta 8080"]
        A["API de indicadores<br/>Quarkus / Java<br/>porta 8081"]
        DB[("SQLite<br/>volume sqlite_data")]

        W -->|"/api/auth, /api/integrations,<br/>/api/farms e /api/purchases<br/>/swagger-ui e /v3/api-docs"| P
        W -->|"/api/analytics<br/>/q/swagger-ui e /q/openapi"| A
        P -->|"cadastros, códigos, sessões<br/>e integration_settings"| DB
        A -->|"valida sessão, consulta e agrega"| DB
    end

    P -->|"HTTPS: código de acesso<br/>RESEND_API_KEY"| R["Resend<br/>envio de e-mail"]
    R -.->|"e-mail com código de 6 dígitos"| U

    subgraph Evo["compose.evolution.yaml (testes)"]
        EV["Evolution API 2.3.7<br/>porta 8090"]
        EVDB[("PostgreSQL + Redis<br/>do Evolution")]
        EV --> EVDB
    end

    P -->|"HTTP apikey: status de compra<br/>e mensagem de teste"| EV
    EV -.->|"WhatsApp só para<br/>números de teste"| WA["Celulares de teste"]

    XLSX["Planilhas em docs/planilhas"] -.->|"resumo histórico extraído na preparação dos dados"| SEED["schema.sql + data.sql"]
    SEED -.->|"inicialização do SQLite"| P
```

As APIs compartilham o mesmo arquivo SQLite. O Spring Boot cria as tabelas e os registros iniciais, gera os códigos de acesso e as sessões; o Quarkus lê os dados para calcular indicadores. As duas APIs validam o cookie `intergado_session` contra a tabela `sessions` em todas as rotas `/api/*`, exceto as de login. O Spring Boot é o único serviço que chama o Resend; sem `RESEND_API_KEY`, o código vai apenas para o log.

O Evolution API roda em um Compose à parte, só para testes. Quando os dois arquivos sobem juntos, o Spring Boot o alcança por `http://evolution-api:8080`; nos scripts de desenvolvimento, por `EVOLUTION_API_URL`. Os dados editáveis das integrações vêm da tela **Integrações** (`integration_settings`) e, na falta deles, do `.env`. Detalhes em [EVOLUTION_API.md](EVOLUTION_API.md). Os quatro saldos de fazendas cadastrados inicialmente são históricos, de 2021. As planilhas XLSX **não são importadas automaticamente** a cada execução.

## Sequência: login, painel, solicitação e WhatsApp

```mermaid
sequenceDiagram
    autonumber
    actor Usuario as Usuário
    participant React as Navegador / React
    participant Web as Nginx ou Vite
    participant Spring as Spring Boot
    participant Quarkus as Quarkus
    participant SQLite as SQLite
    participant Resend as Resend
    participant Evolution as Evolution API
    participant WhatsApp as WhatsApp

    Usuario->>React: Abre o dashboard
    React->>Web: GET /
    Web-->>React: HTML, JavaScript e CSS
    React->>Web: GET /api/auth/me
    Web->>Spring: Encaminha GET /api/auth/me
    Spring-->>React: 401 sem sessão
    React-->>Usuario: Tela de login

    Usuario->>React: Informa o e-mail
    React->>Web: POST /api/auth/request-code
    Web->>Spring: Encaminha POST /api/auth/request-code
    opt E-mail ativo em users e sem código nos últimos 60 s
        Spring->>SQLite: INSERT login_codes (hash, validade 10 min)
        Spring->>Resend: POST /emails com o código
        Resend-->>Usuario: E-mail com código de 6 dígitos
    end
    Spring-->>React: 202 com mensagem genérica

    Usuario->>React: Digita o código
    React->>Web: POST /api/auth/verify
    Web->>Spring: Encaminha POST /api/auth/verify
    Spring->>SQLite: Confere hash, tentativas e validade
    Spring->>SQLite: Marca código usado e INSERT sessions
    Spring-->>React: 200 + Set-Cookie intergado_session (HttpOnly)

    Note over React,Quarkus: Daqui em diante o navegador envia o cookie.<br/>Spring Boot e Quarkus validam a sessão em sessions antes de cada rota /api/*

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

    rect rgb(236, 248, 238)
    Note over Usuario,WhatsApp: WhatsApp (testes): configuração e pareamento na tela Integrações
    Usuario->>React: Abre Integrações e salva números e opções
    React->>Web: PUT /api/integrations/evolution
    Web->>Spring: Encaminha PUT
    Spring->>SQLite: Grava integration_settings (tela tem prioridade sobre o .env)
    Spring-->>React: Valores e origem de cada campo
    Usuario->>React: Conectar / gerar código
    React->>Web: POST /api/integrations/evolution/connect
    Web->>Spring: Encaminha POST
    Spring->>Evolution: GET /instance/connectionState/{instância}
    alt Instância ainda não existe
        Spring->>Evolution: POST /instance/create
    else Instância existe e está desconectada
        Spring->>Evolution: GET /instance/connect/{instância}
    end
    Evolution-->>Spring: QR Code e código de pareamento
    Spring-->>React: QR Code e código
    Usuario->>WhatsApp: Escaneia o QR Code com o celular que envia
    WhatsApp-->>Evolution: Sessão conectada (estado open)
    end

    Usuario->>React: Muda o status de uma solicitação
    React->>Web: PUT /api/purchases/{id}/status
    Web->>Spring: Encaminha PUT
    Spring->>SQLite: UPDATE purchase_requests
    Spring-->>React: 200 + JSON, sem esperar o WhatsApp
    opt Avisos ligados e novo status na lista escolhida
        Spring->>Spring: Envio em segundo plano
        Spring->>Evolution: GET /instance/connectionState/{instância}
        alt Estado open
            loop Cada número de teste
                Spring->>Evolution: POST /message/sendText/{instância}
                Evolution->>WhatsApp: Mensagem com solicitação, status e valor
            end
        else Não conectado ou Evolution indisponível
            Spring->>Spring: Registra aviso no log e não envia
        end
    end
```

O bloco do WhatsApp só acontece com o `compose.evolution.yaml` ativo. As mensagens vão apenas para os números de teste; a mesma verificação de conexão vale para o botão **Enviar mensagem de teste** (`POST /api/integrations/evolution/test`). Veja [EVOLUTION_API.md](EVOLUTION_API.md).

Sem sessão válida, qualquer rota `/api/*` responde `401` e o React volta para a tela de login; o botão **Sair** chama `POST /api/auth/logout`. O formulário usa `POST /api/purchases`; a mudança de status usa `PUT /api/purchases/{id}/status`. O Swagger de cadastros permite também `POST` e `PUT /api/farms` e `PUT /api/purchases/{id}`. Consulte os endpoints no [README](../README.md#endpoints).
