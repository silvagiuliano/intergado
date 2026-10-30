# Deploy

Como publicar o Intergado em um servidor Linux com Docker Compose e HTTPS pelo Caddy.

Esta é uma aplicação de **demonstração**. O deploy descrito aqui serve para homologação e avaliação. Para produção ainda faltam perfis de permissão, backup automático e monitoramento (ver [Limitações](#limitações)).

## Visão geral

```text
Internet ──► Caddy :443 (HTTPS) ──► web (Nginx) ──► purchases-api (Spring Boot)
             Caddy :80 → redireciona            └─► analytics-api (Quarkus)
                                                        │
                                          SQLite no volume sqlite_data
Opcional: compose.evolution.yaml ──► Evolution API + PostgreSQL + Redis (WhatsApp de teste)
```

| Serviço | Imagem / build | Porta publicada |
| --- | --- | --- |
| `caddy` | `caddy:2.11.7-alpine` | `CADDY_HTTP_PORT` (80) e `CADDY_HTTPS_PORT` (443, TCP e UDP) |
| `web` | `web/Dockerfile` (Node 24 → Nginx) | `APP_LISTEN_HOST:WEB_PORT` (HTTP direto, sem Caddy) |
| `purchases-api` | `services/purchases-api/Dockerfile` (Java 17) | nenhuma |
| `analytics-api` | `services/analytics-api/Dockerfile` (Java 17) | nenhuma |
| `evolution-api` (opcional) | `evoapicloud/evolution-api:v2.3.7` | `EVOLUTION_LISTEN_HOST:EVOLUTION_PORT` (127.0.0.1:8090) |

Volumes: `sqlite_data` (banco), `caddy_data` (certificados), `caddy_config` e, com o Evolution, `evolution_instances`, `evolution_postgres` e `evolution_redis`.

## Requisitos do servidor

- Linux com Docker Engine e o plugin Docker Compose (`docker compose version`).
- 2 vCPU e 2 GB de RAM, no mínimo; o build das imagens Java usa mais memória que a execução. Com o Evolution, prefira 4 GB.
- Acesso ao repositório Git e à internet (imagens Docker, dependências Maven e npm, Let's Encrypt, Resend).
- Para HTTPS com certificado público: um domínio com registro DNS `A` (e `AAAA`, se houver IPv6) apontando para o servidor, e as portas **80 e 443** livres e abertas no firewall. O Let's Encrypt valida pela porta 80 ou 443; em outras portas, só o certificado interno funciona.

## 1. Preparar o servidor

```bash
sudo apt-get update && sudo apt-get install -y git
# Docker: https://docs.docker.com/engine/install/
sudo usermod -aG docker "$USER"   # entre de novo na sessão depois

# Firewall (exemplo com ufw): só SSH e o Caddy
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 443/udp
sudo ufw enable
```

Confira que nada mais usa as portas 80 e 443: `sudo ss -ltnp '( sport = :80 or sport = :443 )'`.

## 2. Obter o código

```bash
git clone <url-do-repositório> intergado
cd intergado
```

O nome da pasta define o nome do projeto no Compose e o prefixo dos volumes (ex.: `intergado_sqlite_data`). Mantenha a mesma pasta nas atualizações para não “perder” os volumes.

## 3. Configurar o `.env`

```bash
cp .env.example .env
chmod 600 .env
```

O `.env` fica fora do Git e guarda chaves; nunca faça commit dele. Ajuste ao menos:

| Variável | Valor no servidor |
| --- | --- |
| `CADDY_SITE_ADDRESS` | O domínio, ex.: `intergado.seudominio.com.br`. Vários: `"intergado.seudominio.com.br, www.intergado.seudominio.com.br"` (vírgula **e** espaço) |
| `CADDY_TLS` | `auto` com domínio público. `internal` em rede interna ou sem DNS público. `off` só para testes: HTTP puro, sem certificado (exige `AUTH_COOKIE_SECURE=false`) |
| `CADDY_LISTEN_HOST` | `0.0.0.0` (todas as interfaces) ou um IP específico do servidor |
| `CADDY_HTTP_PORT`, `CADDY_HTTPS_PORT` | `80` e `443` |
| `APP_LISTEN_HOST` | `127.0.0.1`, para a porta HTTP direta (`WEB_PORT`) não ficar exposta; o acesso externo passa só pelo Caddy |
| `AUTH_ALLOWED_EMAILS` | E-mails que podem entrar, separados por vírgula |
| `AUTH_COOKIE_SECURE` | `true`: o cookie de sessão só trafega em HTTPS |
| `RESEND_API_KEY` | Chave do Resend (`re_...`). Sem ela, ninguém recebe o código (ele vai só para o log) |
| `AUTH_EMAIL_FROM` | Remetente de um domínio verificado no Resend, **entre aspas**: `"Intergado <login@seudominio.com.br>"` |

Com `AUTH_COOKIE_SECURE=true`, o login pelo HTTP direto (`WEB_PORT`) deixa de funcionar; é o esperado em servidor.

Para o WhatsApp de teste, veja a [seção do Evolution](#evolution-api-opcional).

## 4. Subir a aplicação

```bash
docker compose up --build -d
docker compose ps
```

O primeiro build baixa dependências e leva alguns minutos. A ordem de subida é automática: o Spring Boot cria as tabelas e os dados de exemplo, o Quarkus espera o Spring ficar saudável, depois sobem o Nginx e o Caddy.

Na primeira subida com domínio público, o Caddy emite o certificado em alguns segundos. Acompanhe com `docker compose logs -f caddy` (procure `certificate obtained successfully`).

## 5. Verificar

```bash
DOMINIO=intergado.seudominio.com.br
curl -sI http://$DOMINIO | head -3                                 # 301 para https://
curl -s  -o /dev/null -w '%{http_code}\n' https://$DOMINIO/api/purchases   # 401 sem sessão
# Os healthchecks não passam pelo Nginx; consulte dentro dos contêineres:
docker compose exec purchases-api curl -fsS http://localhost:8080/actuator/health
docker compose exec analytics-api curl -fsS http://localhost:8081/q/health/ready
```

Depois, no navegador:

1. Abra `https://<domínio>`; o cadeado deve aparecer sem aviso.
2. Entre com um e-mail de `AUTH_ALLOWED_EMAILS` e o código recebido.
3. Confira o painel, a tela **Integrações** e o Swagger (`/swagger-ui/index.html` e `/q/swagger-ui`).

Se o e-mail não chegar, procure `Resend` em `docker compose logs purchases-api`.

## Atualizar a versão

```bash
cd intergado
# antes: faça o backup do banco (seção abaixo)
git pull
docker compose up --build -d
docker image prune -f
```

As tabelas são criadas com `CREATE TABLE IF NOT EXISTS` na inicialização do Spring Boot; os dados existentes ficam no volume. Mudanças só no `Caddyfile` ou nas variáveis `CADDY_*`: `docker compose up -d caddy` (recria o contêiner com o novo ambiente) ou `docker compose restart caddy`.

### Voltar para a versão anterior

```bash
git log --oneline -5
git checkout <commit-anterior>
docker compose up --build -d
```

Se a versão nova alterou dados de forma incompatível, restaure também o backup do banco (abaixo). Depois de corrigir, volte com `git checkout main`.

## Backup e restauração do banco

O SQLite usa modo WAL. Copiar só o arquivo `.db` com os serviços em uso pode gerar uma cópia incompleta; use o comando `.backup` do SQLite, que faz uma cópia consistente com a aplicação no ar:

```bash
mkdir -p backups
VOLUME=$(docker volume ls -q | grep '_sqlite_data$')
docker run --rm -v "$VOLUME":/data:ro -v "$PWD/backups":/backup alpine:3.22 \
  sh -c 'apk add -q sqlite && sqlite3 /data/intergado.db ".backup /backup/intergado-$(date +%F-%H%M).db"'
```

Guarde as cópias fora do servidor. Um agendamento diário pelo `cron` do host com o mesmo comando já cobre a demonstração.

Para restaurar:

```bash
docker compose stop purchases-api analytics-api
VOLUME=$(docker volume ls -q | grep '_sqlite_data$')
docker run --rm -v "$VOLUME":/data -v "$PWD/backups":/backup alpine:3.22 \
  sh -c 'rm -f /data/intergado.db-wal /data/intergado.db-shm && cp /backup/intergado-AAAA-MM-DD-HHMM.db /data/intergado.db'
docker compose up -d
```

Os certificados (`caddy_data`) são reemitidos sozinhos se perdidos; não precisam de backup. Com o Evolution, o volume `evolution_instances` guarda o pareamento do WhatsApp: perdê-lo exige parear de novo.

## Evolution API (opcional)

Apenas para testes de WhatsApp; detalhes em [EVOLUTION_API.md](EVOLUTION_API.md).

1. No `.env`, gere a chave e a senha antes da primeira subida:

   ```bash
   echo "EVOLUTION_API_KEY=$(openssl rand -hex 24)"
   echo "EVOLUTION_POSTGRES_PASSWORD=$(openssl rand -hex 12)"
   ```

2. Suba tudo junto, sempre com os dois arquivos (inclusive em `down`, `ps` e `logs`):

   ```bash
   docker compose -f compose.yaml -f compose.evolution.yaml up --build -d
   ```

3. Mantenha `EVOLUTION_LISTEN_HOST=127.0.0.1`. O Spring Boot alcança o Evolution pela rede interna (`http://evolution-api:8080`); o painel `/manager` fica só no servidor. Para abri-lo do seu computador, use um túnel SSH: `ssh -L 8090:127.0.0.1:8090 usuario@servidor` e acesse `http://localhost:8090/manager`.
4. Pareie o WhatsApp pela tela **Integrações** do painel.

## Operação do dia a dia

| Tarefa | Comando |
| --- | --- |
| Estado dos serviços | `docker compose ps` |
| Logs | `docker compose logs -f --tail=100 purchases-api` (ou `caddy`, `web`, `analytics-api`) |
| Reiniciar um serviço | `docker compose restart purchases-api` |
| Parar mantendo os dados | `docker compose down` |
| Parar e **apagar** banco, certificados e sessões | `docker compose down -v` |
| Liberar um e-mail | Inclua em `AUTH_ALLOWED_EMAILS` e rode `docker compose up -d purchases-api` |
| Remover um e-mail | Tire da lista e rode o mesmo comando; as sessões dele são encerradas |

`caddy` e os contêineres do Evolution sobem sozinhos após reiniciar o servidor (`restart: unless-stopped`). Para os demais, `docker compose up -d` depois do boot; ou acrescente `restart: unless-stopped` a eles em um `compose.override.yaml` no servidor.

## Problemas comuns

| Sintoma | Causa provável e solução |
| --- | --- |
| `bind: address already in use` no `caddy` | Outro serviço usa 80/443. Libere as portas ou mude `CADDY_HTTP_PORT`/`CADDY_HTTPS_PORT` (sem Let's Encrypt nesse caso) |
| Caddy não obtém certificado | DNS não aponta para o servidor, porta 80/443 fechada no firewall ou na nuvem, ou limite de emissões do Let's Encrypt. Veja `docker compose logs caddy` |
| Erro ao ler `CADDY_SITE_ADDRESS` com vários endereços | Separe com vírgula **e** espaço e use aspas |
| Navegador avisa de certificado inválido | `CADDY_TLS=internal` ou acesso por IP. Exporte a CA: `docker compose cp caddy:/data/caddy/pki/authorities/local/root.crt ./caddy-root.crt` e instale nos clientes |
| `https://` não responde | `CADDY_TLS=off` ativo: só HTTP na `CADDY_HTTP_PORT` |
| Login volta para a tela de e-mail | `AUTH_COOKIE_SECURE=true` acessando por HTTP, ou e-mail fora de `AUTH_ALLOWED_EMAILS` |
| Código não chega | `RESEND_API_KEY` vazia ou inválida, ou remetente fora de domínio verificado. Veja `docker compose logs purchases-api` |
| Painel mostra “Serviços indisponíveis” | `docker compose ps`: o Spring ou o Quarkus não ficou saudável. Veja os logs do serviço |
| `EVOLUTION_API_KEY` exigida ao subir | Faltou a chave no `.env` ao usar `compose.evolution.yaml` |
| Build sem memória (`Killed`) | Aumente a RAM ou crie swap; o build Maven é a etapa mais pesada |

## Checklist do deploy

- [ ] DNS do domínio aponta para o servidor
- [ ] Portas 80 e 443 livres e abertas; `WEB_PORT` e `8090` não expostas
- [ ] `.env` criado com `chmod 600`, `CADDY_SITE_ADDRESS`, `AUTH_ALLOWED_EMAILS`, `AUTH_COOKIE_SECURE=true`, `RESEND_API_KEY` e `AUTH_EMAIL_FROM`
- [ ] `docker compose up --build -d` sem erros e todos os serviços `healthy`/`running`
- [ ] HTTP redireciona para HTTPS e o certificado é válido
- [ ] Login por e-mail funciona
- [ ] Backup do banco testado (cópia e restauração)
- [ ] Opcional: Evolution no ar e WhatsApp pareado

## Limitações

- Banco SQLite em um único servidor: sem réplica nem escala horizontal.
- Backup manual ou por `cron`; sem rotina automática no projeto.
- Todos os usuários autorizados têm o mesmo acesso; não há perfis.
- O Evolution API é uma integração não oficial do WhatsApp, só para testes, e envia apenas aos números de teste.
- Sem monitoramento nem alertas além dos healthchecks do Compose.
