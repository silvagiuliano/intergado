#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

usage() {
  cat <<'EOF'
Uso: ./scripts/deploy-compose.sh [--check] [--skip-build]

  --check       Valida pré-requisitos e configuração sem alterar containers.
  --skip-build  Usa as imagens locais existentes.

O deploy usa o perfil padrão do Docker Compose e preserva volumes.
Borda: Caddy geral do host (padrão; site em /etc/caddy/sites/intergado.caddy, gerado por
./scripts/install-host-caddy.sh) ou o Caddy do projeto com COMPOSE_PROFILES=standalone.
Evolution API: COMPOSE_FILE=compose.yaml:compose.evolution.yaml no .env.
EOF
}

check_only=false
build_images=true
for arg in "$@"; do
  case "$arg" in
    --check) check_only=true ;;
    --skip-build) build_images=false ;;
    -h|--help) usage; exit 0 ;;
    *) usage >&2; exit 2 ;;
  esac
done

log() { printf '[deploy] %s\n' "$*"; }
warn() { printf '[deploy] AVISO: %s\n' "$*" >&2; }
deploy_started=false
fail() {
  printf '[deploy] ERRO: %s\n' "$*" >&2
  if "$deploy_started"; then
    printf '[deploy] Estado atual:\n' >&2
    docker compose ps >&2 || true
  fi
  exit 1
}

# Lê uma variável do .env sem executá-lo: valores com espaços (ex.: AUTH_EMAIL_FROM)
# quebrariam um "source". Vale a última ocorrência, como no Docker Compose.
env_value() {
  local key="$1" default="${2:-}" line value=""
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*${key}[[:space:]]*=(.*)$ ]] || continue
    value="${BASH_REMATCH[1]}"
  done < .env
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  if [[ "$value" == \"*\" || "$value" == \'*\' ]]; then value="${value:1:${#value}-2}"; fi
  printf '%s' "${value:-$default}"
}

for command_name in docker curl; do
  command -v "$command_name" >/dev/null 2>&1 || fail "$command_name não encontrado no PATH"
done
docker compose version >/dev/null || fail "Docker Compose indisponível"
docker info >/dev/null 2>&1 || fail "daemon Docker indisponível ou sem permissão"

[[ -f .env ]] || fail "arquivo .env ausente; copie .env.example e revise e-mails, chaves e portas"

caddyfile="$(env_value CADDYFILE ./caddy/Caddyfile)"
caddy_domain="$(env_value CADDY_DOMAIN localhost)"
https_port="$(env_value CADDY_HTTPS_PORT 443)"
# COMPOSE_PROFILES do ambiente prevalece sobre o do .env, como no Docker Compose.
compose_profiles="${COMPOSE_PROFILES-$(env_value COMPOSE_PROFILES)}"
compose_files="${COMPOSE_FILE-$(env_value COMPOSE_FILE compose.yaml)}"
edge=host
[[ ",${compose_profiles// /}," == *,standalone,* ]] && edge=standalone
if [[ "$edge" == host ]]; then
  caddyfile=""
  https_port=443
fi

# Arquivos montados pelo Compose: se faltarem, o Docker cria uma pasta no lugar e o serviço falha.
for path in compose.yaml web/nginx.conf ${caddyfile:+"$caddyfile"}; do
  [[ -f "$path" && -s "$path" ]] || fail "arquivo necessário ausente ou vazio: $path"
done
docker compose config --quiet || fail "configuração Compose inválida"

[[ -n "$(env_value AUTH_ALLOWED_EMAILS)" ]] \
  || warn "AUTH_ALLOWED_EMAILS vazio: ninguém conseguirá entrar no painel"
[[ -n "$(env_value RESEND_API_KEY)" ]] \
  || warn "RESEND_API_KEY vazio: o código de login só aparece no log do purchases-api"
if [[ "$edge" == host || "$caddyfile" != *Caddyfile.local ]]; then
  [[ "$(env_value AUTH_COOKIE_SECURE false)" == true ]] \
    || warn "AUTH_COOKIE_SECURE=false com acesso por HTTPS: use true para o cookie de sessão só trafegar em HTTPS"
fi
if [[ "$compose_files" == *compose.evolution.yaml* ]]; then
  [[ -n "$(env_value EVOLUTION_API_KEY)" ]] \
    || fail "COMPOSE_FILE inclui compose.evolution.yaml, mas EVOLUTION_API_KEY está vazio (gere com: openssl rand -hex 24)"
fi
if [[ "$edge" == standalone ]]; then
  if [[ "$caddyfile" != *Caddyfile.local && "$caddy_domain" != localhost ]]; then
    [[ "$(env_value CADDY_HTTP_PORT 80)" == 80 && "$https_port" == 443 ]] \
      || warn "domínio real com portas diferentes de 80/443: a emissão automática do certificado pode falhar"
  fi
  log "Pré-requisitos, .env e configuração Compose validados (Caddy do projeto: $caddyfile; domínio: $caddy_domain)"
else
  [[ "$caddy_domain" != localhost && "$caddy_domain" != 127.0.0.1 ]] \
    || fail "CADDY_DOMAIN=$caddy_domain no Caddy do host capturaria todos os projetos; use um nome próprio (ex.: intergado.localhost) ou COMPOSE_PROFILES=standalone"
  if ! command -v caddy >/dev/null 2>&1 || ! systemctl is-active --quiet caddy 2>/dev/null; then
    warn "caddy.service não está ativo no host: sem ele o Intergado só responde no loopback (docs/DEPLOY.md, seção 1.1)"
  elif [[ ! -f /etc/caddy/sites/intergado.caddy ]]; then
    warn "/etc/caddy/sites/intergado.caddy ausente; gere e instale com ./scripts/install-host-caddy.sh"
  elif [[ -r /etc/caddy/sites/intergado.caddy ]]; then
    expected="$(./scripts/install-host-caddy.sh --print 2>/dev/null || true)"
    if [[ -n "$expected" ]] && ! diff -q <(printf '%s\n' "$expected" | grep -v '^[[:space:]]*#' | tr -d '[:space:]') \
          <(grep -v '^[[:space:]]*#' /etc/caddy/sites/intergado.caddy | tr -d '[:space:]') >/dev/null; then
      warn "/etc/caddy/sites/intergado.caddy difere do .env atual (domínio ou porta); reinstale com ./scripts/install-host-caddy.sh"
    fi
  fi
  log "Pré-requisitos, .env e configuração Compose validados (Caddy do host; domínio: $caddy_domain)"
fi

if "$check_only"; then
  log "Verificação concluída; nenhum container foi alterado"
  exit 0
fi

on_error() {
  local status=$?
  printf '[deploy] Falha no deploy (código %s). Estado atual:\n' "$status" >&2
  docker compose ps >&2 || true
  exit "$status"
}
trap on_error ERR
deploy_started=true

wait_health() {
  local service="$1" timeout="$2" id state
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    id="$(docker compose ps -q "$service")"
    if [[ -n "$id" ]]; then
      state="$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null || true)"
      [[ "$state" == healthy ]] && return 0
      [[ "$state" == unhealthy ]] && fail "$service ficou unhealthy; consulte docker compose logs $service"
    fi
    sleep 2
  done
  fail "tempo esgotado aguardando $service ficar healthy"
}

wait_running() {
  local service="$1" timeout="$2" id state
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    id="$(docker compose ps -a -q "$service")"
    if [[ -n "$id" ]]; then
      state="$(docker inspect -f '{{.State.Status}}' "$id")"
      [[ "$state" == running ]] && return 0
      [[ "$state" == exited || "$state" == dead ]] && fail "$service parou; consulte docker compose logs $service"
    fi
    sleep 2
  done
  fail "tempo esgotado aguardando $service iniciar"
}

wait_http() {
  local name="$1" url="$2" timeout="$3"
  local deadline=$((SECONDS + timeout))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  fail "tempo esgotado aguardando $name em $url"
}

published_port() {
  local address
  address="$(docker compose port "$1" "$2")"
  [[ -n "$address" ]] || fail "porta publicada não encontrada para $1:$2"
  printf '%s\n' "${address##*:}"
}

if "$build_images"; then
  log "Construindo imagens com paralelismo 1"
  docker compose --parallel 1 build purchases-api analytics-api web
else
  log "Build ignorado; usando imagens locais"
fi

# APIs e, com compose.evolution.yaml, o Evolution; web e caddy vêm depois.
mapfile -t backend < <(docker compose config --services | grep -vxE 'web|caddy')
log "Iniciando ${backend[*]}"
# --remove-orphans remove containers de serviços que saíram do Compose.
docker compose up -d --remove-orphans "${backend[@]}"
for service in purchases-api analytics-api; do wait_health "$service" 300; done

# Nginx depois das APIs, para resolver o DNS atualizado dos upstreams.
log "Recriando o web (Nginx)"
docker compose up -d --no-deps --force-recreate web
wait_running web 120
web_port="$(published_port web 80)"
wait_http web "http://127.0.0.1:${web_port}/health" 120

tls_flags=(--resolve "${caddy_domain}:${https_port}:127.0.0.1")
[[ "$caddy_domain" == localhost || "$caddy_domain" == *.localhost ]] && tls_flags+=(-k)
https_ok=false
if [[ "$edge" == standalone ]]; then
  log "Recriando o Caddy do projeto após a prontidão do web"
  docker compose up -d --no-deps --force-recreate caddy
  wait_health caddy 90
  caddy_port="$(published_port caddy 80)"
  wait_http caddy "http://127.0.0.1:${caddy_port}/caddy-health" 90
  wait_http web-via-caddy "http://127.0.0.1:${caddy_port}/health" 90
fi

if [[ "$edge" == host || "$caddyfile" != *Caddyfile.local ]]; then
  # Testa o HTTPS do domínio no próprio host. Em nomes locais o certificado é interno (-k).
  # Para domínio real a emissão depende de DNS e portas externas: falha vira aviso.
  log "Conferindo HTTPS de ${caddy_domain}"
  deadline=$((SECONDS + 60))
  while (( SECONDS < deadline )); do
    if curl -fsS --max-time 5 "${tls_flags[@]}" -o /dev/null \
         "https://${caddy_domain}:${https_port}/health" 2>/dev/null; then
      https_ok=true; break
    fi
    sleep 3
  done
  if ! "$https_ok"; then
    if [[ "$edge" == host ]]; then
      warn "https://${caddy_domain}/health não respondeu pelo Caddy do host; confira ./scripts/install-host-caddy.sh e journalctl -u caddy"
    else
      warn "HTTPS de ${caddy_domain} ainda não respondeu com certificado válido; veja docker compose logs caddy (DNS, portas 80/443)"
    fi
  fi
fi

log "Conferindo as APIs pela rede Compose"
docker compose exec -T purchases-api curl -fsS --max-time 3 -o /dev/null http://localhost:8080/actuator/health \
  || fail "purchases-api sem health"
docker compose exec -T analytics-api curl -fsS --max-time 3 -o /dev/null http://localhost:8081/q/health/ready \
  || fail "analytics-api sem health"

docker compose ps
if [[ "$edge" == host ]]; then
  log "Deploy concluído: https://${caddy_domain}/ pelo Caddy do host (web direto: http://127.0.0.1:${web_port}/health)"
elif [[ "$caddyfile" == *Caddyfile.local ]]; then
  log "Deploy concluído: http://${caddy_domain}:${caddy_port}/ (HTTP sem TLS)"
else
  log "Deploy concluído: https://${caddy_domain}/ (health interno: http://127.0.0.1:${caddy_port}/health)"
fi
