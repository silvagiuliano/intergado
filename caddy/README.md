# Caddy

O Caddy é a porta pública da aplicação. Ele encaminha tudo para o Nginx do
serviço `web` (React, `/api/*` e Swagger), aplica headers básicos de segurança,
limita o corpo das requisições a 2 MB e comprime respostas.

Mesmo padrão do projeto `jev`: os dois projetos dividem o Caddy do host, cada um
com o seu arquivo em `/etc/caddy/sites/`.

Há dois modos de borda:

| Modo | Como ativar | Quem escuta 80/443 |
|---|---|---|
| **Caddy do host (padrão)** | `COMPOSE_PROFILES` sem `standalone` | `caddy.service` do host, que atende todos os projetos |
| Caddy do projeto | `COMPOSE_PROFILES=standalone` no `.env` | o serviço `caddy` deste Compose |

## Caddy do host (padrão)

O Nginx (`WEB_PORT`) é publicado só em `127.0.0.1`. O site do Intergado é o
modelo `host/intergado.caddy.template`, preenchido a partir do `.env` e
instalado em `/etc/caddy/sites/intergado.caddy`:

```bash
./scripts/install-host-caddy.sh --print   # só mostra o site gerado
./scripts/install-host-caddy.sh           # instala, valida, recarrega (usa sudo)
```

O script exige que o `/etc/caddy/Caddyfile` do host contenha
`import /etc/caddy/sites/*.caddy`. Valida a configuração completa como usuário
`caddy`, restaura o arquivo anterior se a validação falhar e só então faz
`systemctl reload caddy`. Rode de novo sempre que mudar `CADDY_DOMAIN` ou
`WEB_PORT`; o `deploy-compose.sh` avisa quando o site instalado diverge do `.env`.

`CADDY_DOMAIN` precisa ser um nome do Intergado: um domínio real ou um nome
local como `intergado.localhost`. `localhost` puro é recusado, porque no Caddy
geral capturaria todos os projetos. Instalação do Caddy no Ubuntu 24.04 e
confiança na CA local: [docs/DEPLOY.md](../docs/DEPLOY.md), seção 1.1.

Logs do site em `/var/log/caddy/intergado.log`; do serviço em `journalctl -u caddy`.

## Caddy do projeto (`standalone`)

Para rodar o Intergado sozinho, sem Caddy no host (demonstração, máquina de
desenvolvimento). Com `COMPOSE_PROFILES=standalone`, o `--profile` da linha de
comando substitui a lista do `.env`: ao subir outro profile, inclua os dois.

O Evolution API não é um profile: entra pelo `COMPOSE_FILE` do `.env`
(`COMPOSE_FILE=compose.yaml:compose.evolution.yaml`) e vale nos dois modos.

## Arquivos do modo standalone

| Arquivo | Uso | Selecionar no `.env` |
|---|---|---|
| `Caddyfile` | **Padrão.** HTTPS automático para `CADDY_DOMAIN` | `CADDYFILE=./caddy/Caddyfile` |
| `Caddyfile.local` | HTTP simples na porta 80, sem TLS | `CADDYFILE=./caddy/Caddyfile.local` |

## HTTPS com o seu domínio (standalone)

1. DNS: registro A/AAAA do domínio apontando para o IP público do servidor.
2. Portas 80 e 443 (TCP, e UDP 443 para HTTP/3) abertas na internet.
3. No `.env`:

```env
COMPOSE_PROFILES=standalone
CADDY_DOMAIN=intergado.seudominio.com.br
CADDY_HTTP_PORT=80
CADDY_HTTPS_PORT=443
```

4. Recrie o proxy e acompanhe a emissão do certificado:

```bash
docker compose up -d --force-recreate caddy
docker compose logs -f caddy
curl -I https://intergado.seudominio.com.br/health
```

O Caddy emite e renova o certificado sozinho e redireciona HTTP para HTTPS.
Certificados e estado ACME ficam nos volumes `caddy_data` e `caddy_config`.
O HSTS só é enviado para domínios reais.

Com `CADDY_DOMAIN=localhost`, o Caddy usa um certificado da própria CA interna:
o navegador alerta e o `curl` precisa de `-k`. Para HTTP simples, use
`CADDYFILE=./caddy/Caddyfile.local` (com `AUTH_COOKIE_SECURE=false`).

## Health checks

O Nginx responde `GET /health` sem consultar as APIs. No modo standalone, o
healthcheck do Compose e o `scripts/deploy-compose.sh` chamam
`http://127.0.0.1/caddy-health` e `http://127.0.0.1/health`; o bloco
`http://127.0.0.1` do `Caddyfile` responde só a esses dois caminhos e a
aplicação não é servida em HTTP. Os dois modos respondem `/caddy-health`.

## Rate limit

O Caddy oficial (pacote e imagem `caddy:2-alpine`) não inclui módulo de rate
limit por IP. Se essa proteção for necessária, use uma imagem Caddy com plugin
de rate limit ou aplique essa proteção em outro ponto da borda.
