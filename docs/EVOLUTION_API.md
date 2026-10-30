# WhatsApp com Evolution API

## Fase 1 — pedido

> Vamos começar usando como teste de envio de mensagens o Evolution API, open-source, apenas em testes. Quero configurar o nr. de telefone via arquivo .env inicialmente.
>
> Vamos usar a versão docker.

Depois, o pedido incluiu uma **tela de configuração de integradores**, para cadastrar dados como o telefone do WhatsApp do Evolution.

## O que foi implementado

| Parte | Como funciona |
| --- | --- |
| Evolution API | `compose.evolution.yaml`: `evoapicloud/evolution-api:v2.3.7` com PostgreSQL 15 e Redis 7 próprios. API e painel `/manager` em `http://127.0.0.1:8090`. |
| Configuração | `.env` traz a URL, a chave global e os valores iniciais. A tela **Integrações** do painel grava alterações no SQLite (`integration_settings`) e tem prioridade sobre o `.env`. Campo vazio na tela volta a usar o `.env`. |
| Envio | Spring Boot chama o Evolution (`/instance/*` e `/message/sendText/{instância}`). **Só os números de teste recebem mensagens.** |
| Avisos automáticos | Quando ligados, uma mudança de status de compra para um dos status escolhidos gera mensagem aos números de teste, em segundo plano. |
| Segurança | A chave do Evolution fica só no `.env`; a tela mostra apenas “Configurada” ou “Ausente”. As rotas `/api/integrations/*` exigem login. |

### Variáveis

| Variável | Na tela | Uso |
| --- | --- | --- |
| `EVOLUTION_API_KEY` | não | Chave global do Evolution. Obrigatória para subir o `compose.evolution.yaml`. Gere com `openssl rand -hex 24`. |
| `EVOLUTION_API_URL` | não | URL usada pelo Spring Boot nos scripts `dev-*.sh` (padrão `http://127.0.0.1:8090`). No Docker, a URL interna `http://evolution-api:8080` é fixa. |
| `EVOLUTION_LISTEN_HOST`, `EVOLUTION_PORT` | não | Endereço e porta publicados (padrão `127.0.0.1:8090`). |
| `EVOLUTION_POSTGRES_PASSWORD` | não | Senha do PostgreSQL interno do Evolution. Defina antes da primeira subida; mudar depois exige recriar o volume. |
| `EVOLUTION_INSTANCE` | sim | Nome da instância (conexão de WhatsApp). Padrão `intergado`. |
| `EVOLUTION_INSTANCE_NUMBER` | sim | WhatsApp que envia, só dígitos com DDI e DDD (ex.: `5591999990000`). Com ele, o Evolution também gera um código de pareamento. |
| `WHATSAPP_TEST_NUMBERS` | sim | Destinatários de teste, separados por vírgula. |
| `WHATSAPP_ENABLED` | sim | `true` liga os avisos automáticos de mudança de status. |
| `WHATSAPP_NOTIFY_STATUSES` | sim | Status que geram aviso (ex.: `AWAITING_APPROVAL,APPROVED`). Vazio: todos. |

## Como testar

1. Preencha no `.env` ao menos `EVOLUTION_API_KEY`. Os números podem ir no `.env` ou na tela.
2. Suba o Evolution:
   - com a aplicação no Docker: `docker compose -f compose.yaml -f compose.evolution.yaml up -d`;
   - com os scripts de desenvolvimento: `docker compose -f compose.evolution.yaml up -d` e, depois, `./dev-purchases.sh`.
3. Entre no painel e abra **Integrações** no menu lateral.
4. Confira “Chave global do Evolution API: Configurada”. Preencha **WhatsApp que envia** e **Números de teste** e clique em **Salvar**.
5. Em **Conexão do WhatsApp**, clique em **Conectar / gerar código**. No celular que envia: WhatsApp → Dispositivos conectados → Conectar dispositivo. Escaneie o QR Code ou, se apareceu um código, escolha “Conectar com número de telefone” e digite-o.
6. Clique em **Verificar conexão** até aparecer **Conectado**. Depois, **Enviar mensagem de teste**.
7. Para os avisos automáticos, ligue **Enviar avisos automáticos**, escolha os status, salve e mude o status de uma solicitação no painel.

Use um número de WhatsApp de testes para enviar: integrações não oficiais podem levar o WhatsApp a restringir o número.

## Comportamento e diagnóstico

- Sem conexão ativa (estado diferente de `open`), o envio falha na hora com “WhatsApp não conectado” e nada é enviado.
- Com o Evolution fora do ar, a tela mostra “Evolution API não respondeu” e os avisos automáticos são apenas registrados no log.
- Falhas de envio aparecem no log do Spring Boot como `Falha ao enviar WhatsApp`.
- O painel do próprio Evolution (`http://127.0.0.1:8090/manager`, com a chave global) também mostra a instância.
- `docker compose -f compose.evolution.yaml down` mantém a sessão do WhatsApp; `down -v` apaga volumes e exige parear de novo.

## Rotas

| Método | Rota | Uso |
| --- | --- | --- |
| `GET` | `/api/integrations` | Integrações, campos, origem do valor (tela, `.env` ou padrão) e variáveis de ambiente |
| `PUT` | `/api/integrations/{evolution\|resend}` | Salva campos, ex.: `{ "testNumbers": "5591999990001", "enabled": "true" }` |
| `GET` | `/api/integrations/evolution/connection` | Estado da instância |
| `POST` | `/api/integrations/evolution/connect` | Cria a instância, se preciso, e devolve QR Code e código de pareamento |
| `POST` | `/api/integrations/evolution/logout` | Desconecta o WhatsApp |
| `POST` | `/api/integrations/evolution/test` | Envia mensagem de teste aos números de teste |

## Verificado em 30/10/2026

Com o Evolution 2.3.7 em Docker e o Spring Boot apontando para ele: leitura e gravação da configuração, validação de telefone, status e nome da instância, prioridade tela > `.env` > padrão, criação da instância e geração de QR Code real pelo WhatsApp, recusa imediata do envio sem pareamento, aviso automático disparado e barrado pela mesma regra, erro `502` com o Evolution parado. **O envio de uma mensagem a um número real não foi testado**, porque exige parear um celular.

## Próximas fases (fora do escopo)

Recebimento de mensagens por webhook, transcrição de áudio e imagem, resumo com IA e envio para destinatários reais (não só números de teste) — ver [OPORTUNIDADES_MELHORIAS.md](OPORTUNIDADES_MELHORIAS.md).
