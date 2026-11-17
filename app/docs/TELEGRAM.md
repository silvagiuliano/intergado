# Telegram · Bot API

O aviso de mudança de status das solicitações de compra também pode ir para o Telegram. O envio usa a
[Bot API oficial](https://core.telegram.org/bots/api), chamada direto pelo `purchases-api` (Spring Boot) por HTTPS.
Diferente do WhatsApp, **não há container extra**: basta um bot criado no `@BotFather` e saída de rede para
`api.telegram.org`.

## Por que a Bot API

| Opção | Uso | Por que não (ou sim) |
| --- | --- | --- |
| **Bot API (escolhida)** | Bot envia para conversas, grupos e canais em que foi adicionado | Oficial, gratuita, sem risco de bloqueio, só HTTP + token. |
| MTProto como usuário (TDLib, Telethon) | Automatizar uma conta pessoal | Exige número de telefone e sessão; desnecessário para avisos. |
| Servidor local da Bot API (`telegram-bot-api`) | Arquivos grandes, alto volume | Não precisamos; se um dia precisar, aponte `TELEGRAM_API_URL` para ele. |

## Como configurar

1. No Telegram, abra o **@BotFather**, envie `/newbot`, escolha o nome e o usuário (termina em `bot`).
   Ele devolve o token, no formato `123456789:AA...`.
2. Coloque o token no `.env`: `TELEGRAM_BOT_TOKEN=123456789:AA...`. Recrie o `purchases-api`
   (`docker compose up -d purchases-api`, ou reinicie o `dev-purchases.sh`).
3. Em **Integrações → Telegram · Bot API**, clique em **Verificar bot**. Deve aparecer o nome e o `@usuario` do bot.
4. Defina quem recebe:
   - **Grupo:** adicione o bot ao grupo. Para um grupo onde **só administradores enviam**, o bot precisa ser administrador.
   - **Conversa privada:** a pessoa abre o bot e envia `/start` (um bot não pode iniciar conversa com ninguém).
   - **Canal:** adicione o bot como administrador do canal com permissão de publicar.
5. Clique em **Buscar conversas**, marque as que devem receber avisos, ligue **Enviar avisos automáticos**,
   escolha os status e **Salve**.
6. **Enviar mensagem de teste** mostra o resultado por conversa.

## Comportamento e diagnóstico

- **Buscar conversas** lê o `getUpdates` sem confirmar as atualizações. O Telegram guarda essas atualizações por
  **24 horas**. Se a lista vier vazia, mande qualquer mensagem no grupo (ou `/start` no privado) e busque de novo.
  Se não aparecer, digite o `chat_id` à mão.
- O `getUpdates` não funciona se o bot tiver um webhook configurado (erro 409). Este app não usa webhook. Se outro
  sistema usar o mesmo bot, crie um bot só para o Intergado.
- Quando um grupo vira supergrupo, o `chat_id` muda (passa a começar com `-100`). O envio falha com
  `group chat was upgraded to a supergroup`. Busque as conversas de novo e troque o identificador.
- `Forbidden: bot was blocked by the user` / `bot is not a member`: a pessoa bloqueou o bot ou ele saiu do grupo.
- O token nunca aparece em logs nem nas mensagens de erro da tela. Ele é substituído por `***`.
- O envio automático roda em segundo plano. Uma falha do Telegram não impede a mudança de status e só aparece no log.

## Variáveis

| Variável | Tela | Padrão | Uso |
| --- | --- | --- | --- |
| `TELEGRAM_BOT_TOKEN` | não (segredo) | vazio | Token do `@BotFather`. Sem ele, a integração fica desligada. |
| `TELEGRAM_API_URL` | não | `https://api.telegram.org` | Base da Bot API. |
| `TELEGRAM_CHATS` | sim | vazio | `chat_id` de destino, separados por vírgula. Grupos começam com `-`, canais aceitam `@nome`. |
| `TELEGRAM_ENABLED` | sim | `false` | Liga o aviso automático de mudança de status. |
| `TELEGRAM_NOTIFY_STATUSES` | sim | todos | Status que geram aviso. |

## API

| Rota | Uso |
| --- | --- |
| `GET /api/integrations/telegram/bot` | `getMe`: confere o token e mostra o bot. |
| `GET /api/integrations/telegram/chats` | Conversas vistas pelo bot nas últimas 24 h. |
| `POST /api/integrations/telegram/test` | Envia a mensagem de teste às conversas cadastradas. |
