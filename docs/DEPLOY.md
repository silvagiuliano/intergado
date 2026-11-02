# Deploy

Como publicar o Intergado em um servidor Linux com Docker Compose e HTTPS pelo Caddy geral do host, no mesmo padrão do projeto `jev`: um `caddy.service` atende todos os projetos, cada um com o seu arquivo em `/etc/caddy/sites/`. Para uma máquina sem Caddy no host, veja [Caddy do projeto (standalone)](#caddy-do-projeto-standalone). Detalhes da borda em [caddy/README.md](../caddy/README.md).

Esta é uma aplicação de **demonstração**. O deploy descrito aqui serve para homologação e avaliação. Para produção ainda faltam perfis de permissão, backup automático e monitoramento (ver [Limitações](#limitações)).

## Visão geral

```text
Internet ──► Caddy do host :443 ──► 127.0.0.1:WEB_PORT web (Nginx) ──► purchases-api (Spring Boot)
             (/etc/caddy/sites/intergado.caddy)                   └─► analytics-api (Quarkus)
                                                                          │
                                                            SQLite no volume sqlite_data
Opcional: compose.evolution.yaml ──► Evolution API + PostgreSQL + Redis (WhatsApp de teste)
```

| Serviço | Imagem / build | Porta publicada |
| --- | --- | --- |
| `web` | `web/Dockerfile` (Node 24 → Nginx) | `127.0.0.1:WEB_PORT` (3000), alvo do Caddy do host |
| `purchases-api` | `services/purchases-api/Dockerfile` (Java 17) | nenhuma |
| `analytics-api` | `services/analytics-api/Dockerfile` (Java 17) | nenhuma |
| `evolution-api` (opcional) | `evoapicloud/evolution-api:v2.3.7` | `EVOLUTION_LISTEN_HOST:EVOLUTION_PORT` (127.0.0.1:8090) |
| `caddy` (só profile `standalone`) | `caddy:2-alpine` | `CADDY_HTTP_PORT` (80) e `CADDY_HTTPS_PORT` (443) |

Volumes: `sqlite_data` (banco), `caddy_data`/`caddy_config` (só standalone) e, com o Evolution, `evolution_instances`, `evolution_postgres` e `evolution_redis`.

| Script | Uso |
| --- | --- |
| `./scripts/deploy-compose.sh [--check] [--skip-build]` | Valida o `.env`, constrói, sobe na ordem certa e confere os health checks e o HTTPS do domínio |
| `./scripts/install-host-caddy.sh [--print]` | Gera `/etc/caddy/sites/intergado.caddy` do `.env`, valida e recarrega o Caddy do host |

## Requisitos do servidor

- Ubuntu 24.04 (ou outro Linux com systemd) com Docker Engine e o plugin Docker Compose (`docker compose version`).
- Caddy do host instalado pelo repositório oficial (seção 1.1).
- 2 vCPU e 2 GB de RAM, no mínimo; o build das imagens Java usa mais memória que a execução. Com o Evolution, prefira 4 GB.
- Acesso ao repositório Git e à internet (imagens Docker, dependências Maven e npm, Let's Encrypt, Resend).
- Para HTTPS com certificado público: um domínio com registro DNS `A` (e `AAAA`, se houver IPv6) apontando para o servidor, e TCP 80/443 e UDP 443 abertos.

## 1. Preparar o servidor

```bash
sudo apt-get update && sudo apt-get install -y git curl
# Docker: https://docs.docker.com/engine/install/
sudo usermod -aG docker "$USER"   # entre de novo na sessão depois
sudo systemctl enable docker
```

### 1.1 Caddy do host (padrão)

Mesmo procedimento do `jev`. Se o Caddy do host já estiver instalado e com `import /etc/caddy/sites/*.caddy` no Caddyfile principal (por exemplo, pelo deploy do `jev`), pule para a [seção 2](#2-obter-o-código).

Use o repositório oficial do Caddy: o pacote `caddy` do `universe` é a série 2.6, antiga.

1. Libere 80, 443 e 2019 (API de administração, só loopback):

   ```bash
   sudo ss -ltnp '( sport = :80 or sport = :443 or sport = :2019 )'
   # apache2/nginx: sudo systemctl disable --now apache2 nginx
   # Caddy de um projeto em Compose: docker compose stop caddy
   ```

2. Instale:

   ```bash
   sudo apt update && sudo apt install -y curl gnupg
   sudo install -d -m 0755 /etc/apt/keyrings
   curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
     | sudo gpg --dearmor -o /etc/apt/keyrings/caddy-stable.gpg
   echo "deb [signed-by=/etc/apt/keyrings/caddy-stable.gpg] https://dl.cloudsmith.io/public/caddy/stable/deb/debian any-version main" \
     | sudo tee /etc/apt/sources.list.d/caddy-stable.list
   sudo chmod o+r /etc/apt/keyrings/caddy-stable.gpg /etc/apt/sources.list.d/caddy-stable.list
   sudo apt update && sudo apt install -y caddy
   caddy version && systemctl status caddy
   ```

3. Caddyfile principal com um arquivo por projeto:

   ```bash
   sudo cp /etc/caddy/Caddyfile /etc/caddy/Caddyfile.orig
   sudo install -d -m 0755 /etc/caddy/sites
   printf '{\n\temail administrador@example.com\n}\n\nimport /etc/caddy/sites/*.caddy\n' \
     | sudo tee /etc/caddy/Caddyfile
   ```

4. Firewall, para domínio real. Com `ufw` (instalado, normalmente inativo no 24.04):

   ```bash
   sudo ufw allow OpenSSH
   sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw allow 443/udp
   sudo ufw enable
   ```

   O Docker publica portas por fora do `ufw`; o que protege os serviços é publicá-los só em `127.0.0.1`, como faz este Compose.

5. Nomes locais (`*.localhost`): a CA interna do Caddy aparece no primeiro acesso. Para o navegador confiar:

   ```bash
   CA=/var/lib/caddy/.local/share/caddy/pki/authorities/local/root.crt
   sudo cp "$CA" /usr/local/share/ca-certificates/caddy-local.crt && sudo update-ca-certificates
   sudo cat "$CA" > /tmp/caddy-root.crt
   sudo apt install -y libnss3-tools
   certutil -d sql:$HOME/.pki/nssdb -A -t "C,," -n "Caddy Local CA" -i /tmp/caddy-root.crt   # Chrome
   ```

   No Firefox (snap), importe `/tmp/caddy-root.crt` em Configurações → Certificados → Autoridades. O `sudo caddy trust` grava no NSS do root e não alcança o navegador do usuário.

Certificados e estado ACME ficam em `/var/lib/caddy`; logs do site em `/var/log/caddy/intergado.log` e do serviço em `journalctl -u caddy`. Atualize com `sudo apt upgrade caddy`; `sudo systemctl reload caddy` aplica configuração sem derrubar conexões.

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

O `.env` fica fora do Git e guarda chaves; nunca faça commit dele. Ajuste ao menos, sem `standalone` em `COMPOSE_PROFILES`:

| Variável | Valor no servidor |
| --- | --- |
| `CADDY_DOMAIN` | O domínio, ex.: `intergado.seudominio.com.br`, ou `intergado.localhost` para uso local. Cada projeto do host precisa de um nome próprio; `localhost` puro é recusado |
| `WEB_PORT` | `3000`, ou outra porta livre no loopback do servidor (diferente das usadas por outros projetos, ex.: `APP_PORT` do `jev`) |
| `AUTH_ALLOWED_EMAILS` | E-mails que podem entrar, separados por vírgula |
| `AUTH_COOKIE_SECURE` | `true`: o cookie de sessão só trafega em HTTPS |
| `RESEND_API_KEY` | Chave do Resend (`re_...`). Sem ela, ninguém recebe o código (ele vai só para o log) |
| `AUTH_EMAIL_FROM` | Remetente de um domínio verificado no Resend, **entre aspas**: `"Intergado <login@seudominio.com.br>"` |

Com `AUTH_COOKIE_SECURE=true`, o login pelo HTTP direto (`http://127.0.0.1:WEB_PORT`) deixa de funcionar; é o esperado em servidor. Em nomes `*.localhost` o navegador já resolve para 127.0.0.1, sem editar o `/etc/hosts`.

Para o WhatsApp de teste, veja a [seção do Evolution](#evolution-api-opcional).

## 4. Subir a aplicação e instalar o site

```bash
./scripts/deploy-compose.sh --check   # só valida
./scripts/deploy-compose.sh
./scripts/install-host-caddy.sh
curl -I https://intergado.seudominio.com.br/health
```

O primeiro build baixa dependências e leva alguns minutos. O `deploy-compose.sh` sobe as APIs, espera ficarem saudáveis, recria o Nginx, confere `http://127.0.0.1:WEB_PORT/health` e o HTTPS do domínio. No modo do host ele avisa quando o `caddy.service` está parado ou quando `/etc/caddy/sites/intergado.caddy` falta ou diverge do `.env`.

O `install-host-caddy.sh` gera `/etc/caddy/sites/intergado.caddy` a partir de `caddy/host/intergado.caddy.template`, valida como usuário `caddy`, restaura o arquivo anterior se a validação falhar e só então recarrega. Rode de novo sempre que mudar `CADDY_DOMAIN` ou `WEB_PORT`. Não edite a cópia em `/etc/caddy/sites`. Mudanças de site afetam todos os projetos; o script nunca recarrega sem validar.

## 5. Verificar

```bash
DOMINIO=intergado.seudominio.com.br
curl -sI http://$DOMINIO | head -3                                         # 308 para https://
curl -s  https://$DOMINIO/caddy-health                                     # {"status":"ok","service":"caddy","site":"intergado"}
curl -s  -o /dev/null -w '%{http_code}\n' https://$DOMINIO/api/purchases   # 401 sem sessão
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
./scripts/deploy-compose.sh
docker image prune -f
```

As tabelas são criadas com `CREATE TABLE IF NOT EXISTS` na inicialização do Spring Boot; os dados existentes ficam no volume. Se o `caddy/host/intergado.caddy.template` mudou, o deploy avisa que o site instalado diverge: rode `./scripts/install-host-caddy.sh`.

### Voltar para a versão anterior

```bash
git log --oneline -5
git checkout <commit-anterior>
./scripts/deploy-compose.sh
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

Os certificados ficam com o Caddy do host (`/var/lib/caddy`) e são reemitidos sozinhos se perdidos. Com o Evolution, o volume `evolution_instances` guarda o pareamento do WhatsApp: perdê-lo exige parear de novo.

## Evolution API (opcional)

Apenas para testes de WhatsApp; detalhes em [EVOLUTION_API.md](EVOLUTION_API.md).

1. No `.env`, gere a chave e a senha antes da primeira subida:

   ```bash
   echo "EVOLUTION_API_KEY=$(openssl rand -hex 24)"
   echo "EVOLUTION_POSTGRES_PASSWORD=$(openssl rand -hex 12)"
   ```

2. No `.env`, inclua o arquivo do Evolution no Compose; assim `deploy-compose.sh`, `up`, `down`, `ps` e `logs` já usam os dois:

   ```env
   COMPOSE_FILE=compose.yaml:compose.evolution.yaml
   ```

   Depois rode `./scripts/deploy-compose.sh`.

3. Mantenha `EVOLUTION_LISTEN_HOST=127.0.0.1`. O Spring Boot alcança o Evolution pela rede interna (`http://evolution-api:8080`); o painel `/manager` fica só no servidor. Para abri-lo do seu computador, use um túnel SSH: `ssh -L 8090:127.0.0.1:8090 usuario@servidor` e acesse `http://localhost:8090/manager`.
4. Pareie o WhatsApp pela tela **Integrações** do painel.

## Operação do dia a dia

| Tarefa | Comando |
| --- | --- |
| Estado dos serviços | `docker compose ps` |
| Logs | `docker compose logs -f --tail=100 purchases-api` (ou `web`, `analytics-api`); Caddy: `sudo journalctl -u caddy -f` |
| Reiniciar um serviço | `docker compose restart purchases-api` |
| Parar mantendo os dados | `docker compose down` |
| Parar e **apagar** banco e sessões | `docker compose down -v` |
| Conferir configuração e site do Caddy | `./scripts/deploy-compose.sh --check` |
| Liberar um e-mail | Inclua em `AUTH_ALLOWED_EMAILS` e rode `docker compose up -d purchases-api` |
| Remover um e-mail | Tire da lista e rode o mesmo comando; as sessões dele são encerradas |

Todos os contêineres sobem sozinhos após reiniciar o servidor (`restart: unless-stopped`, com o serviço Docker habilitado). O Caddy do host sobe pelo systemd.

## Problemas comuns

| Sintoma | Causa provável e solução |
| --- | --- |
| `502 Bad Gateway` no domínio | O Nginx não responde em `127.0.0.1:WEB_PORT`: `docker compose ps`, `curl http://127.0.0.1:3000/health`; se mudou `WEB_PORT`, rode `./scripts/install-host-caddy.sh` |
| Domínio não responde / outro site aparece | Site não instalado ou divergente: `./scripts/deploy-compose.sh --check` e `./scripts/install-host-caddy.sh`. Confira `import /etc/caddy/sites/*.caddy` no `/etc/caddy/Caddyfile` |
| `CADDY_DOMAIN=localhost ... capturaria todos os projetos` | Use um nome próprio (`intergado.localhost`) ou o domínio real |
| `port is already allocated` no `caddy` do Compose | `standalone` ativo junto com o Caddy do host. Tire `standalone` de `COMPOSE_PROFILES` e rode `docker compose rm -sf caddy` |
| Caddy não obtém certificado | DNS não aponta para o servidor, porta 80/443 fechada no firewall ou na nuvem, ou limite de emissões do Let's Encrypt. Veja `journalctl -u caddy` |
| Navegador avisa de certificado inválido | Nome `*.localhost` com a CA interna: confie na CA (seção 1.1, passo 5) |
| Login volta para a tela de e-mail | `AUTH_COOKIE_SECURE=true` acessando por HTTP, ou e-mail fora de `AUTH_ALLOWED_EMAILS` |
| Código não chega | `RESEND_API_KEY` vazia ou inválida, ou remetente fora de domínio verificado. Veja `docker compose logs purchases-api` |
| Painel mostra “Serviços indisponíveis” | `docker compose ps`: o Spring ou o Quarkus não ficou saudável. Veja os logs do serviço |
| `EVOLUTION_API_KEY` exigida ao subir | Faltou a chave no `.env` ao usar `compose.evolution.yaml` |
| Build sem memória (`Killed`) | Aumente a RAM ou crie swap; o build Maven é a etapa mais pesada |

## Checklist do deploy

- [ ] DNS do domínio aponta para o servidor
- [ ] Caddy do host ativo nas portas 80 e 443, com `import /etc/caddy/sites/*.caddy`; `WEB_PORT` e `8090` só em `127.0.0.1`
- [ ] `.env` criado com `chmod 600`, `CADDY_DOMAIN`, `AUTH_ALLOWED_EMAILS`, `AUTH_COOKIE_SECURE=true`, `RESEND_API_KEY` e `AUTH_EMAIL_FROM`, sem `standalone`
- [ ] `./scripts/deploy-compose.sh` sem erros nem avisos de Caddy
- [ ] `./scripts/install-host-caddy.sh` instalou `/etc/caddy/sites/intergado.caddy`
- [ ] HTTP redireciona para HTTPS e o certificado é válido
- [ ] Login por e-mail funciona
- [ ] Backup do banco testado (cópia e restauração)
- [ ] Opcional: Evolution no ar e WhatsApp pareado

## Caddy do projeto (standalone)

Para rodar o Intergado sozinho, sem Caddy no host (demonstração, máquina de desenvolvimento). Não use junto com o Caddy do host: os dois disputam as portas 80/443.

```env
COMPOSE_PROFILES=standalone
CADDY_DOMAIN=intergado.seudominio.com.br   # ou localhost
CADDYFILE=./caddy/Caddyfile                # ./caddy/Caddyfile.local: HTTP sem TLS
CADDY_HTTP_PORT=80
CADDY_HTTPS_PORT=443
```

Depois, `./scripts/deploy-compose.sh` (recria o Caddy após o Nginx e confere `/caddy-health` e `/health`). Logs: `docker compose logs -f caddy`. Certificados nos volumes `caddy_data` e `caddy_config`. Arquivos e detalhes em [caddy/README.md](../caddy/README.md).

## Limitações

- Banco SQLite em um único servidor: sem réplica nem escala horizontal.
- Backup manual ou por `cron`; sem rotina automática no projeto.
- Todos os usuários autorizados têm o mesmo acesso; não há perfis.
- O Evolution API é uma integração não oficial do WhatsApp, só para testes, e envia apenas aos números de teste.
- Sem monitoramento nem alertas além dos healthchecks do Compose.
